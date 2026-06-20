package cp.player.engine

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cp.player.util.DebugLog
import cp.player.util.UserPreferences
import kotlinx.coroutines.*

/**
 * 交叉淡入淡出管理器。
 *
 * 注意：FlickPlayer 是单实例引擎（共享 RustEngine 单例），不支持双播放器交叉淡入淡出。
 * 当引擎为 FlickPlayer 时，所有淡入淡出操作均跳过，曲目自然切换。
 */
class CrossfadeManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPlayerSwitched: (Player) -> Unit
) {
    val players = arrayOfNulls<Player>(2)
    var activePlayerIndex = 0
    var isCrossfading = false
    private var crossfadeJob: Job? = null

    private var outgoingReverb: android.media.audiofx.PresetReverb? = null
    private var outgoingEq: android.media.audiofx.Equalizer? = null

    val activePlayer: Player? get() = players[activePlayerIndex]
    val nextPlayer: Player? get() = players[(activePlayerIndex + 1) % 2]

    /** 当前是否为 FlickPlayer 引擎 */
    private val isFlickEngine: Boolean
        get() = activePlayer is FlickPlayer

    fun initPlayers(engineType: Int, listener: Player.Listener) {
        for (i in 0..1) {
            val player = CPPlayerManager.createPlayer(context, engineType)
            player.addListener(listener)
            players[i] = player
        }
    }

    fun switchEngine(engineType: Int, listener: Player.Listener) {
        abortCrossfade()
        val oldActivePlayer = activePlayer
        val currentMediaItem = oldActivePlayer?.currentMediaItem
        val currentPosition = oldActivePlayer?.currentPosition ?: 0L
        val isPlaying = oldActivePlayer?.isPlaying == true

        players.forEach { it?.release() }

        for (i in 0..1) {
            val player = CPPlayerManager.createPlayer(context, engineType)
            player.addListener(listener)
            players[i] = player
        }

        val newActivePlayer = activePlayer
        if (newActivePlayer != null && currentMediaItem != null) {
            newActivePlayer.setMediaItem(currentMediaItem)
            newActivePlayer.seekTo(currentPosition)
            newActivePlayer.prepare()
            if (isPlaying) {
                newActivePlayer.play()
            }
            onPlayerSwitched(newActivePlayer)
        }
    }

    fun release() {
        abortCrossfade()
        players.forEach { it?.release() }
        players.fill(null)
    }

    fun abortCrossfade() {
        if (!isCrossfading) return
        crossfadeJob?.cancel()
        if (!isFlickEngine && UserPreferences.getFadeMode(context) == 0) {
            val old = nextPlayer ?: return
            old.pause()
            old.volume = 1.0f
            old.clearMediaItems()
        }
        activePlayer?.volume = 1.0f
        isCrossfading = false
        releaseAudioFx()
    }

    /**
     * 单播放器淡出（fadeMode == 1）。
     * FlickPlayer 完全跳过 — 单实例引擎无法独立控制淡出音量。
     */
    fun startSinglePlayerFadeOut(fadeDurationMs: Long) {
        if (isFlickEngine) return
        val p = activePlayer ?: return
        isCrossfading = true
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
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

    /**
     * 单播放器淡入（fadeMode == 1）。
     * FlickPlayer 完全跳过。
     */
    fun startSinglePlayerFadeIn(fadeDurationMs: Long) {
        if (isFlickEngine) return
        val p = activePlayer ?: return
        val startVol = p.volume
        isCrossfading = true
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val steps = 20
            val delayTime = fadeDurationMs / steps
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                p.volume = startVol + (1.0f - startVol) * progress
                delay(delayTime)
            }
            p.volume = 1.0f
            isCrossfading = false
        }
    }

    /**
     * 双播放器交叉淡入淡出（fadeMode == 0）。
     * FlickPlayer 完全跳过 — 共享 RustEngine 单例无法同时播放两个流。
     */
    fun startCrossfade(fadeDurationMs: Long) {
        if (isFlickEngine) return
        val oldPlayer = activePlayer ?: return
        val newPlayer = nextPlayer ?: return
        val targetIndex = oldPlayer.currentMediaItemIndex + 1

        if (targetIndex >= oldPlayer.mediaItemCount) return

        isCrossfading = true
        val items = (0 until oldPlayer.mediaItemCount).map { oldPlayer.getMediaItemAt(it) }

        activePlayerIndex = (activePlayerIndex + 1) % 2
        onPlayerSwitched(newPlayer)

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
        setupAudioFx(oldPlayer)

        crossfadeJob = scope.launch {
            val steps = 30
            val delayTime = fadeDurationMs / steps
            for (i in 1..steps) {
                val progress = i.toFloat() / steps

                val outVol = kotlin.math.max(0.0, Math.pow((1.0f - progress).toDouble(), 1.5)).toFloat()
                val inVol = kotlin.math.max(0.0, Math.pow(progress.toDouble(), 0.8)).toFloat()

                newPlayer.volume = inVol
                oldPlayer.volume = outVol

                applyAudioFxFade(progress)
                delay(delayTime)
            }
            oldPlayer.pause()
            oldPlayer.volume = 1.0f
            oldPlayer.clearMediaItems()

            releaseAudioFx()
            isCrossfading = false
        }
    }

    private fun setupAudioFx(player: Player) {
        try {
            releaseAudioFx()
            val exoPlayer = player as? ExoPlayer ?: return
            val sessionId = exoPlayer.audioSessionId
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
            DebugLog.e("Failed to setup audio FX", e)
        }
    }

    private fun applyAudioFxFade(progress: Float) {
        try {
            outgoingEq?.let { eq ->
                val numBands = eq.numberOfBands
                for (b in 0 until numBands) {
                    val centerFreq = eq.getCenterFreq(b.toShort())
                    if (centerFreq < 600_000) {
                        val minLevel = eq.bandLevelRange[0]
                        eq.setBandLevel(b.toShort(), (minLevel * progress).toInt().toShort())
                    } else if (centerFreq > 8_000_000) {
                        val minLevel = eq.bandLevelRange[0]
                        eq.setBandLevel(b.toShort(), (minLevel * (progress * 0.5f)).toInt().toShort())
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun releaseAudioFx() {
        try {
            outgoingReverb?.release()
            outgoingReverb = null
            outgoingEq?.release()
            outgoingEq = null
        } catch (e: Exception) {}
    }
}
