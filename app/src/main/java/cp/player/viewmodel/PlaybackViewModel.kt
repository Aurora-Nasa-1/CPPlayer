package cp.player.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import cp.player.model.Song
import cp.player.service.MusicService
import cp.player.lyrics.LyricsManager
import cp.player.util.DebugLog
import cp.player.util.toMediaItem
import cp.player.util.toSong
import cp.player.util.buildLocalContentUri
import cp.player.util.UserPreferences
import cp.player.manager.DownloadRegistry
import cp.player.repository.PlaybackRepository
import cp.player.usecase.MediaMetadataUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class PlaybackViewModel(application: Application) : BaseViewModel(application) {
    private val playbackRepository = PlaybackRepository()
    private val mediaMetadataUseCase = MediaMetadataUseCase(application)

    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)

    var currentCommentSortType by mutableIntStateOf(1)
    var currentSampleRate by mutableIntStateOf(0)
    var currentBitrate by mutableIntStateOf(0)
    var currentBitDepth by mutableIntStateOf(0)
    var currentChannels by mutableIntStateOf(0)
    var currentCodecName by mutableStateOf("")
    var isFmMode by mutableStateOf(false)
    var isPersonalFmLoading by mutableStateOf(false)
    var isHeartbeatLoading by mutableStateOf(false)
    var localSongs by mutableStateOf<List<Pair<Song, android.net.Uri>>>(emptyList())
    var sleepTimerRemaining by mutableLongStateOf(0L)
    var extractedColor by mutableStateOf<Int?>(null)

    /** LiveSort 播放时的 per-song 淡入淡出覆盖。key = song.id, value = (fadeInSec, fadeOutSec) */
    var livesortFadeOverrides by mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap())

    private var sleepTimerJob: Job? = null
    private var isFetchingMoreFm = false

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    init {
        initController(application)

        viewModelScope.launch {
            cp.player.engine.RustEngine.audioEvents.collectLatest { event ->
                if (event is cp.player.engine.AudioEvent.FormatChanged) {
                    currentSampleRate = event.sampleRate
                    currentBitrate = event.bitrate
                    currentBitDepth = event.bitDepth
                    currentChannels = event.channels
                    currentCodecName = event.codecName
                }
            }
        }

        viewModelScope.launch {
            DownloadRegistry.downloadedSongsFlow.collectLatest { list ->
                localSongs = list.mapNotNull { metadata ->
                    val song = metadata.song
                    val uri = if (metadata.filePath?.startsWith("content://") == true) {
                        android.net.Uri.parse(metadata.filePath)
                    } else {
                        android.net.Uri.fromFile(java.io.File(metadata.filePath ?: ""))
                    }
                    song to uri
                }
            }
        }

        refreshLocalSongs()
    }

    fun refreshLocalSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            mediaMetadataUseCase.refreshLocalSongs()
        }
    }

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle): ListenableFuture<androidx.media3.session.SessionResult> {
                    when (command.customAction) {
                        "UPDATE_PLAYBACK_INFO" -> {
                            currentSampleRate = args.getInt("sampleRate")
                            currentBitrate = args.getInt("bitrate")
                            currentBitDepth = args.getInt("bitDepth")
                            currentChannels = args.getInt("channels")
                            currentCodecName = args.getString("codecName", "")
                        }
                        "ACTION_PLAYER_ERROR" -> {
                            DebugLog.toast(getApplication(), args.getString("error") ?: "Unknown error")
                        }
                        "ACTION_SONG_CHANGED" -> {
                            val mediaId = args.getString("mediaId") ?: return@onCustomCommand com.google.common.util.concurrent.Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
                            val title = args.getString("title") ?: "Unknown"
                            val artist = args.getString("artist") ?: "Unknown"
                            val album = args.getString("album") ?: "Unknown"
                            val artworkUri = args.getString("artworkUri")
                            DebugLog.i("PlaybackVM: ACTION_SONG_CHANGED id=$mediaId title=$title")
                            val song = Song(id = mediaId, name = title, artist = artist, album = album, albumArtUrl = artworkUri)
                            currentSong = song
                            LyricsManager.fetch(mediaId, getApplication(), songTitle = title, songArtist = artist)
                            extractColorFromUrl(artworkUri)
                        }
                    }
                    return com.google.common.util.concurrent.Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
                }
            })
            .buildAsync()

        mediaControllerFuture?.addListener({
            mediaController?.let { controller ->
                syncState(controller)
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        // mediaItem 可能为 null，直接从 controller 获取当前歌曲
                        val item = mediaItem ?: controller.currentMediaItem
                        DebugLog.i("PlaybackVM: onMediaItemTransition id=${item?.mediaId} reason=$reason")
                        updateSongFromMediaItem(item)
                        if (isFmMode && controller.currentMediaItemIndex >= controller.mediaItemCount - 2) {
                            fetchMoreFmSongs()
                        }
                        updateQueue()
                    }
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        updateQueue()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                            val d = controller.duration
                            if (d != C.TIME_UNSET) duration = d.coerceAtLeast(0L)
                        }
                    }
                    override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
                    override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffleMode = enabled }
                })

                viewModelScope.launch {
                    var lastMediaId: String? = null
                    while (isActive) {
                        currentPosition = controller.currentPosition
                        // 轮询检测歌曲切换（MediaController 的 onMediaItemTransition 可能不触发）
                        val currentItem = controller.currentMediaItem
                        val currentMediaId = currentItem?.mediaId
                        if (currentMediaId != null && currentMediaId != lastMediaId) {
                            lastMediaId = currentMediaId
                            val song = currentItem.toSong()
                            if (currentSong?.id != song.id) {
                                DebugLog.i("PlaybackVM: poll detected song change → ${song.id} ${song.name}")
                                currentSong = song
                                // 歌词由 MusicService 通过 ACTION_SONG_CHANGED 触发，轮询只更新歌曲信息
                                extractColorFromUrl(song.albumArtUrl)
                            }
                        }
                        delay(100L)
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun syncState(controller: MediaController) {
        isPlaying = controller.isPlaying
        currentPosition = controller.currentPosition
        duration = controller.duration.coerceAtLeast(0L)
        repeatMode = controller.repeatMode
        shuffleMode = controller.shuffleModeEnabled
        updateSongFromMediaItem(controller.currentMediaItem)
        updateQueue()
    }

    private fun updateSongFromMediaItem(mediaItem: MediaItem?) {
        mediaItem?.let {
            val song = it.toSong()
            DebugLog.i("PlaybackVM: updateSong id=${song.id} name=${song.name}")
            currentSong = song
            // 歌词由 MusicService 通过 ACTION_SONG_CHANGED 触发，此处不重复调用
            extractColorFromUrl(song.albumArtUrl)
        }
    }

    private fun extractColorFromUrl(url: String?) {
        if (url.isNullOrEmpty()) {
            extractedColor = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val color = mediaMetadataUseCase.extractColorFromUrl(url)
            withContext(Dispatchers.Main) {
                if (color != null && color != 0) {
                    extractedColor = color
                }
            }
        }
    }

    fun updateQueue() {
        mediaController?.let { controller ->
            val list = (0 until controller.mediaItemCount).map { i ->
                controller.getMediaItemAt(i).toSong()
            }
            currentQueue = list
        }
    }

    fun playSong(song: Song?, playlist: List<Song?> = emptyList(), resetFmMode: Boolean = true) {
        if (song == null) return
        if (resetFmMode) isFmMode = false
        val target = (if (playlist.isNotEmpty()) playlist else listOf(song)).filterNotNull()
        val startIndex = target.indexOf(song).coerceAtLeast(0)

        runWithController { controller ->
            controller.stop()
            controller.clearMediaItems()
            val items = target.mapNotNull { createMediaItem(it) }
            controller.setMediaItems(items, startIndex.coerceAtMost(items.size - 1), 0L)
            controller.prepare()
            controller.play()
        }
    }

    private fun createMediaItem(song: Song?): MediaItem? {
        if (song == null) return null
        val localUri = localSongs.find { (s, _) ->
            @Suppress("SENSELESS_COMPARISON")
            s != null && s.id == song.id
        }?.second ?: song.buildLocalContentUri()
        val quality = UserPreferences.getQualityWifi(getApplication())
        return song.toMediaItem(localUri, quality, cookie)
    }

    fun togglePlayPause() = runWithController { if (it.isPlaying) it.pause() else it.play() }
    fun skipNext() = runWithController { it.seekToNext() }
    fun skipPrevious() = runWithController { it.seekToPrevious() }
    fun seekTo(pos: Long) = runWithController { controller ->
        val safePos = pos.coerceIn(0L, duration.coerceAtLeast(0L))
        controller.seekTo(safePos)
        currentPosition = safePos
    }

    fun toggleRepeatMode() = runWithController {
        val next = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        it.repeatMode = next
    }

    fun toggleShuffleMode() = runWithController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun addToQueue(song: Song?) = runWithController { controller ->
        if (song != null) {
            createMediaItem(song)?.let { 
                val wasEmpty = controller.mediaItemCount == 0
                controller.addMediaItem(it)
                updateQueue()
                if (wasEmpty) {
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }
    
    fun insertNext(song: Song?) = runWithController { controller ->
        if (song != null) {
            createMediaItem(song)?.let {
                val wasEmpty = controller.mediaItemCount == 0
                val nextIndex = if (wasEmpty) 0 else controller.currentMediaItemIndex + 1
                controller.addMediaItem(nextIndex, it)
                updateQueue()
                if (wasEmpty) {
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    fun addSongsToQueue(songs: List<Song>) = runWithController { controller ->
        if (songs.isNotEmpty()) {
            val wasEmpty = controller.mediaItemCount == 0
            val items = songs.mapNotNull { createMediaItem(it) }
            controller.addMediaItems(items)
            updateQueue()
            if (wasEmpty) {
                controller.prepare()
                controller.play()
            }
        }
    }
    fun playAt(index: Int) = runWithController { controller ->
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun moveQueueItem(f: Int, t: Int) = runWithController { it.moveMediaItem(f, t); updateQueue() }
    fun removeQueueItem(i: Int) = runWithController { it.removeMediaItem(i); updateQueue() }
    fun clearQueue() = runWithController { it.clearMediaItems(); updateQueue() }

    private fun runWithController(action: (MediaController) -> Unit) {
        mediaController?.let(action) ?: mediaControllerFuture?.addListener({ mediaController?.let(action) }, MoreExecutors.directExecutor())
    }

    fun playPersonalFm() {
        viewModelScope.launch {
            isPersonalFmLoading = true
            try {
                val songs = withContext(Dispatchers.IO) { playbackRepository.getPersonalFm(cookie) }
                if (songs.isNotEmpty()) { isFmMode = true; playSong(songs[0], songs, resetFmMode = false) }
            } finally { isPersonalFmLoading = false }
        }
    }

    private fun fetchMoreFmSongs() {
        if (isFetchingMoreFm) return
        isFetchingMoreFm = true
        viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) { playbackRepository.getPersonalFm(cookie) }
                runWithController { controller ->
                    songs.filterNotNull().forEach { song ->
                        createMediaItem(song)?.let { controller.addMediaItem(it) }
                    }
                }
            } finally { isFetchingMoreFm = false }
        }
    }

    fun playHeartbeat(songId: String, playlistId: Long) {
        viewModelScope.launch {
            try {
                isHeartbeatLoading = true
                DebugLog.d("Heartbeat: id=$songId, pid=$playlistId")
                val songs = withContext(Dispatchers.IO) { playbackRepository.getHeartbeatSongs(songId, playlistId, cookie) }
                if (songs.isNotEmpty()) {
                    playSong(songs[0], songs)
                } else {
                    DebugLog.toast(getApplication(), "Heartbeat mode: No songs returned")
                }
            } catch (e: Exception) {
                DebugLog.e("Heartbeat error: ${e.message}")
            } finally {
                isHeartbeatLoading = false
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) { sleepTimerRemaining = 0L; return }
        sleepTimerRemaining = minutes * 60 * 1000L
        sleepTimerJob = viewModelScope.launch {
            while (sleepTimerRemaining > 0) { delay(1000); sleepTimerRemaining -= 1000 }
            runWithController { it.pause() }
            sleepTimerRemaining = 0L
        }
    }

    fun deleteLocalSong(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val songId = localSongs.find { it.second == uri }?.first?.id
                val deleted = if (uri.scheme == "content") {
                    try { context.contentResolver.delete(uri, null, null) > 0 }
                    catch (e: Exception) { androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.delete() ?: false }
                } else {
                    java.io.File(uri.path ?: "").delete()
                }
                if (deleted && songId != null) {
                    DownloadRegistry.unregister(context, songId)
                }
            } catch (e: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
