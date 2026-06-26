package cp.player.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cp.player.util.DspPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * FlickPlayer — 基于 Rust JNI 引擎的 Media3 播放器。
 *
 * 线程模型：
 * - 所有 JNI 调用通过 [RustEngine.engineDispatcher] 在专用 IO 线程执行，不阻塞主线程。
 * - 状态更新在主线程（通过 SharedFlow collect）。
 * - 进度由 RustEngine 事件驱动，不再独立轮询。
 */
class FlickPlayer(private val context: Context) : SimpleBasePlayer(Looper.getMainLooper()) {

    companion object {
        private const val TAG = "FlickPlayer"
        private const val TARGET_PRE_BUFFER_SECS = 15
        private const val MIN_PRE_BUFFER_BYTES = 240 * 1024L
        private const val MAX_PRE_BUFFER_BYTES = 2600 * 1024L
        private const val UNDERRUN_POLL_MS = 1000L
    }

    private class PlaylistItem(val uid: java.util.UUID, val item: MediaItem)
    private var playlist: List<PlaylistItem> = emptyList()
    private var currentMediaItemIndex = 0
    private var playWhenReady = false
    private var isPlaying = false
    private var playbackState = Player.STATE_IDLE

    private val currentPositionMs = AtomicLong(0L)
    private val positionUpdateTimeMs = AtomicLong(0L)
    private val lastSeekTimeMs = AtomicLong(0L)

    @Volatile private var durationMs = 0L
    @Volatile private var isDownloading = false
    @Volatile private var bytesDownloaded = 0L
    @Volatile private var downloadComplete = false

    @Volatile private var underrunPositionMs = 0L
    @Volatile private var lastSampleRate = 0
    @Volatile private var lastBitrate = 0
    @Volatile private var currentPlayingPath: String? = null

    /**
     * 暂停请求标志。
     *
     * 用于防止暂停过程中的竞态条件：handleSetPlayWhenReady(false) 同步更新 UI 状态，
     * 但 RustEngine.pause() 是异步的。在此窗口期内，Rust 引擎可能发送旧的 "playing"
     * 状态事件，导致 isPlaying 被错误地设为 true（UI 闪烁/状态不一致）。
     *
     * 设置时机：handleSetPlayWhenReady(false)
     * 清除时机：收到 Rust 引擎的 "paused" 状态确认，或用户恢复播放
     */
    @Volatile private var pauseRequested = false

    // Next-track pre-caching
    @Volatile private var preCachedPath: String? = null
    @Volatile private var preCachedItemIndex = -1
    private var preCacheJob: Job? = null

    /** 主线程 scope，用于状态更新和 UI 相关操作 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engineEventJob: Job? = null
    private var streamProxyJob: Job? = null
    private var underrunWatchJob: Job? = null

    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffleModeEnabled = false

    /** 时长缓存，避免重复创建 MediaMetadataRetriever */
    private val durationCache = ConcurrentHashMap<String, Long>()

    /** DSP 设置是否已同步到引擎（首次播放后自动应用） */
    private var dspApplied = false

    init {
        RustEngine.initEngine(context)
        // setVolume 移到引擎线程
        scope.launch(RustEngine.engineDispatcher) { RustEngine.setVolume(1.0f) }
        observeRustEvents()
    }

    fun getFormatInfo(): Pair<Int, Int> = lastSampleRate to lastBitrate

    /** 首次 play 后同步 DSP 设置（仅执行一次） */
    private fun applyDspOnce() {
        if (!dspApplied) {
            dspApplied = true
            initDspFromPrefs(context)
        }
    }

