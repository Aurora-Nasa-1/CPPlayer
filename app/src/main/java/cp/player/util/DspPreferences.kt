package cp.player.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DspPreferences {
    private const val PREFS_NAME = "cp_player_dsp_prefs"
    private val gson = Gson()
    private const val KEY_EQ_ENABLED = "flick_eq_enabled"
    private const val KEY_PEQ_BANDS = "flick_peq_bands"
    private const val KEY_FX_ENABLED = "flick_fx_enabled"
    private const val KEY_FX_SIZE = "flick_fx_size"
    private const val KEY_FX_MIX = "flick_fx_mix"
    private const val KEY_FX_WIDTH = "flick_fx_width"

    private const val KEY_EXO_EQ_ENABLED = "exo_eq_enabled"
    private const val KEY_EXO_VIRTUALIZER_ENABLED = "exo_virtualizer_enabled"
    private const val KEY_EXO_VIRTUALIZER_STRENGTH = "exo_virtualizer_strength"
    private const val KEY_EXO_EQ_GAINS = "exo_eq_gains"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getEqEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_EQ_ENABLED, false)
    fun setEqEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()

    fun getFxEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_FX_ENABLED, false)
    fun setFxEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_FX_ENABLED, enabled).apply()

    fun getFxSize(context: Context): Float = getPrefs(context).getFloat(KEY_FX_SIZE, 0.55f)
    fun setFxSize(context: Context, size: Float) = getPrefs(context).edit().putFloat(KEY_FX_SIZE, size).apply()

    fun getFxMix(context: Context): Float = getPrefs(context).getFloat(KEY_FX_MIX, 0.25f)
    fun setFxMix(context: Context, mix: Float) = getPrefs(context).edit().putFloat(KEY_FX_MIX, mix).apply()

    fun getFxWidth(context: Context): Float = getPrefs(context).getFloat(KEY_FX_WIDTH, 1.0f)
    fun setFxWidth(context: Context, width: Float) = getPrefs(context).edit().putFloat(KEY_FX_WIDTH, width).apply()

    fun getPeqBands(context: Context): List<PeqBand> {
        val json = getPrefs(context).getString(KEY_PEQ_BANDS, null) ?: return emptyList()
        val type = object : TypeToken<List<PeqBand>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setPeqBands(context: Context, bands: List<PeqBand>) {
        val json = gson.toJson(bands)
        getPrefs(context).edit().putString(KEY_PEQ_BANDS, json).apply()
    }

    fun getExoEqEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_EXO_EQ_ENABLED, false)
    fun setExoEqEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_EXO_EQ_ENABLED, enabled).apply()

    fun getExoVirtualizerEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_EXO_VIRTUALIZER_ENABLED, false)
    fun setExoVirtualizerEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_EXO_VIRTUALIZER_ENABLED, enabled).apply()

    fun getExoVirtualizerStrength(context: Context): Short = getPrefs(context).getInt(KEY_EXO_VIRTUALIZER_STRENGTH, 0).toShort()
    fun setExoVirtualizerStrength(context: Context, strength: Short) = getPrefs(context).edit().putInt(KEY_EXO_VIRTUALIZER_STRENGTH, strength.toInt()).apply()

    fun getExoEqGains(context: Context): Map<Short, Short> {
        val json = getPrefs(context).getString(KEY_EXO_EQ_GAINS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<Short, Short>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setExoEqGains(context: Context, gains: Map<Short, Short>) {
        val json = gson.toJson(gains)
        getPrefs(context).edit().putString(KEY_EXO_EQ_GAINS, json).apply()
    }
}
