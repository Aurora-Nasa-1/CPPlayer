package cp.player.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.provider.BackendProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for persisting and syncing custom settings for each backend provider.
 */
object ProviderSettingsManager {
    private const val PREFS_NAME = "cp_provider_settings_prefs"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets the saved settings for a specific provider as a Map.
     */
    fun getSettings(context: Context, providerId: String): Map<String, Any> {
        val jsonStr = getPrefs(context).getString(providerId, null)
        if (jsonStr.isNullOrEmpty()) {
            return emptyMap()
        }
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(jsonStr, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Saves the settings for a specific provider and optionally syncs them to the backend.
     */
    suspend fun saveSettings(context: Context, provider: BackendProvider, newSettings: Map<String, Any>) {
        // Persist locally
        val jsonStr = gson.toJson(newSettings)
        getPrefs(context).edit().putString(provider.id, jsonStr).apply()

        // Sync to backend via API
        syncSettingsToBackend(provider, jsonStr)
    }

    /**
     * Syncs the JSON string of settings to the backend via settings/save
     */
    suspend fun syncSettingsToBackend(provider: BackendProvider, settingsJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = mapOf("settings" to settingsJson)
            val response = provider.callApi("settings/save", params)
            // Just assume it succeeded if it didn't throw an exception,
            // or we could check the code in response.
            response.contains("\"code\": 200") || response.contains("\"code\":200")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Utility function to call on app start or provider switch to ensure backend has the latest settings.
     */
    suspend fun syncLocalSettingsOnStart(context: Context, provider: BackendProvider?) {
        if (provider == null) return
        val settingsMap = getSettings(context, provider.id)
        if (settingsMap.isNotEmpty()) {
            syncSettingsToBackend(provider, gson.toJson(settingsMap))
        }
    }
}
