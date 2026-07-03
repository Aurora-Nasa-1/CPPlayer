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
        private const val SEEK_WATCHDOG_MS = 500L
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
    @Volatile private var lastBitDepth = 0
    @Volatile private var lastChannels = 0
    @Volatile private var lastCodecName = ""
    @Volatile private var currentPlayingPath: String? = null
    @Volatile private var recoveryPlayIssued = false

    /**
     * 网络缓冲活跃标志。
     *
     * 当流媒体因网络加载不足而进入缓冲状态时设置为 true。
     * 在此期间，Rust 引擎可能发送 "paused" 状态事件（引擎内部因缓冲不足而暂停输出），
     * 但这不是用户主动暂停，不应清除 playWhenReady 或显示为暂停状态。
     *
     * 设置时机：handleStateChanged("buffering")、handleTrackEnded（流媒体未完成）、handleError（流媒体未完成）
     * 清除时机：播放恢复（handleStateChanged("playing")）、用户暂停（handleSetPlayWhenReady(false)）、
     *          播放停止（handleStop/handleSetMediaItems/playCurrentItem）
     */
    @Volatile private var bufferingActive = false

    /**
     * 流媒体 Seek 等待数据标志。
     *
     * 当流媒体播放期间用户 Seek 到未加载位置时设置为 true。
     * Rust 引擎在 Seek 后会立即发送 "Playing" 状态事件（解码器已创建），
     * 但此时解码器可能还没有产出有效音频数据（Seek 目标超出已下载范围）。
     *
     * 在此标志为 true 期间，忽略引擎的 "Playing" 事件，保持 STATE_BUFFERING。
     * 收到进度事件（证明解码器已产出音频）后自动清除。
     *
     * 设置时机：handleSeek（流媒体播放中）
     * 清除时机：handleProgress（收到进度）、playCurrentItem、handleStop、handleSetPlayWhenReady(false)
     */
    @Volatile private var seekDuringStreaming = false

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
    private var stateReconcileJob: Job? = null

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
        startStateReconcile()
    }

    fun getFormatInfo(): Pair<Int, Int> = lastSampleRate to lastBitrate

    fun getExtendedFormatInfo(): FormatInfo = FormatInfo(
        lastSampleRate, lastBitrate, lastBitDepth, lastChannels, lastCodecName
    )

    data class FormatInfo(
        val sampleRate: Int,
        val bitrate: Int,
        val bitDepth: Int,
        val channels: Int,
        val codecName: String
    )

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
                val result = if (bands.isEmpty()) {
                    RustEngine.setEqualizer(true, floatArrayOf(), floatArrayOf(), floatArrayOf())
                } else {
                    val freqs = FloatArray(bands.size)
                    val gains = FloatArray(bands.size)
                    val qs = FloatArray(bands.size)
                    for (i in bands.indices) {
                        val band = bands[i]
                        freqs[i] = band.freq
                        gains[i] = band.gain
                        qs[i] = band.q
                    }
                    RustEngine.setEqualizer(true, freqs, gains, qs)
                }
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

    /**
     * 定期校验播放状态。
     *
     * Rust 引擎在后台可能因音频输出抖动短暂发出 "paused" 事件后继续播放，
     * 但 FlickPlayer 的 isPlaying 已被设为 false。此协程周期性查询引擎真实状态
     * 并修正 FlickPlayer 的状态，确保 MediaSession 报告正确的播放状态。
     */
    private fun startStateReconcile() {
        stateReconcileJob?.cancel()
        stateReconcileJob = scope.launch {
            while (isActive) {
                delay(3000)
                if (playlist.isEmpty() || currentPlayingPath == null) continue

                // 在引擎线程查询真实状态，避免主线程 JNI 调用
                val engineState = withContext(RustEngine.engineDispatcher) {
                    RustEngine.getState()
                }

                val engineIsPlaying = engineState.equals("playing", ignoreCase = true)

                // 引擎实际在播放但 FlickPlayer 认为暂停 → 修正状态
                if (engineIsPlaying && !isPlaying && playWhenReady && !pauseRequested) {
                    Log.w(TAG, "State reconcile: engine is '$engineState' but isPlaying=$isPlaying, correcting")
                    isPlaying = true
                    if (playbackState != Player.STATE_READY) {
                        playbackState = Player.STATE_READY
                    }
                    bufferingActive = false
                    positionUpdateTimeMs.set(System.currentTimeMillis())
                    invalidateState()
                }
                // 引擎已停止但 FlickPlayer 认为在播放 → 修正状态
                else if (!engineIsPlaying && isPlaying && !pauseRequested &&
                    !bufferingActive && engineState.equals("stopped", ignoreCase = true)) {
                    Log.w(TAG, "State reconcile: engine is '$engineState' but isPlaying=$isPlaying, correcting")
                    isPlaying = false
                    playbackState = Player.STATE_IDLE
                    invalidateState()
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
                // 流媒体 Seek 后：接受 "Playing" 状态，同时启动看门狗。
                // 如果 Seek 目标超出已下载范围，解码器会阻塞在 I/O 上（ring buffer 为空，
                // 音频回调输出静音），引擎不会发送 TrackEnded 也不会发送进度。
                // 看门狗在 500ms 后检查进度是否推进，如果没有则主动进入缓冲恢复。

                // 缓冲恢复：引擎从缓冲/暂停状态恢复到播放状态
                bufferingActive = false
                recoveryPlayIssued = false

                // 暂停请求进行中时，忽略引擎的 "playing" 状态事件，
                // 防止旧事件覆盖用户的暂停意图。但如果此时 playWhenReady 也是 true，
                // 说明用户可能在短暂暂停后又立刻点击了播放，或者是底层的其他播放恢复动作。
                // 这时我们需要清除 pauseRequested 并接受 playing 状态。
                if (pauseRequested) {
                    if (playWhenReady) {
                        pauseRequested = false
                    } else {
                        return
                    }
                }
                isPlaying = true
                playbackState = Player.STATE_READY
                positionUpdateTimeMs.set(System.currentTimeMillis())
                invalidateState()

                // 流媒体 Seek 看门狗：检测解码器是否卡在未下载位置
                if (seekDuringStreaming && isDownloading && !downloadComplete) {
                    seekDuringStreaming = false
                    startSeekWatchdog()
                }
            }
            "paused" -> {
                // 流媒体缓冲期间引擎发出的 "paused" 事件不是用户主动暂停，
                // 而是引擎因数据不足暂停输出。保持 STATE_BUFFERING 状态，
                // 等待数据恢复后自动继续播放。
                if (bufferingActive && isDownloading && !downloadComplete) {
                    Log.d(TAG, "Ignoring engine 'paused' during active buffering (underrun)")
                    // 保持 bufferingActive = true，保持 STATE_BUFFERING
                    // 不清除 pauseRequested（非用户操作）
                    invalidateState()
                    return
                }

                pauseRequested = false
                isPlaying = false
                playbackState = Player.STATE_READY
                invalidateState()
            }
            "stopped", "idle" -> {
                // 流媒体缓冲期间的 stopped/idle 也可能是缓冲不足导致的
                if (bufferingActive && isDownloading && !downloadComplete) {
                    Log.d(TAG, "Ignoring engine '$state' during active buffering")
                    return
                }
                bufferingActive = false
                isPlaying = false
                playbackState = Player.STATE_IDLE
                invalidateState()
            }
            "buffering" -> {
                if (isDownloading && !downloadComplete) {
                    bufferingActive = true
                    underrunPositionMs = Math.max(underrunPositionMs, currentPositionMs.get())
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
        if (event.bitDepth > 0) lastBitDepth = event.bitDepth
        if (event.channels > 0) lastChannels = event.channels
        if (event.codecName.isNotEmpty()) lastCodecName = event.codecName
    }

    /**
     * 曲目播放完毕。
     *
     * 根据 repeatMode / shuffleMode 决定下一步：
     * - REPEAT_MODE_ONE   → 重播当前曲目
     * - shuffleMode       → 随机选择下一首
     * - REPEAT_MODE_ALL   → 到末尾时回到第一首
     * - REPEAT_MODE_OFF   → 到末尾时停止
     */
    private fun handleTrackEnded(eventPath: String) {
        // 忽略旧曲目的 TrackEnded
        if (currentPlayingPath != null && !currentPlayingPath.equals(eventPath, ignoreCase = true)) {
            Log.w(TAG, "Ignoring TrackEnded for old path: $eventPath")
            return
        }

        // 流媒体未下载完毕 → 缓冲等待
        if (isDownloading && !downloadComplete) {
            bufferingActive = true
            underrunPositionMs = currentPositionMs.get()
            Log.w(TAG, "Underrun at ${underrunPositionMs}ms, waiting for data...")
            isPlaying = false
            playbackState = Player.STATE_BUFFERING
            invalidateState()
            startUnderrunWatch()
            return
        }

        // 单曲循环 → 重播当前曲目
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            Log.i(TAG, "Track ended, repeat one: replaying $currentMediaItemIndex")
            playCurrentItem()
            return
        }

        // 随机播放 → 随机选择下一首（避免连续重复）
        if (shuffleModeEnabled && playlist.size > 1) {
            var nextIndex: Int
            do {
                nextIndex = (0 until playlist.size).random()
            } while (nextIndex == currentMediaItemIndex)
            Log.i(TAG, "Track ended, shuffle: random next $nextIndex")
            currentMediaItemIndex = nextIndex
            playCurrentItem()
            return
        }

        // 顺序播放
        if (currentMediaItemIndex < playlist.size - 1) {
            Log.i(TAG, "Track ended, advancing to next: ${currentMediaItemIndex + 1}")
            currentMediaItemIndex++
            playCurrentItem()
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            // 列表循环 → 回到第一首
            Log.i(TAG, "Track ended, repeat all: back to 0")
            currentMediaItemIndex = 0
            playCurrentItem()
        } else {
            // 不循环 → 停止
            Log.i(TAG, "Track ended, no more tracks")
            isPlaying = false
            playbackState = Player.STATE_ENDED
            invalidateState()
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "Rust error: $message")

        if (isDownloading && !downloadComplete) {
            bufferingActive = true
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
    // Seek watchdog (streaming seek recovery)
    // ═══════════════════════════════════════════════

    /** 流媒体 Seek 看门狗：检测解码器是否卡在未下载位置 */
    private var seekWatchdogJob: Job? = null
    private var seekRecoveryJob: Job? = null

    private fun startSeekWatchdog() {
        seekWatchdogJob?.cancel()
        val watchStartPosition = currentPositionMs.get()
        seekWatchdogJob = scope.launch {
            delay(SEEK_WATCHDOG_MS)

            // 500ms 后检查：如果位置未推进且仍在播放状态，说明解码器卡在未下载位置
            if (isActive && isDownloading && !downloadComplete &&
                playbackState == Player.STATE_READY && playWhenReady) {
                Log.w(TAG, "Seek watchdog: decoder stuck at ${watchStartPosition}ms (no progress), entering underrun recovery")
                bufferingActive = true
                underrunPositionMs = watchStartPosition
                isPlaying = false
                playbackState = Player.STATE_BUFFERING
                invalidateState()
                startUnderrunWatch()
            }
        }
    }

    /**
     * 流媒体 Seek 后恢复看门狗。
     *
     * Rust 引擎对正在写入的临时文件执行 Seek 可能静默失败，引擎进入 paused 状态。
     * 此看门狗在 1.5s 后检测：若 playWhenReady=true 但引擎未在播放（isPlaying=false
     * 或 playbackState 不是 STATE_READY），则强制 stop+play+seek 恢复播放。
     */
    private fun startSeekRecoveryWatchdog(targetPositionMs: Long) {
        seekRecoveryJob?.cancel()
        seekRecoveryJob = scope.launch {
            delay(1500)
            if (!isActive) return@launch

            // 检测：playWhenReady=true 但引擎未在播放
            if (playWhenReady && (!isPlaying || playbackState != Player.STATE_READY)) {
                Log.w(TAG, "Seek recovery: engine stalled after seek (isPlaying=$isPlaying, state=$playbackState), force restart at ${targetPositionMs}ms")
                bufferingActive = false
                seekDuringStreaming = false
                val path = currentPlayingPath ?: return@launch
                withContext(RustEngine.engineDispatcher) {
                    RustEngine.stop()
                    RustEngine.play(path)
                    if (targetPositionMs > 0) {
                        delay(100)
                        RustEngine.seek(targetPositionMs / 1000.0)
                    }
                }
                isPlaying = false
                playbackState = Player.STATE_BUFFERING
                updatePosition(targetPositionMs)
                invalidateState()
            }
        }
    }

    // ═══════════════════════════════════════════════
    // Underrun recovery
    // ═══════════════════════════════════════════════

    private fun startUnderrunWatch() {
        underrunWatchJob?.cancel()
        underrunWatchJob = scope.launch {
            var targetReached = false
            val positionToRecover = if (underrunPositionMs > 0) underrunPositionMs else currentPositionMs.get()
            val underrunAtBytes = bytesDownloaded

            while (isActive && isDownloading && !downloadComplete) {
                delay(UNDERRUN_POLL_MS)

                // 计算恢复播放所需的最小下载量：
                // 必须确保 Seek 目标位置的数据已经下载到文件中，
                // 否则 play() 重启解码器后 seek() 到未下载位置会再次失败（从头播放）。
                val targetBytes = if (lastBitrate > 0) {
                    // Seek 位置对应的字节 + 5秒安全缓冲
                    (lastBitrate.toLong() * (positionToRecover + TARGET_PRE_BUFFER_SECS * 1000L)) / 8000
                } else {
                    // bitrate 未知：使用已下载数据量 + 预缓冲量作为恢复阈值。
                    // 避免 Long.MAX_VALUE 导致永远等不到恢复（流式播放不需要等整首歌下载完）。
                    underrunAtBytes + MIN_PRE_BUFFER_BYTES
                }

                if (bytesDownloaded > targetBytes) {
                    Log.i(TAG, "Underrun recovered at ${positionToRecover}ms, bytesDownloaded: $bytesDownloaded > targetBytes: $targetBytes")
                    targetReached = true
                    break
                }
            }

            // 如果下载已完成或由于数据量已达标而跳出循环，且不是因为被取消或出错
            if (isActive && (downloadComplete || targetReached) && playbackState == Player.STATE_BUFFERING) {
                if (!recoveryPlayIssued) {
                    recoveryPlayIssued = true
                    bufferingActive = false
                    val path = currentPlayingPath ?: return@launch
                    // JNI 调用移到引擎线程
                    withContext(RustEngine.engineDispatcher) {
                        RustEngine.play(path)
                        applyDspOnce()
                        if (positionToRecover > 0) {
                            delay(100)
                            RustEngine.seek(positionToRecover / 1000.0)
                        }
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
            val cached = durationCache[path]
            if (cached != null) return cached

            // 异步提取时长，避免阻塞主线程（可能引发 ANR）
            scope.launch(Dispatchers.IO) {
                val duration = extractDuration(Uri.parse(path), false)
                if (duration > 0) {
                    durationCache[path] = duration
                    // 通知重新获取并更新状态
                    withContext(Dispatchers.Main) {
                        if (currentPlayingPath == path || currentPlayingPath == getRealPathFromUri(path)) {
                            durationMs = duration
                            invalidateState()
                        }
                    }
                }
            }
            return 0L
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
                // 不插值：直接使用 Rust 引擎报告的位置。
                // 之前用墙钟时间推算 (elapsed) 会导致报告位置领先于实际音频输出，
                // 造成歌词逐字动画滞后于听觉。
                currentPositionMs.get()
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
            // 用户主动暂停：清除缓冲恢复相关标志
            bufferingActive = false
            seekDuringStreaming = false
            underrunWatchJob?.cancel()
            seekWatchdogJob?.cancel()
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
        // 如果用户执行了 Seek，并且 playWhenReady 是 true，我们认为用户希望继续播放
        if (playWhenReady) {
            pauseRequested = false
        }
        val targetPosition = if (positionMs == C.TIME_UNSET) 0L else positionMs
        if (mediaItemIndex != currentMediaItemIndex && mediaItemIndex in playlist.indices) {
            currentMediaItemIndex = mediaItemIndex
            cancelPreCache()
            playCurrentItem()
        } else {
            lastSeekTimeMs.set(System.currentTimeMillis())

            // 流媒体播放中 Seek：保持 bufferingActive，标记等待数据
            // Rust 引擎 Seek 后会立即发送 "Playing"（解码器已创建），
            // 但如果 Seek 目标超出已下载范围，解码器无法产出有效音频。
            // 通过 seekDuringStreaming 标志延迟接受 "Playing"，直到收到进度事件。
            if (isDownloading && !downloadComplete) {
                bufferingActive = true
                seekDuringStreaming = true
                isPlaying = false
                playbackState = Player.STATE_BUFFERING
                underrunWatchJob?.cancel()
                seekWatchdogJob?.cancel()
            }

            // JNI 调用移到引擎线程
            val seekPosMs = targetPosition
            scope.launch(Dispatchers.IO) {
                RustEngine.seek(seekPosMs / 1000.0)
            }
            updatePosition(targetPosition)

            // 流媒体 Seek 后恢复看门狗：
            // Rust 引擎对正在写入的文件执行 Seek 可能失败（静默暂停），
            // 导致 playWhenReady=true 但 isPlaying=false，UI 显示播放但无声音。
            // 1.5s 后检测：若 playWhenReady 且引擎未在播放，强制 stop+play+seek 恢复。
            if (isDownloading && !downloadComplete) {
                startSeekRecoveryWatchdog(seekPosMs)
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        scope.launch(Dispatchers.IO) { RustEngine.stop() }
        pauseRequested = false
        bufferingActive = false
        seekDuringStreaming = false
        seekRecoveryJob?.cancel()
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
        seekWatchdogJob?.cancel()
        seekRecoveryJob?.cancel()
        stateReconcileJob?.cancel()
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
        recoveryPlayIssued = false
        bufferingActive = false
        seekDuringStreaming = false
        underrunWatchJob?.cancel()
        seekWatchdogJob?.cancel()
        seekRecoveryJob?.cancel()
        // Reset format info for new track (will be updated by FormatChanged event)
        lastSampleRate = 0
        lastBitrate = 0
        lastBitDepth = 0
        lastChannels = 0
        lastCodecName = ""

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
            recoveryPlayIssued = false

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
                    // 下载完成，检查是否需要恢复播放：
                    // 1. 正在缓冲状态（underrun 发生过）
                    // 2. 播放状态为 STATE_BUFFERING
                    // 3. 用户没有主动暂停（bufferingActive 仍为 true 说明非用户暂停）
                    if (bufferingActive && playbackState == Player.STATE_BUFFERING) {
                        if (!recoveryPlayIssued) {
                            recoveryPlayIssued = true
                            bufferingActive = false
                            // JNI 调用移到引擎线程
                            withContext(RustEngine.engineDispatcher) {
                                RustEngine.play(tempFile.absolutePath)
                                applyDspOnce()
                                if (underrunPositionMs > 0) {
                                    delay(100)
                                    RustEngine.seek(underrunPositionMs / 1000.0)
                                }
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
        if (repeatMode == Player.REPEAT_MODE_ONE) return
        val nextIndex = if (shuffleModeEnabled && playlist.size > 1) {
            var idx: Int
            do { idx = (0 until playlist.size).random() } while (idx == currentMediaItemIndex)
            idx
        } else if (currentMediaItemIndex + 1 < playlist.size) {
            currentMediaItemIndex + 1
        } else if (repeatMode == Player.REPEAT_MODE_ALL && playlist.isNotEmpty()) {
            0
        } else {
            return
        }

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
