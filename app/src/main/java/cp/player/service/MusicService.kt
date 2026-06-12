package cp.player.service

import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.app.PendingIntent
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import androidx.media3.common.Rating
import androidx.media3.common.HeartRating
import android.content.Context
import java.io.File
import cp.player.util.UserPreferences
import cp.player.provider.ProviderManager
import cp.player.util.DebugLog
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private val players = arrayOfNulls<ExoPlayer>(2)
    private var activePlayerIndex = 0
    private var mediaSession: MediaSession? = null

    private val activePlayer get() = players[activePlayerIndex]
    private val nextPlayer get() = players[(activePlayerIndex + 1) % 2]

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isCrossfading = false
    private var crossfadeJob: Job? = null

    private fun abortCrossfade() {
        if (!isCrossfading) return
        crossfadeJob?.cancel()
        if (UserPreferences.getFadeMode(this@MusicService) == 0) {
            val old = nextPlayer ?: return
            old.pause()
            old.volume = 1.0f
            old.clearMediaItems()
        }
        activePlayer?.volume = 1.0f
        isCrossfading = false
    }

    private fun startSinglePlayerFadeOut(fadeDurationMs: Long) {
        isCrossfading = true
        val p = activePlayer ?: return
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            val steps = 20
            val delayTime = fadeDurationMs / steps
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                p.volume = 1.0f - progress
                delay(delayTime)
            }
            isCrossfading = false
        }
    }

    private fun startSinglePlayerFadeIn(fadeDurationMs: Long) {
        val p = activePlayer ?: return
        p.volume = 0f
        isCrossfading = true
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            val steps = 20
            val delayTime = fadeDurationMs / steps
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                p.volume = progress
                delay(delayTime)
            }
            p.volume = 1.0f
            isCrossfading = false
        }
    }

    private fun createForwardingPlayer(player: Player): ForwardingPlayer {
        return object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                abortCrossfade()
                super.seekToPreviousMediaItem()
            }
            override fun seekToNext() {
                abortCrossfade()
                super.seekToNext()
            }
            override fun seekToPreviousMediaItem() {
                abortCrossfade()
                super.seekToPreviousMediaItem()
            }
            override fun seekToNextMediaItem() {
                abortCrossfade()
                super.seekToNextMediaItem()
            }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                abortCrossfade()
                super.seekTo(mediaItemIndex, positionMs)
            }
            override fun seekToDefaultPosition() {
                abortCrossfade()
                super.seekToDefaultPosition()
            }
            override fun seekToDefaultPosition(mediaItemIndex: Int) {
                abortCrossfade()
                super.seekToDefaultPosition(mediaItemIndex)
            }
        }
    }

    companion object {
        private var cache: SimpleCache? = null
        fun getCache(context: Context): SimpleCache {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "media")
                val cacheSize = UserPreferences.getCacheSize(context) * 1024L * 1024L
                val evictor = LeastRecentlyUsedCacheEvictor(cacheSize)
                cache = SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
            }
            return cache!!
        }
    }
    private var lyriconProvider: LyriconProvider? = null
    private var lyricJob: Job? = null
    private var playbackInfoJob: Job? = null
    private val likedSongIds = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (UserPreferences.getPauseOnNoisy(this@MusicService)) {
                    val wasPlaying = activePlayer?.isPlaying == true
                    players.forEach { it?.pause() }
                    
                    if (wasPlaying && UserPreferences.getAutoResumeUsbAudio(this@MusicService)) {
                        wasPlayingBeforeDeviceDisconnect = true
                        deviceReconnectJob?.cancel()
                        deviceReconnectJob = serviceScope.launch {
                            delay(2000)
                            wasPlayingBeforeDeviceDisconnect = false
                        }
                    }
                }
            }
        }
    }

    private var wasPlayingBeforeFocusLoss = false
    private var wasPlayingBeforeDeviceDisconnect = false
    private var deviceReconnectJob: Job? = null

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            if (UserPreferences.getAutoResumeUsbAudio(this@MusicService) && wasPlayingBeforeDeviceDisconnect) {
                val isUsbOrHeadset = addedDevices?.any { 
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || 
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                } == true
                if (isUsbOrHeadset) {
                    activePlayer?.play()
                    wasPlayingBeforeDeviceDisconnect = false
                    deviceReconnectJob?.cancel()
                }
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                players.forEach { it?.pause() }
                wasPlayingBeforeFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val mode = UserPreferences.getAudioFocusMode(this@MusicService)
                if (mode == 1) { // 1 = Pause
                    wasPlayingBeforeFocusLoss = activePlayer?.isPlaying == true || wasPlayingBeforeFocusLoss
                    players.forEach { it?.pause() }
                } else if (mode == 0) { // Duck
                    players.forEach { it?.volume = 0.2f }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (UserPreferences.getAllowDucking(this@MusicService)) {
                    players.forEach { it?.volume = 0.2f }
                } else {
                    wasPlayingBeforeFocusLoss = activePlayer?.isPlaying == true || wasPlayingBeforeFocusLoss
                    players.forEach { it?.pause() }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                players.forEach {
                    if (it == activePlayer && !isCrossfading) {
                        it?.volume = 1.0f
                    }
                }
                if (wasPlayingBeforeFocusLoss) {
                    activePlayer?.play()
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.i("MusicService: Service onCreate")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        }

        val renderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com"))
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)")

        val cpDataSourceFactory = BackendDataSource.Factory(this, httpDataSourceFactory)

        val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(getCache(this))
            .setUpstreamDataSourceFactory(cpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        for (i in 0..1) {
            val playerBuilder = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)

            if (UserPreferences.getAutoAudioFocus(this)) {
                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                playerBuilder.setAudioAttributes(audioAttributes, true)
                playerBuilder.setHandleAudioBecomingNoisy(true)
            }

            val player = playerBuilder.build()
                
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (player != activePlayer) return
                    if (playWhenReady) {
                        if (!UserPreferences.getAutoAudioFocus(this@MusicService)) {
                            if (!requestAudioFocus()) {
                                players.forEach { it?.pause() }
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player != activePlayer) return
                    updateMediaSessionLayout()
                    updateWidget()
                    lyriconProvider?.player?.setPlaybackState(isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (player != activePlayer) return
                    
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        if (UserPreferences.getFadeMode(this@MusicService) == 1) {
                            val fadeDur = (UserPreferences.getFadeDuration(this@MusicService) * 1000L).toLong()
                            startSinglePlayerFadeIn(fadeDur)
                        }
                    }

                    updateMediaSessionLayout()
                    updateWidget()
                    mediaItem?.let { updateLyriconSong(it) }
                }

                override fun onEvents(eventPlayer: Player, events: Player.Events) {
                    if (eventPlayer != activePlayer) return
                    if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                        updateMediaSessionLayout()
                        updateWidget()
                    }
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        val state = eventPlayer.playbackState
                        updateMediaSessionLayout()
                        updateWidget()
                        if (state == Player.STATE_READY) {
                            startPlaybackInfoLoop()
                        }
                    }
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (player != activePlayer) return
                    lyriconProvider?.player?.setPosition(newPosition.positionMs)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (player != activePlayer) return
                    val errorMsg = "Error ${error.errorCode}: ${error.errorCodeName}\n${error.message}"
                    DebugLog.e("MusicService: Player Error", error)

                    val args = android.os.Bundle().apply { putString("error", errorMsg) }
                    mediaSession?.broadcastCustomCommand(SessionCommand("ACTION_PLAYER_ERROR", android.os.Bundle.EMPTY), args)

                    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        activePlayer?.let {
                            val currentItem = it.currentMediaItem
                            if (currentItem != null) {
                                it.prepare()
                                it.play()
                            }
                        }
                    }
                }
            })
            players[i] = player
        }

        startPlaybackInfoLoop()

        val intent = Intent(this, cp.player.MainActivity::class.java).apply {
            action = "ACTION_SHOW_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, createForwardingPlayer(activePlayer!!))
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("ACTION_LIKE", android.os.Bundle.EMPTY))
                        .add(SessionCommand("UPDATE_PLAYBACK_INFO", android.os.Bundle.EMPTY))
                        .add(SessionCommand("ACTION_PLAYER_ERROR", android.os.Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(availableSessionCommands, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                }

                override fun onSetRating(session: MediaSession, controller: MediaSession.ControllerInfo, rating: Rating): ListenableFuture<SessionResult> {
                    if (rating is HeartRating) {
                        val mediaId = session.player.currentMediaItem?.mediaId
                        if (mediaId != null) {
                            if (rating.isHeart) likedSongIds.add(mediaId) else likedSongIds.remove(mediaId)
                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                ProviderManager.callApi("like", mapOf("id" to mediaId, "like" to rating.isHeart.toString(), "cookie" to (cookie ?: "")))
                                withContext(Dispatchers.Main) { updateMediaSessionLayout() }
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: android.os.Bundle): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_LIKE") {
                        val currentMediaItem = session.player.currentMediaItem
                        val mediaId = currentMediaItem?.mediaId
                        if (mediaId != null) {
                            val isLiked = likedSongIds.contains(mediaId) || (currentMediaItem.mediaMetadata.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true)
                            val nextLikeState = !isLiked
                            if (nextLikeState) likedSongIds.add(mediaId) else likedSongIds.remove(mediaId)

                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                ProviderManager.callApi("like", mapOf("id" to mediaId, "like" to nextLikeState.toString(), "cookie" to (cookie ?: "")))
                                withContext(Dispatchers.Main) { updateMediaSessionLayout() }
                            }
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        initLyricon()
        updateMediaSessionLayout()
    }

    private fun initLyricon() {
        lyriconProvider = LyriconFactory.createProvider(this).apply {
            service.addConnectionListener(object : io.github.proify.lyricon.provider.ConnectionListener {
                override fun onConnected(provider: LyriconProvider) { syncToLyricon() }
                override fun onReconnected(provider: LyriconProvider) { syncToLyricon() }
                override fun onDisconnected(provider: LyriconProvider) {}
                override fun onConnectTimeout(provider: LyriconProvider) {}
            })
            register()
        }

        serviceScope.launch {
            while (true) {
                val p = activePlayer
                if (p != null && p.isPlaying) {
                    lyriconProvider?.player?.setPosition(p.currentPosition)
                    
                    if (!isCrossfading) {
                        val dur = p.duration
                        val pos = p.currentPosition
                        val fadeDur = (UserPreferences.getFadeDuration(this@MusicService) * 1000L).toLong()
                        val fadeMode = UserPreferences.getFadeMode(this@MusicService)
                        
                        if (dur != C.TIME_UNSET && fadeDur > 0 && dur - pos <= fadeDur && dur - pos > 0) {
                            if (p.currentMediaItemIndex < p.mediaItemCount - 1) {
                                if (fadeMode == 0) {
                                    startCrossfade(fadeDur)
                                } else if (fadeMode == 1) {
                                    startSinglePlayerFadeOut(fadeDur)
                                }
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private var outgoingReverb: android.media.audiofx.PresetReverb? = null
    private var outgoingEq: android.media.audiofx.Equalizer? = null

    private fun startCrossfade(fadeDurationMs: Long) {
        val oldPlayer = activePlayer ?: return
        val newPlayer = nextPlayer ?: return
        val targetIndex = oldPlayer.currentMediaItemIndex + 1
        
        if (targetIndex >= oldPlayer.mediaItemCount) return
        
        isCrossfading = true
        val items = (0 until oldPlayer.mediaItemCount).map { oldPlayer.getMediaItemAt(it) }
        
        activePlayerIndex = (activePlayerIndex + 1) % 2
        mediaSession?.player = createForwardingPlayer(newPlayer)

        newPlayer.volume = 0f
        newPlayer.setMediaItems(items, targetIndex, 0)
        newPlayer.repeatMode = oldPlayer.repeatMode
        newPlayer.shuffleModeEnabled = oldPlayer.shuffleModeEnabled
        newPlayer.prepare()
        newPlayer.play()
        
        oldPlayer.repeatMode = Player.REPEAT_MODE_OFF
        if (oldPlayer.mediaItemCount > targetIndex) {
            oldPlayer.removeMediaItems(targetIndex, oldPlayer.mediaItemCount)
        }
        
        crossfadeJob?.cancel()
        
        // Setup Live DJ effects for the outgoing track
        try {
            outgoingReverb?.release()
            outgoingEq?.release()
            
            val sessionId = oldPlayer.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                outgoingReverb = android.media.audiofx.PresetReverb(0, sessionId).apply {
                    preset = android.media.audiofx.PresetReverb.PRESET_LARGEHALL
                    enabled = true
                }
                outgoingEq = android.media.audiofx.Equalizer(0, sessionId).apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            cp.player.util.DebugLog.e("MusicService: Failed to setup audio FX", e)
        }

        crossfadeJob = serviceScope.launch {
            val steps = 30
            val delayTime = fadeDurationMs / steps
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                
                // Non-linear DJ mix curves
                val outVol = kotlin.math.max(0.0, Math.pow((1.0f - progress).toDouble(), 1.5)).toFloat()
                val inVol = kotlin.math.max(0.0, Math.pow(progress.toDouble(), 0.8)).toFloat()

                newPlayer.volume = inVol
                oldPlayer.volume = outVol
                
                // DJ High-pass filter effect on outgoing track (fade out the bass)
                try {
                    outgoingEq?.let { eq ->
                        val numBands = eq.numberOfBands
                        for (b in 0 until numBands) {
                            val centerFreq = eq.getCenterFreq(b.toShort())
                            if (centerFreq < 600_000) { // Frequencies below 600Hz
                                val minLevel = eq.bandLevelRange[0]
                                // Drop bass progressively
                                eq.setBandLevel(b.toShort(), (minLevel * progress).toInt().toShort())
                            } else if (centerFreq > 8_000_000) { // Frequencies above 8kHz
                                val minLevel = eq.bandLevelRange[0]
                                // Slight drop on highs to push track backwards
                                eq.setBandLevel(b.toShort(), (minLevel * (progress * 0.5f)).toInt().toShort())
                            }
                        }
                    }
                } catch (e: Exception) {}

                delay(delayTime)
            }
            oldPlayer.pause()
            oldPlayer.volume = 1.0f
            oldPlayer.clearMediaItems()
            
            try {
                outgoingReverb?.release()
                outgoingReverb = null
                outgoingEq?.release()
                outgoingEq = null
            } catch (e: Exception) {}

            isCrossfading = false
        }
    }

    private fun syncToLyricon() {
        val p = activePlayer ?: return
        val provider = lyriconProvider ?: return

        provider.player.setPlaybackState(p.isPlaying)
        provider.player.setPosition(p.currentPosition)
        p.currentMediaItem?.let { updateLyriconSong(it) }
    }

    private fun startPlaybackInfoLoop() {
        if (playbackInfoJob?.isActive == true) return
        playbackInfoJob = serviceScope.launch {
            while (true) {
                val p = activePlayer
                if (p != null && p.playbackState == Player.STATE_READY) {
                    var activeFormat = p.audioFormat
                    if (activeFormat == null || activeFormat.sampleRate == -1) {
                        val currentTracks = p.currentTracks
                        for (group in currentTracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        activeFormat = group.getTrackFormat(i)
                                        break
                                    }
                                }
                            }
                            if (activeFormat != null && activeFormat.sampleRate != -1) break
                        }
                    }

                    if (activeFormat != null) {
                        val sampleRate = if (activeFormat.sampleRate != -1) activeFormat.sampleRate else 0
                        val bitrate = if (activeFormat.bitrate != -1) activeFormat.bitrate else 0

                        if (sampleRate > 0 || bitrate > 0) {
                            val extras = android.os.Bundle().apply {
                                putInt("sampleRate", sampleRate)
                                putInt("bitrate", bitrate)
                            }
                            mediaSession?.setSessionExtras(extras)
                            mediaSession?.broadcastCustomCommand(SessionCommand("UPDATE_PLAYBACK_INFO", android.os.Bundle.EMPTY), extras)
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    private fun updateLyriconSong(mediaItem: MediaItem) {
        val songId = mediaItem.mediaId
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "Unknown"

        lyriconProvider?.player?.setPlaybackState(activePlayer?.isPlaying ?: false)
        lyriconProvider?.player?.setSong(Song(id = songId, name = title, artist = artist))

        lyricJob?.cancel()
        lyricJob = serviceScope.launch {
            try {
                val result = ProviderManager.callApi("lyric/new", mapOf("id" to songId))
                val body = com.google.gson.JsonParser.parseString(result).asJsonObject

                if (body.has("lrc") || body.has("yrc")) {
                    val lrc = body.get("lrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                    val tlyric = body.get("tlyric")?.asJsonObject?.get("lyric")?.asString ?: ""
                    val yrc = body.get("yrc")?.asJsonObject?.get("lyric")?.asString ?: ""

                    val lyricLines = if (yrc.isNotEmpty()) {
                        cp.player.util.LyricUtils.parseYrc(yrc)
                    } else {
                        cp.player.util.LyricUtils.parseLrc(lrc, activePlayer?.duration ?: 0L)
                    }

                    val finalLines = if (tlyric.isNotEmpty()) {
                        val tlines = cp.player.util.LyricUtils.parseLrc(tlyric).associateBy { it.time }
                        lyricLines.map { line ->
                            val trans = tlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
                            if (trans != null) {
                                line.copy(translation = trans.value.text)
                            } else {
                                line
                            }
                        }
                    } else {
                        lyricLines
                    }

                    lyriconProvider?.player?.setSong(
                        Song(
                            id = songId,
                            name = title,
                            artist = artist,
                            duration = activePlayer?.duration?.coerceAtLeast(0L) ?: 0L,
                            lyrics = cp.player.util.LyricUtils.toRichLyricLines(finalLines)
                        )
                    )
                    lyriconProvider?.player?.setPosition(activePlayer?.currentPosition ?: 0L)
                    lyriconProvider?.player?.setDisplayTranslation(tlyric.isNotEmpty())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "ACTION_PREVIOUS" -> {
                    abortCrossfade()
                    activePlayer?.seekToPrevious()
                }
                "ACTION_NEXT" -> {
                    abortCrossfade()
                    activePlayer?.seekToNext()
                }
                "ACTION_TOGGLE_PLAY" -> activePlayer?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun updateWidget() {
        val p = activePlayer ?: return
        val item = p.currentMediaItem ?: return
        cp.player.widget.MusicWidgetProvider.updateAllWidgets(
            this,
            item.mediaMetadata.title?.toString(),
            item.mediaMetadata.artist?.toString(),
            item.mediaMetadata.artworkUri?.toString(),
            p.isPlaying
        )
    }

    private fun updateMediaSessionLayout() {
        val session = mediaSession ?: return
        val currentMediaItem = activePlayer?.currentMediaItem
        val mediaId = currentMediaItem?.mediaId
        val isLiked = (mediaId != null && likedSongIds.contains(mediaId)) || (currentMediaItem?.mediaMetadata?.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true)

        val likeCommand = SessionCommand("ACTION_LIKE", android.os.Bundle.EMPTY)
        val likeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setSessionCommand(likeCommand)
            .setDisplayName("Like")
            .setIconResId(if (isLiked) cp.player.R.drawable.ic_heart_filled else cp.player.R.drawable.ic_heart_outline)
            .setEnabled(true)
            .build()

        val customLayout = com.google.common.collect.ImmutableList.of(likeButton)
        session.setCustomLayout(customLayout)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = activePlayer ?: return
        if ((!p.isPlaying && p.playbackState != Player.STATE_BUFFERING) || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        unregisterReceiver(noisyReceiver)
        abandonAudioFocus()
        mediaSession?.run {
            players.forEach { it?.release() }
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
