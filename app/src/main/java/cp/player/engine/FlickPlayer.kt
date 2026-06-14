package cp.player.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cp.player.service.BackendDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class FlickPlayer(private val context: Context) : SimpleBasePlayer(Looper.getMainLooper()) {

    companion object {
        private const val TAG = "FlickPlayer"
        private const val CACHE_PREFIX = "stream_cache_"
        private const val CACHE_EXPIRY_MS = 1800000L // 30 mins
    }

    private val engineLock = ReentrantLock()
    private var playlist: List<MediaItem> = emptyList()
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
    @Volatile private var isTransitioning = false

    private val scope = CoroutineScope(Dispatchers.Main)
    private var engineEventJob: Job? = null
    private var pollJobTimer: Job? = null
    private var streamProxyJob: Job? = null

    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffleModeEnabled = false

    init {
        RustEngine.initEngine(context)
        RustEngine.setVolume(1.0f)
        observeRustEvents()
    }

    private fun observeRustEvents() {
        engineEventJob = scope.launch {
            RustEngine.audioEvents.collect { event ->
                if (playlist.isEmpty()) return@collect
                
                when (event) {
                    is AudioEvent.StateChanged -> handleStateChanged(event.state)
                    is AudioEvent.Progress -> handleProgress(event)
                    is AudioEvent.TrackEnded -> handleTrackEnded(event.path)
                    is AudioEvent.Error -> handleError(event.message)
                    else -> {}
                }
            }
        }
    }

    private fun handleStateChanged(state: String) {
        when (state.lowercase()) {
            "playing" -> {
                isPlaying = true
                playbackState = Player.STATE_READY
                isTransitioning = false
                positionUpdateTimeMs.set(System.currentTimeMillis())
                startProgressPolling()
                invalidateState()
            }
            "paused" -> {
                isPlaying = false
                playbackState = Player.STATE_READY
                isTransitioning = false
                stopProgressPolling()
                invalidateState()
            }
            "stopped", "idle" -> {
                if (!isTransitioning) {
                    isPlaying = false
                    playbackState = Player.STATE_IDLE
                    stopProgressPolling()
                    invalidateState()
                }
            }
            "buffering" -> {
                playbackState = Player.STATE_BUFFERING
                invalidateState()
            }
        }
    }

    private fun handleProgress(event: AudioEvent.Progress) {
        if (System.currentTimeMillis() - lastSeekTimeMs.get() < 1000) return

        val newPos = (event.positionSecs * 1000).toLong()
        val current = currentPositionMs.get()
        
        // Ignore small back-jumps due to buffer refill or decoder jitter
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

    private var currentPlayingPath: String? = null

    private fun handleTrackEnded(eventPath: String) {
        // Ignore TrackEnded events from previously playing tracks
        if (currentPlayingPath != null && !currentPlayingPath.equals(eventPath, ignoreCase = true)) {
            Log.w(TAG, "Ignoring TrackEnded for old path: $eventPath. Current is: $currentPlayingPath")
            return
        }

        if (isDownloading && !downloadComplete) {
            Log.w(TAG, "Premature track end detected while downloading. Buffering...")
            playbackState = Player.STATE_BUFFERING
            invalidateState()
            return
        }

        Log.i(TAG, "Track ended. Next: ${currentMediaItemIndex < playlist.size - 1}")
        if (currentMediaItemIndex < playlist.size - 1) {
            isTransitioning = true
            currentMediaItemIndex++
            playCurrentItem()
        } else {
            if (downloadComplete || !isDownloading) {
                isPlaying = false
                playbackState = Player.STATE_ENDED
                stopProgressPolling()
                invalidateState()
            } else {
                Log.w(TAG, "Premature track end detected while downloading. Ignoring.")
            }
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "Received Rust AudioEvent.Error: $message")
        playbackState = Player.STATE_IDLE
        isPlaying = false
        stopProgressPolling()
        invalidateState()
    }

    private fun updatePosition(newPos: Long) {
        currentPositionMs.set(newPos)
        positionUpdateTimeMs.set(System.currentTimeMillis())
        invalidateState()
    }

    private fun startProgressPolling() {
        pollJobTimer?.cancel()
        pollJobTimer = scope.launch {
            while (isActive) {
                if (isPlaying && playbackState == Player.STATE_READY) {
                    val progress = withContext(Dispatchers.IO) { RustEngine.getProgress() }
                    if (progress != null && System.currentTimeMillis() - lastSeekTimeMs.get() >= 1000) {
                        val newPos = (progress.positionSecs * 1000).toLong()
                        if (newPos > 0) {
                            updatePosition(newPos)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressPolling() {
        pollJobTimer?.cancel()
        pollJobTimer = null
    }

    private fun getRealDuration(): Long {
        if (playlist.isEmpty()) return 0L
        val item = playlist[currentMediaItemIndex]
        val extraDuration = item.mediaMetadata.extras?.getLong("duration") ?: 0L
        if (extraDuration > 0) return extraDuration
        
        val path = item.localConfiguration?.uri?.toString() ?: return 0L
        if (!path.startsWith("cp://") && !path.startsWith("http://") && !path.startsWith("https://")) {
            return extractDuration(Uri.parse(path), false)
        }
        return 0L
    }

    private fun extractDuration(uri: Uri, isFile: Boolean): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            if (isFile) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

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
            val mediaItemDataList = playlist.mapIndexed { index, item ->
                val itemDurationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L
                val displayDurationMs = if (index == currentMediaItemIndex && durationMs > 0) {
                    durationMs
                } else if (itemDurationMs > 0) {
                    itemDurationMs
                } else {
                    C.TIME_UNSET
                }
                
                SimpleBasePlayer.MediaItemData.Builder(item.mediaId)
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
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_ENDED) {
                if (playlist.isNotEmpty()) {
                    playCurrentItem()
                }
            } else {
                engineLock.lock()
                try {
                    RustEngine.resume()
                } finally {
                    engineLock.unlock()
                }
            }
        } else {
            engineLock.lock()
            try {
                RustEngine.pause()
            } finally {
                engineLock.unlock()
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        playlist = mediaItems.toList()
        currentMediaItemIndex = if (startIndex in mediaItems.indices) startIndex else 0
        if (playWhenReady && playlist.isNotEmpty()) {
            playCurrentItem()
        }
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
                currentMediaItemIndex = validFrom.coerceAtMost(playlist.size - 1)
                if (playlist.isEmpty()) {
                    engineLock.lock()
                    try { RustEngine.stop() } finally { engineLock.unlock() }
                    isPlaying = false
                    playbackState = Player.STATE_IDLE
                    stopProgressPolling()
                } else if (playWhenReady) {
                    playCurrentItem()
                }
            }
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
            playCurrentItem()
        } else {
            engineLock.lock()
            try {
                lastSeekTimeMs.set(System.currentTimeMillis())
                RustEngine.seek(targetPosition / 1000.0)
                updatePosition(targetPosition)
            } finally {
                engineLock.unlock()
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engineLock.lock()
        try { RustEngine.stop() } finally { engineLock.unlock() }
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        isPlaying = false
        stopProgressPolling()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        engineLock.lock()
        try { RustEngine.stopEngine() } finally { engineLock.unlock() }
        engineEventJob?.cancel()
        streamProxyJob?.cancel()
        stopProgressPolling()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        engineLock.lock()
        try { RustEngine.setVolume(volume) } finally { engineLock.unlock() }
        return Futures.immediateVoidFuture()
    }

    private fun getRealPathFromUri(uriStr: String): String? {
        val uri = Uri.parse(uriStr)
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        return cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve real path for $uriStr", e)
            }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun playCurrentItem() {
        if (playlist.isEmpty()) return
        
        val item = playlist[currentMediaItemIndex]
        updatePosition(0L)
        durationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L
        isDownloading = false
        downloadComplete = false
        bytesDownloaded = 0L
        playbackState = Player.STATE_BUFFERING
        invalidateState()
        
        val path = item.localConfiguration?.uri?.toString()
        if (path != null) {
            var actualPath = path
            if (path.startsWith("content://") || path.startsWith("file://")) {
                actualPath = getRealPathFromUri(path) ?: path
            }

            if (actualPath.startsWith("cp://") || actualPath.startsWith("http://") || actualPath.startsWith("https://")) {
                streamToRustEngine(item)
                return
            }

            currentPlayingPath = actualPath
            engineLock.lock()
            try { RustEngine.play(actualPath) } finally { engineLock.unlock() }
        } else {
            Log.e(TAG, "Media URI is null for item: ${item.mediaId}")
        }
    }

    private fun streamToRustEngine(item: MediaItem) {
        streamProxyJob?.cancel()
        val uri = item.localConfiguration?.uri ?: return
        
        playbackState = Player.STATE_BUFFERING
        invalidateState()
        
        streamProxyJob = scope.launch(Dispatchers.IO) {
            isDownloading = true
            downloadComplete = false
            bytesDownloaded = 0
            var dataSource: androidx.media3.datasource.DataSource? = null
            
            try {
                val cacheDir = context.cacheDir
                var ext = "media"
                
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com"))
                    .setAllowCrossProtocolRedirects(true)
                    .setUserAgent("NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)")
                
                val cpDataSourceFactory = BackendDataSource.Factory(context, httpDataSourceFactory)
                
                val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cp.player.service.MusicService.getCache(context))
                    .setUpstreamDataSourceFactory(cpDataSourceFactory)
                    .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                dataSource = cacheDataSourceFactory.createDataSource()
                
                val dataSpec = DataSpec.Builder().setUri(uri).build()
                dataSource.open(dataSpec)
                
                val resolvedUri = dataSource.uri
                if (resolvedUri?.path != null) {
                    val path = resolvedUri.path!!.lowercase()
                    ext = when {
                        path.endsWith(".flac") -> "flac"
                        path.endsWith(".mp3") -> "mp3"
                        path.endsWith(".m4a") -> "m4a"
                        path.endsWith(".wav") -> "wav"
                        else -> "media"
                    }
                }
                
                val tempFile = File(cacheDir, "${CACHE_PREFIX}${System.currentTimeMillis()}.${ext}")
                val outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(128 * 1024)
                var hasTriggeredPlay = false
                val startThreshold = if (ext == "flac" || ext == "wav") 512 * 1024 else 256 * 1024
                
                currentPlayingPath = tempFile.absolutePath

                while (isActive) {
                    val bytesRead = dataSource.read(buffer, 0, buffer.size)
                    if (bytesRead == C.RESULT_END_OF_INPUT || bytesRead < 0) break
                    
                    outputStream.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                }
                outputStream.flush()
                outputStream.close()
                downloadComplete = true
                
                withContext(Dispatchers.Main) {
                    engineLock.lock()
                    try { RustEngine.play(tempFile.absolutePath) } finally { engineLock.unlock() }
                }
                
                cleanupCache(cacheDir, tempFile)
                
            } catch (e: Exception) {
                Log.e(TAG, "Stream processing failed", e)
                withContext(Dispatchers.Main) {
                    playbackState = Player.STATE_IDLE
                    isPlaying = false
                    stopProgressPolling()
                    invalidateState()
                }
            } finally {
                try { dataSource?.close() } catch (e: Exception) {}
            }
        }
    }

    private fun cleanupCache(cacheDir: File, currentFile: File) {
        scope.launch(Dispatchers.IO) {
            try {
                cacheDir.listFiles { _, name -> name.startsWith(CACHE_PREFIX) }?.forEach { file ->
                    if (file.absolutePath != currentFile.absolutePath && 
                        System.currentTimeMillis() - file.lastModified() > CACHE_EXPIRY_MS) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {}
        }
    }
}
