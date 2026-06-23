package cp.player.engine

import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

object ExoAudioFxManager {
    private const val TAG = "ExoAudioFxManager"

    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null

    // For preserving settings between sessions
    var eqEnabled = false
    private var internalVirtualizerEnabled = false
    val eqGains = mutableMapOf<Short, Short>() // band -> millibels
    private var internalVirtualizerStrength: Short = 0

    fun init(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
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

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = internalVirtualizerEnabled
                if (internalVirtualizerStrength > 0) {
                    setStrength(internalVirtualizerStrength)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Virtualizer", e)
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        virtualizer?.release()
        virtualizer = null
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        eqEnabled = enabled
        equalizer?.enabled = enabled
    }

    fun getEqualizerEnabled(): Boolean = eqEnabled

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0

    fun getCenterFreq(band: Short): Int = equalizer?.getCenterFreq(band) ?: 0

    fun getBandLevelRange(): ShortArray = equalizer?.bandLevelRange ?: shortArrayOf(0, 0)

    fun setBandLevel(band: Short, level: Short) {
        eqGains[band] = level
        if (equalizer != null && band < (equalizer?.numberOfBands ?: 0)) {
            equalizer?.setBandLevel(band, level)
        }
    }

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: eqGains[band] ?: 0
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        internalVirtualizerEnabled = enabled
        virtualizer?.enabled = enabled
    }

    fun getVirtualizerEnabled(): Boolean = internalVirtualizerEnabled

    fun setVirtualizerStrength(strength: Short) {
        internalVirtualizerStrength = strength
        virtualizer?.setStrength(strength)
    }

    fun getVirtualizerStrength(): Short = internalVirtualizerStrength
}
