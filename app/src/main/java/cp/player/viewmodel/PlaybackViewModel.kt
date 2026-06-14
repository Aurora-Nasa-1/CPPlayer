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
import cp.player.model.LyricLine
import cp.player.model.Song
import cp.player.service.MusicService
import androidx.palette.graphics.Palette
import coil3.toBitmap
import coil3.request.allowHardware
import cp.player.util.DebugLog
import cp.player.util.JsonUtils
import cp.player.util.LyricUtils
import cp.player.util.UserPreferences
import cp.player.manager.DownloadRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class PlaybackViewModel(application: Application) : BaseViewModel(application) {
    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)
    var currentLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var currentCommentSortType by mutableIntStateOf(1)
    var currentSampleRate by mutableIntStateOf(0)
    var currentBitrate by mutableIntStateOf(0)
    var isFmMode by mutableStateOf(false)
    var localSongs by mutableStateOf<List<Pair<Song, android.net.Uri>>>(emptyList())
    var sleepTimerRemaining by mutableLongStateOf(0L)
    var extractedColor by mutableStateOf<Int?>(null)
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
            val list = mutableListOf<Pair<Song, android.net.Uri>>()

            // 1. Scan Directory and try to recover registry
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val cpMusicDir = java.io.File(musicDir, "CPPlayer")

            val idRegex = Regex("\\[(\\d+)\\]\\.(mp3|flac)$")

            if (cpMusicDir.exists()) {
                cpMusicDir.listFiles { _, name -> name.endsWith(".mp3") || name.endsWith(".flac") }?.forEach { file ->
                    val uri = android.net.Uri.fromFile(file)
                    val match = idRegex.find(file.name)
                    val songId = match?.groupValues?.get(1)

                    if (songId != null) {
                        val baseName = file.nameWithoutExtension
                        val nameArtist = baseName.substringBeforeLast(" [").split(" - ")
                        val song = Song(
                            id = songId,
                            name = nameArtist.getOrNull(0) ?: baseName,
                            artist = nameArtist.getOrNull(1) ?: "Unknown",
                            album = "Local Storage"
                        )
                        // If not in registry, add it
                        if (DownloadRegistry.getMetadata(songId) == null) {
                            DownloadRegistry.register(getApplication(), song, file.absolutePath)
                        }
                    }
                }
            }

            // The flow will take care of updating localSongs once we call register()
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
                        }
                        "ACTION_PLAYER_ERROR" -> {
                            DebugLog.toast(getApplication(), args.getString("error") ?: "Unknown error")
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
                        updateSongFromMediaItem(mediaItem)
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
                    while (isActive) {
                        currentPosition = controller.currentPosition
                        delay(1000L)
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncState(controller: MediaController) {
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
            val song = Song(
                id = it.mediaId,
                name = it.mediaMetadata.title?.toString() ?: "Unknown",
                artist = it.mediaMetadata.artist?.toString() ?: "Unknown",
                album = it.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                albumArtUrl = it.mediaMetadata.artworkUri?.toString(),
                artistId = it.mediaMetadata.extras?.getString("artistId")
            )
            currentSong = song
            fetchLyrics(it.mediaId)
            extractColorFromUrl(song.albumArtUrl)
        }
    }

    private fun extractColorFromUrl(url: String?) {
        if (url.isNullOrEmpty()) {
            extractedColor = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DebugLog.d("Extracting color from $url")
                val loader = coil3.SingletonImageLoader.get(getApplication())
                val request = coil3.request.ImageRequest.Builder(getApplication())
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is coil3.request.SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(palette.getMutedColor(0))
                    if (color != 0) {
                        DebugLog.d("Extracted color: ${Integer.toHexString(color)}")
                        withContext(Dispatchers.Main) {
                            extractedColor = color
                        }
                    } else {
                        DebugLog.e("Palette extraction returned 0")
                    }
                } else {
                    DebugLog.e("Coil result not success: $result")
                }
            } catch (e: Exception) {
                DebugLog.e("Palette extraction failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun updateQueue() {
        mediaController?.let { controller ->
            val list = mutableListOf<Song>()
            for (i in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(i)
                list.add(Song(
                    id = item.mediaId,
                    name = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                    album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                    albumArtUrl = item.mediaMetadata.artworkUri?.toString(),
                    artistId = item.mediaMetadata.extras?.getString("artistId")
                ))
            }
            currentQueue = list
        }
    }

    fun playSong(song: Song?, playlist: List<Song?> = emptyList()) {
        if (song == null) return
        isFmMode = false
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
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { android.net.Uri.parse(it) })
            .setExtras(Bundle().apply {
                putString("artistId", song.artistId)
                putLong("duration", song.durationMs)
            })
            .build()

        val localUri = localSongs.find { (s, _) -> 
            @Suppress("SENSELESS_COMPARISON")
            s != null && s.id == song.id 
        }?.second ?: if (song.id.startsWith("local_")) {
            android.net.Uri.parse("content://media/external/audio/media/${song.id.removePrefix("local_")}")
        } else null
        
        val mediaUri = if (localUri != null) {
            localUri
        } else {
            val quality = UserPreferences.getQualityWifi(getApplication())
            android.net.Uri.Builder()
                .scheme("cp")
                .authority(song.id)
                .appendQueryParameter("quality", quality)
                .apply { cookie?.let { appendQueryParameter("cookie", it) } }
                .build()
        }

        return MediaItem.Builder().setMediaId(song.id).setUri(mediaUri).setMediaMetadata(metadata).build()
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
    fun moveQueueItem(f: Int, t: Int) = runWithController { it.moveMediaItem(f, t); updateQueue() }
    fun removeQueueItem(i: Int) = runWithController { it.removeMediaItem(i); updateQueue() }
    fun clearQueue() = runWithController { it.clearMediaItems(); updateQueue() }

    private fun runWithController(action: (MediaController) -> Unit) {
        mediaController?.let(action) ?: mediaControllerFuture?.addListener({ mediaController?.let(action) }, MoreExecutors.directExecutor())
    }

    fun fetchLyrics(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = callApi("lyric/new", mapOf("id" to songId))
                val lrc = body.get("lrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                val yrc = body.get("yrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                val tlyric = body.get("tlyric")?.asJsonObject?.get("lyric")?.asString ?: ""
                val klyric = body.get("klyric")?.asJsonObject?.get("lyric")?.asString ?: ""

                var lines = if (yrc.isNotEmpty()) LyricUtils.parseYrc(yrc) else LyricUtils.parseLrc(lrc, duration)

                val finalLines = if (tlyric.isNotEmpty()) {
                    val tlines = LyricUtils.parseLrc(tlyric).associateBy { it.time }
                    lines.map { line ->
                        // Match translation within 500ms window
                        val trans = tlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
                        if (trans != null) line.copy(translation = trans.value.text) else line
                    }
                } else lines

                withContext(Dispatchers.Main) { currentLyrics = finalLines }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { currentLyrics = emptyList() }
            }
        }
    }

    fun playPersonalFm() {
        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) { callApi("personal_fm", mapOf("timestamp" to System.currentTimeMillis().toString())) }
                val songs = (body.get("data")?.asJsonArray ?: body.get("result")?.asJsonArray)
                    ?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                if (songs.isNotEmpty()) { isFmMode = true; playSong(songs[0], songs) }
            } finally { isLoading = false }
        }
    }

    private fun fetchMoreFmSongs() {
        if (isFetchingMoreFm) return
        isFetchingMoreFm = true
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { callApi("personal_fm", mapOf("timestamp" to System.currentTimeMillis().toString())) }
                val songs = (body.get("data")?.asJsonArray ?: body.get("result")?.asJsonArray)
                    ?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
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
                isLoading = true

                // If playlistId is 0, heart mode might fail.
                // However, CP API often requires a valid playlist ID that the song belongs to.
                // If it's 0, it means it's likely from 'Liked Songs' which we should have the ID for in UserViewModel.

                DebugLog.d("Heartbeat: id=$songId, pid=$playlistId")

                val params = mutableMapOf(
                    "id" to songId,
                    "pid" to playlistId.toString(),
                    "sid" to songId,
                    "count" to "20"
                )

                val body = withContext(Dispatchers.IO) { callApi("playmode/intelligence/list", params) }

                if (body.get("code")?.asInt != 200) {
                    DebugLog.e("Heartbeat API failed: ${body.get("message")?.asString}")
                    // Fallback: If playlistId was 0, maybe try without it or with a different param?
                    // Actually, if it failed with 400 "歌单不存在", it means pid was definitely wrong.
                }

                val songsJson = when {
                    body.get("data")?.isJsonArray == true -> body.get("data").asJsonArray
                    body.get("data")?.isJsonObject == true && body.get("data").asJsonObject.has("data") -> body.get("data").asJsonObject.get("data").asJsonArray
                    body.has("list") && body.get("list").isJsonArray -> body.get("list").asJsonArray
                    else -> null
                }

                val songs = songsJson?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                if (songs.isNotEmpty()) {
                    playSong(songs[0], songs)
                } else {
                    DebugLog.toast(getApplication(), "Heartbeat mode: No songs returned")
                }
            } catch (e: Exception) {
                DebugLog.e("Heartbeat error: ${e.message}")
            } finally {
                isLoading = false
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
