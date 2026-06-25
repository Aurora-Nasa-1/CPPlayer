package cp.player.engine

import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

import android.content.Context
import cp.player.util.DspPreferences

object ExoAudioFxManager {
    private const val TAG = "ExoAudioFxManager"

    private data class SessionFx(
        val equalizer: Equalizer?,
        val virtualizer: Virtualizer?,
        val bassBoost: BassBoost?,
        val loudnessEnhancer: LoudnessEnhancer?
    )

    private val activeSessions = mutableMapOf<Int, SessionFx>()

    // For preserving settings between sessions
    var eqEnabled = false
    var currentPreset: Short = -1
    private var internalVirtualizerEnabled = false
    val eqGains = mutableMapOf<Short, Short>() // band -> millibels
    private var internalVirtualizerStrength: Short = 0
    private var internalBassBoostEnabled = false
    private var internalBassBoostStrength: Short = 0
    private var internalLoudnessEnabled = false
    private var internalLoudnessGain: Int = 0
    var preampEnabled = false
    var preampGain = 0f // in dB
    private var isInitializedFromPrefs = false

    fun initPrefs(context: Context) {
        if (isInitializedFromPrefs) return
        eqEnabled = DspPreferences.getExoEqEnabled(context)
        currentPreset = DspPreferences.getExoPreset(context)
        internalVirtualizerEnabled = DspPreferences.getExoVirtualizerEnabled(context)
        internalVirtualizerStrength = DspPreferences.getExoVirtualizerStrength(context)
        internalBassBoostEnabled = DspPreferences.getExoBassBoostEnabled(context)
        internalBassBoostStrength = DspPreferences.getExoBassBoostStrength(context)
        internalLoudnessEnabled = DspPreferences.getExoLoudnessEnabled(context)
        internalLoudnessGain = DspPreferences.getExoLoudnessGain(context)
        eqGains.clear()
        eqGains.putAll(DspPreferences.getExoEqGains(context))
        // Load preamplifier settings
        preampEnabled = DspPreferences.getPreamplifierEnabled(context)
        preampGain = DspPreferences.getPreamplifierGain(context)
        isInitializedFromPrefs = true
    }

    fun init(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (activeSessions.containsKey(audioSessionId)) return

        var eq: Equalizer? = null
        try {
            eq = Equalizer(0, audioSessionId).apply {
                enabled = eqEnabled
                if (currentPreset >= 0 && currentPreset < numberOfPresets) {
                    usePreset(currentPreset)
                } else {
                    for ((band, gain) in eqGains) {
                        if (band < numberOfBands) {
                            setBandLevel(band, gain)
                        }
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

        var bassBoost: BassBoost? = null
        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = internalBassBoostEnabled
                if (internalBassBoostStrength > 0 && strengthSupported) {
                    setStrength(internalBassBoostStrength)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init BassBoost", e)
        }

        var loudness: LoudnessEnhancer? = null
        try {
            loudness = LoudnessEnhancer(audioSessionId).apply {
                enabled = internalLoudnessEnabled
                if (internalLoudnessGain != 0) {
                    setTargetGain(internalLoudnessGain)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init LoudnessEnhancer", e)
        }

        if (eq != null || virt != null || bassBoost != null || loudness != null) {
            activeSessions[audioSessionId] = SessionFx(eq, virt, bassBoost, loudness)
        }
    }

    fun release(audioSessionId: Int) {
        activeSessions.remove(audioSessionId)?.let { fx ->
            fx.equalizer?.release()
            fx.virtualizer?.release()
            fx.bassBoost?.release()
            fx.loudnessEnhancer?.release()
        }
    }

    fun releaseAll() {
        activeSessions.values.forEach { fx ->
            fx.equalizer?.release()
            fx.virtualizer?.release()
            fx.bassBoost?.release()
            fx.loudnessEnhancer?.release()
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
        currentPreset = -1 // User customized
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

    fun getNumberOfPresets(): Short {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.numberOfPresets ?: 0
    }

    fun getPresetName(preset: Short): String {
        return activeSessions.values.firstNotNullOfOrNull { it.equalizer }?.getPresetName(preset) ?: ""
    }

    fun usePreset(preset: Short) {
        currentPreset = preset
        activeSessions.values.forEach { fx ->
            fx.equalizer?.usePreset(preset)
            // Need to sync eqGains
            if (fx.equalizer != null) {
                for (band in 0 until fx.equalizer.numberOfBands) {
                    eqGains[band.toShort()] = fx.equalizer.getBandLevel(band.toShort())
                }
            }
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

    fun setBassBoostEnabled(enabled: Boolean) {
        internalBassBoostEnabled = enabled
        activeSessions.values.forEach { fx ->
            fx.bassBoost?.enabled = enabled
        }
    }

    fun getBassBoostEnabled(): Boolean = internalBassBoostEnabled

    fun setBassBoostStrength(strength: Short) {
        internalBassBoostStrength = strength
        activeSessions.values.forEach { fx ->
            if (fx.bassBoost?.strengthSupported == true) {
                fx.bassBoost.setStrength(strength)
            }
        }
    }

    fun getBassBoostStrength(): Short = internalBassBoostStrength

    fun setLoudnessEnabled(enabled: Boolean) {
        internalLoudnessEnabled = enabled
        activeSessions.values.forEach { fx ->
            fx.loudnessEnhancer?.enabled = enabled
        }
    }

    fun getLoudnessEnabled(): Boolean = internalLoudnessEnabled

    fun setLoudnessGain(gain: Int) {
        internalLoudnessGain = gain
        activeSessions.values.forEach { fx ->
            fx.loudnessEnhancer?.setTargetGain(gain)
        }
    }

    fun getLoudnessGain(): Int = internalLoudnessGain

    fun setPreamplifierEnabled(enabled: Boolean) {
        preampEnabled = enabled
        // Preamplifier is applied through volume control or EQ gains
    }

    fun setPreamplifierGain(gain: Float) {
        preampGain = gain
        // Preamplifier is applied through volume control or EQ gains
    }

    fun getPreamplifierEnabled(): Boolean = preampEnabled

    fun getPreamplifierGain(): Float = preampGain
}