    /** 从 SharedPreferences 加载 DSP 设置并同步到 Rust 引擎 */
    private fun initDspFromPrefs(ctx: Context) {
        try {
            val eqEnabled = DspPreferences.getEqEnabled(ctx)
            val bands = DspPreferences.getPeqBands(ctx)
            Log.i(TAG, "initDspFromPrefs: eqEnabled=$eqEnabled, bands=${bands.size}")
            if (eqEnabled) {
                val result = if (bands.isEmpty()) RustEngine.setEqualizer(true, floatArrayOf(), floatArrayOf(), floatArrayOf())
                else RustEngine.setEqualizer(true, bands.map { it.freq }.toFloatArray(), bands.map { it.gain }.toFloatArray(), bands.map { it.q }.toFloatArray())
                Log.i(TAG, "initDspFromPrefs: setEqualizer result=$result")
            }
            val fxEnabled = DspPreferences.getFxEnabled(ctx)
            if (fxEnabled) {
                val result = RustEngine.setFx(true, 0f, 1f, 0.35f, 6800f, 240f, DspPreferences.getFxSize(ctx), DspPreferences.getFxMix(ctx), 0.35f, DspPreferences.getFxWidth(ctx))
                Log.i(TAG, "initDspFromPrefs: setFx result=$result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "initDspFromPrefs failed", e)
        }
    }

    private fun calculatePreBufferBytes(item: MediaItem): Long {
        val bitrate = item.mediaMetadata.extras?.getInt("bitrate") ?: 0
        if (bitrate > 0) {
            val bytes = (bitrate.toLong() * TARGET_PRE_BUFFER_SECS) / 8
            return bytes.coerceIn(MIN_PRE_BUFFER_BYTES, MAX_PRE_BUFFER_BYTES)
        }
        return MIN_PRE_BUFFER_BYTES
    }

    // ═══════════════════════════════════════════════
    // 引擎事件处理（在主线程）
    // ═══════════════════════════════════════════════

    private fun observeRustEvents() {
        engineEventJob = scope.launch {
            RustEngine.audioEvents.collect { event ->
                if (playlist.isEmpty()) return@collect
                when (event) {
                    is AudioEvent.StateChanged -> handleStateChanged(event.state, event.path)
                    is AudioEvent.Progress -> handleProgress(event)
                    is AudioEvent.TrackEnded -> handleTrackEnded(event.path)
                    is AudioEvent.Error -> handleError(event.message)
                    is AudioEvent.FormatChanged -> handleFormatChanged(event)
                    else -> {}
                }
            }
        }
    }

    private fun handleStateChanged(state: String, eventPath: String? = null) {
        // 路径过滤：忽略旧曲目的状态事件
        val currentPath = currentPlayingPath
        if (eventPath != null && currentPath != null &&
            !eventPath.equals(currentPath, ignoreCase = true)) {
            return
        }

        when (state.lowercase()) {
            "playing" -> {
                // 暂停请求进行中时，忽略引擎的 "playing" 状态事件，
                // 防止旧事件覆盖用户的暂停意图
                if (pauseRequested) return
                isPlaying = true
                playbackState = Player.STATE_READY
                positionUpdateTimeMs.set(System.currentTimeMillis())
                invalidateState()
            }
            "paused" -> {
                pauseRequested = false
                isPlaying = false
                playbackState = Player.STATE_READY
                invalidateState()
            }
            "stopped", "idle" -> {
                isPlaying = false
                playbackState = Player.STATE_IDLE
                invalidateState()
            }
            "buffering" -> {
                if (isDownloading && !downloadComplete) {
                    isPlaying = false
                    playbackState = Player.STATE_BUFFERING
                    invalidateState()
                    startUnderrunWatch()
                } else {
                    playbackState = Player.STATE_BUFFERING
                    invalidateState()
                }
            }
        }
    }

    private fun handleProgress(event: AudioEvent.Progress) {
        if (System.currentTimeMillis() - lastSeekTimeMs.get() < 1000) return

        val newPos = (event.positionSecs * 1000).toLong()
        val current = currentPositionMs.get()

        if (newPos < current && current - newPos < 2000 && !downloadComplete) {
            return
        }

        updatePosition(newPos)

        var newDuration = getRealDuration()
        if (newDuration <= 0) {
            if (!isDownloading || downloadComplete) {
                newDuration = (event.durationSecs * 1000).toLong()
            }
        }

        if (newDuration > 0 && durationMs != newDuration) {
            durationMs = newDuration
            invalidateState()
        }
    }

    private fun handleFormatChanged(event: AudioEvent.FormatChanged) {
        if (event.sampleRate > 0) lastSampleRate = event.sampleRate
        if (event.bitrate > 0) lastBitrate = event.bitrate
    }

    /**
     * 曲目播放完毕。
     *
     * 自动下一首的逻辑和手动点击播放完全一致：
     * 1. 递增索引
     * 2. 调用 playCurrentItem()（内部先 stop() 再 play()）
     */
    private fun handleTrackEnded(eventPath: String) {
        // 忽略旧曲目的 TrackEnded
        if (currentPlayingPath != null && !currentPlayingPath.equals(eventPath, ignoreCase = true)) {
            Log.w(TAG, "Ignoring TrackEnded for old path: $eventPath")
            return
        }

        // 流媒体未下载完毕 → 缓冲等待
        if (isDownloading && !downloadComplete) {
            underrunPositionMs = currentPositionMs.get()
            Log.w(TAG, "Underrun at ${underrunPositionMs}ms, waiting for data...")
            isPlaying = false
            playbackState = Player.STATE_BUFFERING
            invalidateState()
            startUnderrunWatch()
            return
        }

        if (currentMediaItemIndex < playlist.size - 1) {
            Log.i(TAG, "Track ended, advancing to next: ${currentMediaItemIndex + 1}")
            currentMediaItemIndex++
            playCurrentItem()
        } else {
            Log.i(TAG, "Track ended, no more tracks")
            isPlaying = false
            playbackState = Player.STATE_ENDED
            invalidateState()
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "Rust error: $message")

        if (isDownloading && !downloadComplete) {
            underrunPositionMs = currentPositionMs.get()
            isPlaying = false
            playbackState = Player.STATE_BUFFERING
            invalidateState()
            startUnderrunWatch()
            return
        }

        playbackState = Player.STATE_IDLE
        isPlaying = false
        invalidateState()
    }

    // ═══════════════════════════════════════════════
    // Underrun recovery
    // ═══════════════════════════════════════════════

    private fun startUnderrunWatch() {
        underrunWatchJob?.cancel()
        underrunWatchJob = scope.launch {
            while (isActive && isDownloading && !downloadComplete) {
                delay(UNDERRUN_POLL_MS)
            }
            if (downloadComplete && playbackState == Player.STATE_BUFFERING && playWhenReady) {
                val path = currentPlayingPath ?: return@launch
                // JNI 调用移到引擎线程
                withContext(RustEngine.engineDispatcher) {
                    RustEngine.play(path)
                    applyDspOnce()
                    if (underrunPositionMs > 0) {
                        delay(100)
                        RustEngine.seek(underrunPositionMs / 1000.0)
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // Position / Progress
    // ═══════════════════════════════════════════════

    private fun updatePosition(newPos: Long) {
        currentPositionMs.set(newPos)
        positionUpdateTimeMs.set(System.currentTimeMillis())
        invalidateState()
    }

    private fun getRealDuration(): Long {
        if (playlist.isEmpty()) return 0L
        val item = playlist[currentMediaItemIndex].item
        val extraDuration = item.mediaMetadata.extras?.getLong("duration") ?: 0L
        if (extraDuration > 0) return extraDuration
        val path = item.localConfiguration?.uri?.toString() ?: return 0L
        if (!path.startsWith("cp://") && !path.startsWith("http://") && !path.startsWith("https://")) {
            return durationCache.getOrPut(path) {
                extractDuration(Uri.parse(path), false)
            }
        }
        return 0L
    }

    private fun extractDuration(uri: Uri, isFile: Boolean): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            if (isFile) retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            d?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }

    // ═══════════════════════════════════════════════
    // SimpleBasePlayer overrides
    // ═══════════════════════════════════════════════

    override fun getState(): State {
        val stateBuilder = State.Builder()
            .setAvailableCommands(
                Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_CHANGE_MEDIA_ITEMS,
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                        Player.COMMAND_GET_VOLUME,
                        Player.COMMAND_SET_VOLUME,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SET_REPEAT_MODE,
                        Player.COMMAND_SET_SHUFFLE_MODE,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_TRACKS
                    )
                    .build()
            )
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffleModeEnabled)

        if (playlist.isNotEmpty()) {
            val mediaItemDataList = playlist.mapIndexed { index, pItem ->
                val item = pItem.item
                val itemDurationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L
                val displayDurationMs = if (index == currentMediaItemIndex && durationMs > 0) {
                    durationMs
                } else if (itemDurationMs > 0) {
                    itemDurationMs
                } else {
                    C.TIME_UNSET
                }
                SimpleBasePlayer.MediaItemData.Builder(pItem.uid)
                    .setMediaItem(item)
                    .setMediaMetadata(item.mediaMetadata)
                    .setIsSeekable(true)
                    .setDurationUs(if (displayDurationMs > 0) displayDurationMs * 1000 else C.TIME_UNSET)
                    .build()
            }
            stateBuilder.setPlaylist(mediaItemDataList)
            stateBuilder.setCurrentMediaItemIndex(currentMediaItemIndex)
            stateBuilder.setContentPositionMs {
                if (isPlaying && playbackState == Player.STATE_READY) {
                    val elapsed = System.currentTimeMillis() - positionUpdateTimeMs.get()
                    currentPositionMs.get() + elapsed
                } else {
                    currentPositionMs.get()
                }
            }
        } else {
            stateBuilder.setPlaylist(emptyList())
            if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                stateBuilder.setPlaybackState(Player.STATE_IDLE)
            }
        }
        return stateBuilder.build()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        this.shuffleModeEnabled = shuffleModeEnabled
        cancelPreCache()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playbackState = Player.STATE_BUFFERING
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            pauseRequested = false
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_ENDED) {
                if (playlist.isNotEmpty()) playCurrentItem()
            } else {
                // JNI 调用移到引擎线程
                scope.launch(Dispatchers.IO) { RustEngine.resume() }
            }
        } else {
            pauseRequested = true
            scope.launch(Dispatchers.IO) { RustEngine.pause() }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        playlist = mediaItems.map { PlaylistItem(java.util.UUID.randomUUID(), it) }
        currentMediaItemIndex = if (startIndex in mediaItems.indices) startIndex else 0
        cancelPreCache()
        if (playWhenReady && playlist.isNotEmpty()) playCurrentItem()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val newList = playlist.toMutableList()
        val wasEmpty = newList.isEmpty()
        val insertIndex = if (index == C.INDEX_UNSET) newList.size else index.coerceIn(0, newList.size)
        newList.addAll(insertIndex, mediaItems.map { PlaylistItem(java.util.UUID.randomUUID(), it) })
        playlist = newList
        if (wasEmpty) currentMediaItemIndex = 0
        else if (currentMediaItemIndex >= insertIndex) currentMediaItemIndex += mediaItems.size
        currentMediaItemIndex = currentMediaItemIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val newList = playlist.toMutableList()
        val validFrom = fromIndex.coerceAtLeast(0)
        val validTo = toIndex.coerceAtMost(newList.size)
        if (validFrom >= validTo) return Futures.immediateVoidFuture()
        val itemsToMove = newList.subList(validFrom, validTo).toList()
        newList.subList(validFrom, validTo).clear()
        val insertIndex = newIndex.coerceIn(0, newList.size)
        newList.addAll(insertIndex, itemsToMove)
        playlist = newList
        if (currentMediaItemIndex in validFrom until validTo) {
            currentMediaItemIndex = insertIndex + (currentMediaItemIndex - validFrom)
        } else if (currentMediaItemIndex >= validTo && currentMediaItemIndex < insertIndex) {
            currentMediaItemIndex -= (validTo - validFrom)
        } else if (currentMediaItemIndex >= insertIndex && currentMediaItemIndex < validFrom) {
            currentMediaItemIndex += (validTo - validFrom)
        }
        currentMediaItemIndex = currentMediaItemIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        cancelPreCache()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val newList = playlist.toMutableList()
        val validFrom = fromIndex.coerceAtLeast(0)
        val validTo = toIndex.coerceAtMost(newList.size)
        if (validFrom < validTo) {
            newList.subList(validFrom, validTo).clear()
            playlist = newList
            if (currentMediaItemIndex >= validTo) {
                currentMediaItemIndex -= (validTo - validFrom)
            } else if (currentMediaItemIndex >= validFrom) {
                currentMediaItemIndex = validFrom.coerceAtMost(playlist.size - 1).coerceAtLeast(0)
                if (playlist.isEmpty()) {
                    scope.launch(Dispatchers.IO) { RustEngine.stop() }
                    isPlaying = false
                    playbackState = Player.STATE_IDLE
                } else if (playWhenReady) {
                    playCurrentItem()
                }
            }
            currentMediaItemIndex = currentMediaItemIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
            cancelPreCache()
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        val targetPosition = if (positionMs == C.TIME_UNSET) 0L else positionMs
        if (mediaItemIndex != currentMediaItemIndex && mediaItemIndex in playlist.indices) {
            currentMediaItemIndex = mediaItemIndex
            cancelPreCache()
            playCurrentItem()
        } else {
            lastSeekTimeMs.set(System.currentTimeMillis())
            // JNI 调用移到引擎线程
            scope.launch(Dispatchers.IO) {
                RustEngine.seek(targetPosition / 1000.0)
            }
            updatePosition(targetPosition)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        scope.launch(Dispatchers.IO) { RustEngine.stop() }
        pauseRequested = false
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        isPlaying = false
        cancelPreCache()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        // 取消所有协程，然后在引擎线程停止引擎
        engineEventJob?.cancel()
        streamProxyJob?.cancel()
        underrunWatchJob?.cancel()
        preCacheJob?.cancel()
        scope.launch(Dispatchers.IO) { RustEngine.stopEngine() }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        scope.launch(Dispatchers.IO) { RustEngine.setVolume(volume) }
        return Futures.immediateVoidFuture()
    }

    // ═══════════════════════════════════════════════
    // 播放核心逻辑
    // ═══════════════════════════════════════════════

    private fun getRealPathFromUri(uriStr: String): String? {
        val uri = Uri.parse(uriStr)
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve path for $uriStr", e)
            }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /**
     * 播放当前曲目。
     *
     * 无论用户点击播放还是自动下一首，都走这个方法。
     * 流程：stop() 清理引擎 → play() 开始新曲目。
     */
    private fun playCurrentItem() {
        if (playlist.isEmpty()) return

        val item = playlist[currentMediaItemIndex].item
        updatePosition(0L)
        durationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L
        isDownloading = false
        downloadComplete = false
        bytesDownloaded = 0L
        underrunPositionMs = 0L
        underrunWatchJob?.cancel()

        playbackState = Player.STATE_BUFFERING
        invalidateState()

        val path = item.localConfiguration?.uri?.toString()
        if (path == null) {
            Log.e(TAG, "Media URI is null for item: ${item.mediaId}")
            return
        }

        var actualPath = path
        if (path.startsWith("content://") || path.startsWith("file://")) {
            actualPath = getRealPathFromUri(path) ?: path
        }

        if (actualPath.startsWith("cp://") || actualPath.startsWith("http://") || actualPath.startsWith("https://")) {
            if (preCachedPath != null && preCachedItemIndex == currentMediaItemIndex) {
                currentPlayingPath = preCachedPath
                isDownloading = false
                downloadComplete = true
                preCachedPath = null
                preCachedItemIndex = -1
                // JNI 调用移到引擎线程
                scope.launch(Dispatchers.IO) {
                    RustEngine.play(currentPlayingPath!!)
                    applyDspOnce()
                }
                startPreCacheNextTrack()
            } else {
                streamToRustEngine(item)
            }
            return
        }

        currentPlayingPath = actualPath
        // JNI 调用移到引擎线程：先 stop 再 play
        scope.launch(Dispatchers.IO) {
            RustEngine.stop()
            RustEngine.play(actualPath)
            applyDspOnce()
        }
        startPreCacheNextTrack()
    }

    // ═══════════════════════════════════════════════
    // 流媒体下载
    // ═══════════════════════════════════════════════

    private fun streamToRustEngine(item: MediaItem) {
        streamProxyJob?.cancel()
        underrunWatchJob?.cancel()
        val uri = item.localConfiguration?.uri ?: return

        playbackState = Player.STATE_BUFFERING
        invalidateState()

        val preBufferBytes = calculatePreBufferBytes(item)

        streamProxyJob = scope.launch {
            isDownloading = true
            downloadComplete = false
            bytesDownloaded = 0
            underrunPositionMs = 0L

            try {
                val tempFile = withContext(Dispatchers.IO) {
                    StreamProxy.streamToCache(
                        context = context,
                        uri = uri,
                        preBufferBytes = preBufferBytes,
                        onPreBufferReady = { readyFile ->
                            if (currentPlayingPath == null || currentPlayingPath != readyFile.absolutePath) {
                                currentPlayingPath = readyFile.absolutePath
                                Log.i(TAG, "Pre-buffer ready, starting playback: ${readyFile.name}")
                                // JNI 调用在 IO 线程执行
                                scope.launch(Dispatchers.IO) { RustEngine.play(readyFile.absolutePath); applyDspOnce() }
                                startPreCacheNextTrack()
                            }
                        },
                        onProgress = { bytes -> bytesDownloaded = bytes }
                    )
                }

                if (tempFile != null) {
                    downloadComplete = true
                    if (playbackState == Player.STATE_BUFFERING && playWhenReady) {
                        // JNI 调用移到引擎线程
                        withContext(RustEngine.engineDispatcher) {
                            RustEngine.play(tempFile.absolutePath)
                            applyDspOnce()
                            if (underrunPositionMs > 0) {
                                RustEngine.seek(underrunPositionMs / 1000.0)
                            }
                        }
                    }
                } else {
                    throw Exception("StreamProxy returned null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream failed", e)
                playbackState = Player.STATE_IDLE
                isPlaying = false
                invalidateState()
            }
        }
    }

    // ═══════════════════════════════════════════════
    // Pre-caching
    // ═══════════════════════════════════════════════

    private fun cancelPreCache() {
        preCacheJob?.cancel()
        preCacheJob = null
        preCachedPath = null
        preCachedItemIndex = -1
    }

    private fun startPreCacheNextTrack() {
        cancelPreCache()
        val nextIndex = currentMediaItemIndex + 1
        if (nextIndex >= playlist.size) return
        if (repeatMode == Player.REPEAT_MODE_ONE) return

        val nextItem = playlist[nextIndex].item
        val uri = nextItem.localConfiguration?.uri ?: return
        val uriStr = uri.toString()
        if (!uriStr.startsWith("cp://") && !uriStr.startsWith("http://") && !uriStr.startsWith("https://")) return

        preCacheJob = scope.launch(Dispatchers.IO) {
            try {
                val file = StreamProxy.streamToCache(
                    context = context,
                    uri = uri,
                    preBufferBytes = 0,
                    onPreBufferReady = null,
                    onProgress = { _ -> }
                )
                if (file != null && isActive) {
                    preCachedPath = file.absolutePath
                    preCachedItemIndex = nextIndex
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pre-cache failed: ${e.message}")
            }
        }
    }
}
