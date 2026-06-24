package cp.player.engine

import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

import android.content.Context
import cp.player.util.DspPreferences

object ExoAudioFxManager {
    private const val TAG = "ExoAudioFxManager"

    private data class SessionFx(
        val equalizer: Equalizer?,
        val virtualizer: Virtualizer?
    )

    private val activeSessions = mutableMapOf<Int, SessionFx>()

    // For preserving settings between sessions
    var eqEnabled = false
    private var internalVirtualizerEnabled = false
    val eqGains = mutableMapOf<Short, Short>() // band -> millibels
    private var internalVirtualizerStrength: Short = 0
    private var isInitializedFromPrefs = false

    fun initPrefs(context: Context) {
        if (isInitializedFromPrefs) return
        eqEnabled = DspPreferences.getExoEqEnabled(context)
        internalVirtualizerEnabled = DspPreferences.getExoVirtualizerEnabled(context)
        internalVirtualizerStrength = DspPreferences.getExoVirtualizerStrength(context)
        eqGains.clear()
        eqGains.putAll(DspPreferences.getExoEqGains(context))
        isInitializedFromPrefs = true
    }

    fun init(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (activeSessions.containsKey(audioSessionId)) return

        var eq: Equalizer? = null
        try {
            eq = Equalizer(0, audioSessionId).apply {
                enabled = eqEnabled
                for ((band, gain) in eqGains) {
                    if (band < numberOfBands) {
                        setBandLevel(band, gain)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Equalizer", e)
        }

        var virt: Virtualizer? = null
        try {
            virt = Virtualizer(0, audioSessionId).apply {
                enabled = internalVirtualizerEnabled
                if (internalVirtualizerStrength > 0) {
                    setStrength(internalVirtualizerStrength)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Virtualizer", e)
        }

        activeSessions[audioSessionId] = SessionFx(eq, virt)
    }

    fun release(audioSessionId: Int) {
        activeSessions.remove(audioSessionId)?.let { fx ->
            fx.equalizer?.release()
            fx.virtualizer?.release()
        }
    }

    fun releaseAll() {
        activeSessions.values.forEach { fx ->
            fx.equalizer?.release()
            fx.virtualizer?.release()
        }
        activeSessions.clear()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        eqEnabled = enabled
        activeSessions.values.forEach { fx ->
            fx.equalizer?.enabled = enabled
        }
    }

    fun getEqualizerEnabled(): Boolean = eqEnabled

    fun getNumberOfBands(): Short {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.numberOfBands ?: 0
    }

    fun getCenterFreq(band: Short): Int {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.getCenterFreq(band) ?: 0
    }

    fun getBandLevelRange(): ShortArray {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.bandLevelRange ?: shortArrayOf(0, 0)
    }

    fun setBandLevel(band: Short, level: Short) {
        eqGains[band] = level
        activeSessions.values.forEach { fx ->
            if (fx.equalizer != null && band < fx.equalizer.numberOfBands) {
                fx.equalizer.setBandLevel(band, level)
            }
        }
    }

    fun getBandLevel(band: Short): Short {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.getBandLevel(band) ?: eqGains[band] ?: 0
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        internalVirtualizerEnabled = enabled
        activeSessions.values.forEach { fx ->
            fx.virtualizer?.enabled = enabled
        }
    }

    fun getVirtualizerEnabled(): Boolean = internalVirtualizerEnabled

    fun setVirtualizerStrength(strength: Short) {
        internalVirtualizerStrength = strength
        activeSessions.values.forEach { fx ->
            fx.virtualizer?.setStrength(strength)
        }
    }

    fun getVirtualizerStrength(): Short = internalVirtualizerStrength
}
