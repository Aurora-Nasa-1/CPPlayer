package cp.player.service

import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
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
import cp.player.engine.CPPlayerManager
import cp.player.engine.CrossfadeManager
import cp.player.engine.UsbAudioManager
import cp.player.monitor.HealthMonitor
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences
import cp.player.api.MusicApiServiceFactory
import cp.player.util.DebugLog
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.lyric.model.Song
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val players = arrayOfNulls<Player>(2)
    private var activePlayerIndex = 0
    private var mediaSession: MediaSession? = null
    private lateinit var crossfadeManager: CrossfadeManager

    private val activePlayer get() = players[activePlayerIndex]
    private val nextPlayer get() = players[(activePlayerIndex + 1) % 2]

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    private var usbAudioManager: cp.player.engine.UsbAudioManager? = null

    private fun createForwardingPlayer(player: Player): ForwardingPlayer {
        return object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                crossfadeManager.abortCrossfade()
                super.seekToPreviousMediaItem()
            }
            override fun seekToNext() {
                crossfadeManager.abortCrossfade()
                super.seekToNext()
            }
            override fun seekToPreviousMediaItem() {
                crossfadeManager.abortCrossfade()
                super.seekToPreviousMediaItem()
            }
            override fun seekToNextMediaItem() {
                crossfadeManager.abortCrossfade()
                super.seekToNextMediaItem()
            }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                crossfadeManager.abortCrossfade()
                super.seekTo(mediaItemIndex, positionMs)
            }
            override fun seekToDefaultPosition() {
                crossfadeManager.abortCrossfade()
                super.seekToDefaultPosition()
            }
            override fun seekToDefaultPosition(mediaItemIndex: Int) {
                crossfadeManager.abortCrossfade()
                super.seekToDefaultPosition(mediaItemIndex)
            }
        }
    }

    companion object {
        /** LiveSort per-song 淡入淡出覆盖。key = song.id, value = (fadeInSec, fadeOutSec) */
        @Volatile
        var livesortFadeOverrides: Map<String, Pair<Float, Float>> = emptyMap()

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

        /**
         * 清除音频流磁盘缓存。
         */
        fun clearAudioCache(context: Context) {
            try {
                getCache(context).release()
                cache = null
                val cacheDir = File(context.cacheDir, "media")
                cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
    private var lyriconProvider: LyriconProvider? = null
    private var lyricJob: Job? = null
    private var playbackInfoJob: Job? = null

    // SuperLyric state
    private var superLyricReady = false
    private var currentSuperLyricLines: List<io.github.proify.lyricon.lyric.model.RichLyricLine>? = null
    private var currentSuperLyricIndex: Int = -1
    private var currentSuperLyricTitle: String = ""
    private var currentSuperLyricArtist: String = ""
    private var currentSuperLyricAlbum: String = ""
    private val likedSongIds = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 听歌打卡：追踪当前歌曲的播放开始时间
    private var scrobbleSongId: String? = null
    private var scrobbleStartTime: Long = 0L

    // Live engine switch listener
    private val enginePrefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "audio_engine") {
            val newEngine = prefs.getInt("audio_engine", 0)
            DebugLog.i("MusicService: Engine preference changed to $newEngine, switching...")
            serviceScope.launch(Dispatchers.Main) {
                switchEngine(newEngine)
            }
        }
    }

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
        if (usbAudioManager?.isDeviceRegistered == true && UserPreferences.getUsbExclusive(this@MusicService)) {
            // Ignore focus loss in USB Exclusive Mode
            return@OnAudioFocusChangeListener
        }

        // ExoPlayer 自动焦点模式下由原生处理，跳过手动逻辑
        // Flick 引擎始终需要手动处理音频焦点
        val isFlickEngine = UserPreferences.getAudioEngine(this@MusicService) == 1
        if (!isFlickEngine && UserPreferences.getAutoAudioFocus(this@MusicService)) {
            return@OnAudioFocusChangeListener
        }

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                val mode = UserPreferences.getAudioFocusMode(this@MusicService)
                if (mode == 2) { // 2 = Continue playing, ignore focus loss
                    // 不暂停、不降低音量，保持播放
                } else {
                    players.forEach { it?.pause() }
                    wasPlayingBeforeFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val mode = UserPreferences.getAudioFocusMode(this@MusicService)
                if (mode == 2) { // 2 = Continue playing, ignore focus loss
                    // 不暂停、不降低音量，保持播放
                } else if (mode == 1) { // 1 = Pause
                    wasPlayingBeforeFocusLoss = activePlayer?.isPlaying == true || wasPlayingBeforeFocusLoss
                    players.forEach { it?.pause() }
                } else if (mode == 0) { // Duck
                    players.forEach { it?.volume = 0.2f }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val mode = UserPreferences.getAudioFocusMode(this@MusicService)
                if (mode == 2) { // 2 = Continue playing, ignore focus loss
                    // 不暂停、不降低音量，保持播放
                } else if (UserPreferences.getAllowDucking(this@MusicService)) {
                    players.forEach { it?.volume = 0.2f }
                } else {
                    wasPlayingBeforeFocusLoss = activePlayer?.isPlaying == true || wasPlayingBeforeFocusLoss
                    players.forEach { it?.pause() }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                players.forEach {
                    if (it == activePlayer && !crossfadeManager.isCrossfading) {
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

        try {
            initSuperLyric()
        } catch (e: Throwable) {
            DebugLog.w("MusicService: initSuperLyric outer catch: ${e.message}")
        }

        usbAudioManager = UsbAudioManager(this)
        usbAudioManager?.start()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        }

        for (i in 0..1) {
            val engineType = UserPreferences.getAudioEngine(this)
            DebugLog.i("MusicService: Initializing player index $i with engineType: $engineType")
            val player: Player = CPPlayerManager.createPlayer(this, engineType)
                
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (player != activePlayer) return
                    if (playWhenReady) {
                        val autoFocus = UserPreferences.getAutoAudioFocus(this@MusicService)
                        val isFlick = UserPreferences.getAudioEngine(this@MusicService) == 1
                        // Flick 引擎无原生焦点管理，始终需要手动请求
                        // ExoPlayer 自动焦点模式下由原生处理
                        if (!autoFocus || isFlick) {
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
                    if (!isPlaying && superLyricReady) {
                        SuperLyricHelper.sendStop(SuperLyricData())
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    DebugLog.i("MusicService: onMediaItemTransition id=${mediaItem?.mediaId} reason=$reason player==$activePlayer?${player == activePlayer}")
                    if (player != activePlayer) return

                    // 听歌打卡：上报上一首歌的播放数据
                    val prevSongId = scrobbleSongId
                    if (prevSongId != null && scrobbleStartTime > 0) {
                        val playedSeconds = ((System.currentTimeMillis() - scrobbleStartTime) / 1000).toInt()
                        if (playedSeconds > 0) {
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    MusicApiServiceFactory.instance.scrobble(prevSongId, "", playedSeconds)
                                    DebugLog.i("MusicService: scrobble reported id=$prevSongId time=${playedSeconds}s")
                                } catch (e: Exception) {
                                    DebugLog.w("MusicService: scrobble failed: ${e.message}")
                                }
                            }
                        }
                    }
                    // 记录新歌曲的打卡信息
                    scrobbleSongId = mediaItem?.mediaId
                    scrobbleStartTime = System.currentTimeMillis()

                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        // LiveSort per-song 淡入覆盖
                        val newSongId = mediaItem?.mediaId
                        val override = newSongId?.let { livesortFadeOverrides[it] }
                        val fadeInDur = override?.first?.takeIf { it > 0f }?.times(1000L)?.toLong()

                        if (fadeInDur != null) {
                            crossfadeManager.startSinglePlayerFadeIn(fadeInDur)
                        } else if (UserPreferences.getFadeMode(this@MusicService) == 1) {
                            val fadeDur = (UserPreferences.getFadeDuration(this@MusicService) * 1000L).toLong()
                            crossfadeManager.startSinglePlayerFadeIn(fadeDur)
                        }
                    }

                    updateMediaSessionLayout()
                    updateWidget()
                    mediaItem?.let {
                        updateLyriconSong(it)
                        // 通知 MediaController 歌曲切换（MediaController 可能收不到 onMediaItemTransition）
                        val extras = android.os.Bundle().apply {
                            putString("mediaId", it.mediaId)
                            putString("title", it.mediaMetadata.title?.toString() ?: "")
                            putString("artist", it.mediaMetadata.artist?.toString() ?: "")
                            putString("album", it.mediaMetadata.albumTitle?.toString() ?: "")
                            putString("artworkUri", it.mediaMetadata.artworkUri?.toString() ?: "")
                            putString("artistId", it.mediaMetadata.extras?.getString("artistId"))
                        }
                        mediaSession?.broadcastCustomCommand(SessionCommand("ACTION_SONG_CHANGED", android.os.Bundle.EMPTY), extras)
                    }
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
                    // 记录播放错误到健康监控
                    HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                        timestamp = System.currentTimeMillis(),
                        providerId = ProviderManager.getCurrentProviderId(),
                        method = "PLAYBACK_ERROR",
                        durationMs = 0,
                        success = false,
                        errorCode = error.errorCode,
                        errorMessage = errorMsg
                    ))

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

        crossfadeManager = CrossfadeManager(this, serviceScope) { newPlayer ->
            activePlayerIndex = crossfadeManager.activePlayerIndex
            mediaSession?.player = createForwardingPlayer(newPlayer)
        }
        for (i in 0..1) {
            crossfadeManager.players[i] = players[i]
        }
        crossfadeManager.activePlayerIndex = activePlayerIndex

        // 在 Rust 引擎初始化完成后处理 USB 设备
        if (UserPreferences.getAudioEngine(this) == 1) {
            // 优先使用 MainActivity 通过 Manifest intent-filter 暂存的设备
            val pendingDevice = cp.player.MainActivity.pendingUsbDevice
            if (pendingDevice != null) {
                cp.player.MainActivity.pendingUsbDevice = null
                DebugLog.i("MusicService: Found pending USB device from MainActivity: ${pendingDevice.productName}")
                usbAudioManager?.registerPendingDevice(pendingDevice)
            }
            // 启动周期性扫描作为兜底（覆盖设备枚举延迟、广播丢失等场景）
            usbAudioManager?.startPeriodicScan()
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
                        .add(SessionCommand("ACTION_SONG_CHANGED", android.os.Bundle.EMPTY))
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
                                MusicApiServiceFactory.instance.likeSong(mediaId, rating.isHeart)
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
                                MusicApiServiceFactory.instance.likeSong(mediaId, nextLikeState)
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

        // Listen for engine preference changes for live switching
        UserPreferences.getPrefs(this).registerOnSharedPreferenceChangeListener(enginePrefListener)
    }

    private fun switchEngine(engineType: Int) {
        val currentEngine = UserPreferences.getAudioEngine(this)
        if (engineType == currentEngine) return

        DebugLog.i("MusicService: switchEngine($engineType) — current=$currentEngine")
        val listener = players[activePlayerIndex]?.let { player ->
            // Extract the listener from the current player — we need to re-attach it to new players
            // Since we can't extract the listener, we create a fresh one
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) {
                        val autoFocus = UserPreferences.getAutoAudioFocus(this@MusicService)
                        val isFlick = UserPreferences.getAudioEngine(this@MusicService) == 1
                        if (!autoFocus || isFlick) {
                            if (!requestAudioFocus()) {
                                players.forEach { it?.pause() }
                            }
                        }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateMediaSessionLayout()
                    updateWidget()
                    lyriconProvider?.player?.setPlaybackState(isPlaying)
                    if (!isPlaying && superLyricReady) {
                        SuperLyricHelper.sendStop(SuperLyricData())
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        // LiveSort per-song 淡入覆盖
                        val newSongId = mediaItem?.mediaId
                        val override = newSongId?.let { livesortFadeOverrides[it] }
                        val fadeInDur = override?.first?.takeIf { it > 0f }?.times(1000L)?.toLong()

                        if (fadeInDur != null) {
                            crossfadeManager.startSinglePlayerFadeIn(fadeInDur)
                        } else if (UserPreferences.getFadeMode(this@MusicService) == 1) {
                            val fadeDur = (UserPreferences.getFadeDuration(this@MusicService) * 1000L).toLong()
                            crossfadeManager.startSinglePlayerFadeIn(fadeDur)
                        }
                    }
                    updateMediaSessionLayout()
                    updateWidget()
                    mediaItem?.let {
                        updateLyriconSong(it)
                        val extras = android.os.Bundle().apply {
                            putString("mediaId", it.mediaId)
                            putString("title", it.mediaMetadata.title?.toString() ?: "")
                            putString("artist", it.mediaMetadata.artist?.toString() ?: "")
                            putString("album", it.mediaMetadata.albumTitle?.toString() ?: "")
                            putString("artworkUri", it.mediaMetadata.artworkUri?.toString() ?: "")
                            putString("artistId", it.mediaMetadata.extras?.getString("artistId"))
                        }
                        mediaSession?.broadcastCustomCommand(SessionCommand("ACTION_SONG_CHANGED", android.os.Bundle.EMPTY), extras)
                    }
                }
                override fun onEvents(eventPlayer: Player, events: Player.Events) {
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
                    lyriconProvider?.player?.setPosition(newPosition.positionMs)
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val errorMsg = "Error ${error.errorCode}: ${error.errorCodeName}\n${error.message}"
                    DebugLog.e("MusicService: Player Error", error)
                    val args = android.os.Bundle().apply { putString("error", errorMsg) }
                    mediaSession?.broadcastCustomCommand(SessionCommand("ACTION_PLAYER_ERROR", android.os.Bundle.EMPTY), args)
                }
            }
        }

        if (listener != null) {
            crossfadeManager.switchEngine(engineType, listener)
            activePlayerIndex = crossfadeManager.activePlayerIndex
            mediaSession?.player = createForwardingPlayer(crossfadeManager.activePlayer!!)

            // Update USB audio manager
            if (engineType == 1) {
                if (usbAudioManager == null) {
                    usbAudioManager = cp.player.engine.UsbAudioManager(this)
                    usbAudioManager?.start()
                }
                // 引擎切换完成后启动周期性 USB 扫描
                usbAudioManager?.startPeriodicScan()
            } else {
                usbAudioManager?.stop()
                usbAudioManager = null
            }
        }
    }

    /**
     * 初始化 SuperLyric 发布者。
     * 独立方法 + @Keep 防止 R8 内联优化掉 try-catch。
     */
    @androidx.annotation.Keep
    private fun initSuperLyric() {
        try {
            SuperLyricHelper.registerPublisher()
            SuperLyricHelper.setSystemPlayStateListenerEnabled(false)
            superLyricReady = true
            DebugLog.i("MusicService: SuperLyricHelper initialized")
        } catch (e: Throwable) {
            DebugLog.w("MusicService: SuperLyricHelper init failed: ${e.message}")
        }
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
                    syncSuperLyric(p.currentPosition)
                    
                    if (!crossfadeManager.isCrossfading) {
                        val dur = p.duration
                        val pos = p.currentPosition
                        val globalFadeDur = (UserPreferences.getFadeDuration(this@MusicService) * 1000L).toLong()
                        val fadeMode = UserPreferences.getFadeMode(this@MusicService)

                        // LiveSort per-song 淡出覆盖
                        val songId = p.currentMediaItem?.mediaId
                        val override = songId?.let { livesortFadeOverrides[it] }
                        val effectiveFadeDur = override?.second?.takeIf { it > 0f }?.times(1000L)?.toLong()
                            ?: if (livesortFadeOverrides.isEmpty()) globalFadeDur else 0L

                        if (effectiveFadeDur > 0 && dur != C.TIME_UNSET && dur - pos <= effectiveFadeDur && dur - pos > 0) {
                            val rm = p.repeatMode
                            // 单曲循环不触发 crossfade — 由播放器原生处理重播
                            if (rm != Player.REPEAT_MODE_ONE) {
                                val hasNext = p.currentMediaItemIndex < p.mediaItemCount - 1
                                val canWrap = rm == Player.REPEAT_MODE_ALL || p.shuffleModeEnabled
                                if (hasNext || canWrap) {
                                    if (fadeMode == 0 || (override != null && fadeMode != 2)) {
                                        crossfadeManager.startCrossfade(effectiveFadeDur)
                                    } else if (fadeMode == 1) {
                                        crossfadeManager.startSinglePlayerFadeOut(effectiveFadeDur)
                                    }
                                }
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun syncSuperLyric(position: Long) {
        val lines = currentSuperLyricLines ?: return
        var newIndex = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (position >= line.begin && (line.end == 0L || position < line.end)) {
                newIndex = i
                break
            } else if (position < line.begin) {
                // Since lyrics are usually sorted, if we passed the position, the current line is likely the previous one
                newIndex = if (i > 0) i - 1 else 0
                break
            }
        }

        // If we didn't break, we might be past the last line
        if (newIndex == -1 && lines.isNotEmpty() && position >= lines.last().begin) {
            newIndex = lines.size - 1
        }

        if (superLyricReady && newIndex != -1 && newIndex != currentSuperLyricIndex) {
            currentSuperLyricIndex = newIndex
            val richLine = lines[newIndex]

            val superLyricWords = richLine.words?.map { w ->
                SuperLyricWord(w.text ?: "", w.begin, w.end)
            }?.toTypedArray()

            val mainLine = SuperLyricLine(
                richLine.text ?: "",
                superLyricWords,
                richLine.begin,
                richLine.end
            )

            val data = SuperLyricData()
                .setTitle(currentSuperLyricTitle)
                .setArtist(currentSuperLyricArtist)
                .setAlbum(currentSuperLyricAlbum)
                .setLyric(mainLine)

            if (!richLine.secondary.isNullOrEmpty()) {
                data.secondary = SuperLyricLine(richLine.secondary!!, richLine.begin, richLine.end)
            }

            if (!richLine.translation.isNullOrEmpty()) {
                data.translation = SuperLyricLine(richLine.translation!!, richLine.begin, richLine.end)
            }

            SuperLyricHelper.sendLyric(data)
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
                    var sampleRate = 0
                    var bitrate = 0
                    var bitDepth = 0
                    var channels = 0
                    var codecName = ""

                    // Try ExoPlayer's audioFormat first
                    var activeFormat = (p as? ExoPlayer)?.audioFormat
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

                    if (activeFormat != null && activeFormat.sampleRate != -1) {
                        sampleRate = activeFormat.sampleRate
                        bitrate = if (activeFormat.bitrate != -1) activeFormat.bitrate else 0
                        bitDepth = if (activeFormat.pcmEncoding != android.media.AudioFormat.ENCODING_INVALID) {
                            when (activeFormat.pcmEncoding) {
                                android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                                android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                                android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                                else -> 0
                            }
                        } else 0
                        channels = activeFormat.channelCount
                        codecName = activeFormat.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                    }

                    // Fallback: read format from FlickPlayer's FormatChanged events
                    val flickPlayer = p as? cp.player.engine.FlickPlayer
                    if (flickPlayer != null) {
                        val info = flickPlayer.getExtendedFormatInfo()
                        if (sampleRate == 0 && info.sampleRate > 0) sampleRate = info.sampleRate
                        if (bitrate == 0 && info.bitrate > 0) bitrate = info.bitrate
                        if (bitDepth == 0 && info.bitDepth > 0) bitDepth = info.bitDepth
                        if (channels == 0 && info.channels > 0) channels = info.channels
                        if (codecName.isEmpty() && info.codecName.isNotEmpty()) codecName = info.codecName
                    }

                    if (sampleRate > 0 || bitrate > 0 || bitDepth > 0 || channels > 0 || codecName.isNotEmpty()) {
                        val extras = android.os.Bundle().apply {
                            putInt("sampleRate", sampleRate)
                            putInt("bitrate", bitrate)
                            putInt("bitDepth", bitDepth)
                            putInt("channels", channels)
                            putString("codecName", codecName)
                        }
                        mediaSession?.setSessionExtras(extras)
                        mediaSession?.broadcastCustomCommand(SessionCommand("UPDATE_PLAYBACK_INFO", android.os.Bundle.EMPTY), extras)
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
        val album = metadata.albumTitle?.toString() ?: ""

        currentSuperLyricTitle = title
        currentSuperLyricArtist = artist
        currentSuperLyricAlbum = album
        currentSuperLyricLines = null
        currentSuperLyricIndex = -1

        // 先设置歌曲基本信息
        lyriconProvider?.player?.setPlaybackState(activePlayer?.isPlaying ?: false)
        lyriconProvider?.player?.setSong(Song(id = songId, name = title, artist = artist))

        // 通过 LyricsManager 统一获取歌词（传入标题/歌手用于本地歌曲自动搜索云端绑定）
        cp.player.lyrics.LyricsManager.fetch(songId, this, songTitle = title, songArtist = artist)

        // 观察歌词状态变化，同步到 Lyricon
        lyricJob?.cancel()
        lyricJob = serviceScope.launch {
            cp.player.lyrics.LyricsManager.state.collect { state ->
                if (state is cp.player.lyrics.LyricsState.Success && state.songId == songId) {
                    currentSuperLyricLines = state.richLyricLines
                    currentSuperLyricIndex = -1 // Force resync

                    lyriconProvider?.player?.setSong(
                        Song(
                            id = songId,
                            name = title,
                            artist = artist,
                            duration = activePlayer?.duration?.coerceAtLeast(0L) ?: 0L,
                            lyrics = state.richLyricLines
                        )
                    )
                    lyriconProvider?.player?.setPosition(activePlayer?.currentPosition ?: 0L)
                    lyriconProvider?.player?.setDisplayTranslation(state.lyricsInfo.hasTranslation)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "ACTION_PREVIOUS" -> {
                    crossfadeManager.abortCrossfade()
                    activePlayer?.seekToPrevious()
                }
                "ACTION_NEXT" -> {
                    crossfadeManager.abortCrossfade()
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
        // 无论播放状态，任务移除时先保存队列，防止进程被杀后数据丢失
        saveQueueOnExit()
        val p = activePlayer ?: return
        if ((!p.isPlaying && p.playbackState != Player.STATE_BUFFERING) || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /**
     * 保存当前播放队列到 SharedPreferences，供下次启动恢复。
     */
    private fun saveQueueOnExit() {
        if (!UserPreferences.getRestoreLastQueue(this)) return
        val player = activePlayer ?: return
        val count = player.mediaItemCount
        if (count == 0) return
        try {
            val songs = (0 until count).map { i ->
                val item = player.getMediaItemAt(i)
                val meta = item.mediaMetadata
                cp.player.model.Song(
                    id = item.mediaId,
                    name = meta.title?.toString() ?: "Unknown",
                    artist = meta.artist?.toString() ?: "Unknown",
                    artistId = meta.extras?.getString("artistId"),
                    album = meta.albumTitle?.toString() ?: "",
                    albumArtUrl = meta.artworkUri?.toString()
                )
            }
            val json = com.google.gson.Gson().toJson(songs)
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            UserPreferences.saveLastQueue(this, json, index, position)
            DebugLog.i("MusicService: Saved queue on exit: ${songs.size} songs, index=$index, pos=$position")
        } catch (e: Exception) {
            DebugLog.e("MusicService: Failed to save queue on exit: ${e.message}")
        }
    }

    override fun onDestroy() {
        // 保存当前播放队列以便下次恢复
        saveQueueOnExit()
        try {
            SuperLyricHelper.unregisterPublisher()
        } catch (_: Exception) {}
        serviceScope.cancel()
        UserPreferences.getPrefs(this).unregisterOnSharedPreferenceChangeListener(enginePrefListener)
        crossfadeManager.release()
        usbAudioManager?.stop()
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
