package cp.player.util

import android.content.Context
import android.content.SharedPreferences

object UserPreferences {
    private const val PREFS_NAME = "cp_player_prefs"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_QUALITY_WIFI = "quality_wifi"
    private const val KEY_QUALITY_CELLULAR = "quality_cellular"
    private const val KEY_FADE_DURATION = "fade_duration"
    private const val KEY_CACHE_SIZE = "cache_size"
    private const val KEY_USE_CELLULAR_CACHE = "use_cellular_cache"
    private const val KEY_DOWNLOAD_DIR = "download_dir"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_FIRST_DOWNLOAD = "first_download"
    private const val KEY_ALLOW_CELLULAR_DOWNLOAD = "allow_cellular_download"
    private const val KEY_PURE_BLACK_MODE = "pure_black_mode"
    private const val KEY_THEME_MODE = "theme_mode" // 0: System, 1: Follow Cover, 2: Fixed
    private const val KEY_FOLLOW_COVER_APP = "follow_cover_app"
    private const val KEY_FOLLOW_COVER_MINI = "follow_cover_mini"
    private const val KEY_FOLLOW_COVER_PLAYER = "follow_cover_player"
    private const val KEY_USE_FLUID_BACKGROUND = "use_fluid_background"
    private const val KEY_USE_WAVY_PROGRESS = "use_wavy_progress"
    private const val KEY_USER_PROFILE_CACHE = "user_profile_cache"
    private const val KEY_AUDIO_FEATURES_CACHE = "audio_features_cache"
    private const val KEY_AUDIO_FOCUS_MODE = "audio_focus_mode"
    private const val KEY_ALLOW_DUCKING = "allow_ducking"
    private const val KEY_PAUSE_ON_NOISY = "pause_on_noisy"
    private const val KEY_AUTO_RESUME_USB_AUDIO = "auto_resume_usb_audio"
    private const val KEY_FADE_MODE = "fade_mode" // 0: Crossfade, 1: Single Fade, 2: Off
    private const val KEY_AUTO_AUDIO_FOCUS = "auto_audio_focus"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun getProviderPrefix(): String {
        return cp.player.provider.ProviderManager.currentProvider?.id ?: "default"
    }

    fun saveCookie(context: Context, cookie: String) {
        getPrefs(context).edit().putString("${KEY_COOKIE}_${getProviderPrefix()}", cookie).apply()
    }

    fun getCookie(context: Context): String? {
        return getPrefs(context).getString("${KEY_COOKIE}_${getProviderPrefix()}", null)
    }

    data class SavedAccount(
        val uid: Long,
        val nickname: String,
        val avatarUrl: String,
        val cookie: String
    )

    fun saveAccount(context: Context, account: SavedAccount) {
        val currentList = getSavedAccounts(context).toMutableList()
        val index = currentList.indexOfFirst { it.uid == account.uid }
        if (index >= 0) {
            currentList[index] = account
        } else {
            currentList.add(account)
        }
        val json = com.google.gson.Gson().toJson(currentList)
        getPrefs(context).edit().putString("${KEY_SAVED_ACCOUNTS}_${getProviderPrefix()}", json).apply()
    }

    fun removeAccount(context: Context, uid: Long) {
        val currentList = getSavedAccounts(context).toMutableList()
        currentList.removeAll { it.uid == uid }
        val json = com.google.gson.Gson().toJson(currentList)
        getPrefs(context).edit().putString("${KEY_SAVED_ACCOUNTS}_${getProviderPrefix()}", json).apply()
    }

    fun getSavedAccounts(context: Context): List<SavedAccount> {
        val json = getPrefs(context).getString("${KEY_SAVED_ACCOUNTS}_${getProviderPrefix()}", null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<SavedAccount>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveQualityWifi(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_QUALITY_WIFI, quality).apply()
    }

    fun getQualityWifi(context: Context): String {
        return getPrefs(context).getString(KEY_QUALITY_WIFI, "exhigh") ?: "exhigh"
    }

    fun saveQualityCellular(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_QUALITY_CELLULAR, quality).apply()
    }

    fun getQualityCellular(context: Context): String {
        return getPrefs(context).getString(KEY_QUALITY_CELLULAR, "standard") ?: "standard"
    }

    fun saveFadeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_FADE_MODE, mode).apply()
    }

    fun getFadeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_FADE_MODE, 0)
    }

    fun saveAutoAudioFocus(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_AUDIO_FOCUS, enabled).apply()
    }

    fun getAutoAudioFocus(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_AUDIO_FOCUS, true)
    }

    fun saveFadeDuration(context: Context, duration: Float) {
        getPrefs(context).edit().putFloat(KEY_FADE_DURATION, duration).apply()
    }

    fun getFadeDuration(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FADE_DURATION, 2f)
    }

    fun saveCacheSize(context: Context, sizeMb: Int) {
        getPrefs(context).edit().putInt(KEY_CACHE_SIZE, sizeMb).apply()
    }

    fun getCacheSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_CACHE_SIZE, 512)
    }

    fun saveUseCellularCache(context: Context, use: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_CELLULAR_CACHE, use).apply()
    }

    fun getUseCellularCache(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_CELLULAR_CACHE, false)
    }

    fun saveDownloadDir(context: Context, uri: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_DIR, uri).apply()
    }

    fun getDownloadDir(context: Context): String? {
        return getPrefs(context).getString(KEY_DOWNLOAD_DIR, null)
    }

    fun saveDownloadQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    fun getDownloadQuality(context: Context): String {
        return getPrefs(context).getString(KEY_DOWNLOAD_QUALITY, "standard") ?: "standard"
    }

    fun isFirstDownload(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_DOWNLOAD, true)
    }

    fun setFirstDownloadComplete(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_DOWNLOAD, false).apply()
    }

    fun saveAllowCellularDownload(context: Context, allow: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, allow).apply()
    }

    fun getAllowCellularDownload(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, false)
    }

    fun savePureBlackMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PURE_BLACK_MODE, enabled).apply()
    }

    fun getPureBlackMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PURE_BLACK_MODE, false)
    }

    fun saveThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, 0)
    }

    fun saveFollowCoverApp(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLLOW_COVER_APP, enabled).apply()
    }

    fun getFollowCoverApp(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOLLOW_COVER_APP, false)
    }

    fun saveFollowCoverMini(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLLOW_COVER_MINI, enabled).apply()
    }

    fun getFollowCoverMini(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOLLOW_COVER_MINI, true)
    }

    fun saveFollowCoverPlayer(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLLOW_COVER_PLAYER, enabled).apply()
    }

    fun getFollowCoverPlayer(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOLLOW_COVER_PLAYER, true)
    }

    fun saveUseFluidBackground(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_FLUID_BACKGROUND, enabled).apply()
    }

    fun getUseFluidBackground(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_FLUID_BACKGROUND, false)
    }

    fun saveUseWavyProgress(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_WAVY_PROGRESS, enabled).apply()
    }

    fun getUseWavyProgress(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_WAVY_PROGRESS, true)
    }

    fun savePlaylistSort(context: Context, playlistId: Long, sortOrder: String) {
        getPrefs(context).edit().putString("sort_playlist_$playlistId", sortOrder).apply()
    }

    fun getPlaylistSort(context: Context, playlistId: Long): String {
        return getPrefs(context).getString("sort_playlist_$playlistId", "default") ?: "default"
    }

    fun savePlaylistCache(context: Context, playlistId: Long, json: String) {
        getPrefs(context).edit().putString("cache_playlist_${playlistId}_${getProviderPrefix()}", json).apply()
    }

    fun getPlaylistCache(context: Context, playlistId: Long): String? {
        return getPrefs(context).getString("cache_playlist_${playlistId}_${getProviderPrefix()}", null)
    }

    fun saveUserPlaylistsCache(context: Context, json: String) {
        getPrefs(context).edit().putString("cache_user_playlists_${getProviderPrefix()}", json).apply()
    }

    fun getUserPlaylistsCache(context: Context): String? {
        return getPrefs(context).getString("cache_user_playlists_${getProviderPrefix()}", null)
    }

    fun saveRecommendedSongsCache(context: Context, json: String) {
        getPrefs(context).edit().putString("cache_recommended_songs_${getProviderPrefix()}", json).apply()
    }

    fun getRecommendedSongsCache(context: Context): String? {
        return getPrefs(context).getString("cache_recommended_songs_${getProviderPrefix()}", null)
    }

    fun saveSearchHistory(context: Context, history: List<String>) {
        val json = com.google.gson.Gson().toJson(history)
        getPrefs(context).edit().putString("search_history_v2_${getProviderPrefix()}", json).apply()
    }

    fun getSearchHistory(context: Context): List<String> {
        val json = getPrefs(context).getString("search_history_v2_${getProviderPrefix()}", null)
        return if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                com.google.gson.Gson().fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            val set = getPrefs(context).getStringSet("search_history_${getProviderPrefix()}", emptySet()) ?: emptySet()
            set.toList()
        }
    }

    fun saveUserProfileCache(context: Context, json: String) {
        getPrefs(context).edit().putString("${KEY_USER_PROFILE_CACHE}_${getProviderPrefix()}", json).apply()
    }

    fun getUserProfileCache(context: Context): String? {
        return getPrefs(context).getString("${KEY_USER_PROFILE_CACHE}_${getProviderPrefix()}", null)
    }

    fun saveLocalSongsCache(context: Context, json: String) {
        getPrefs(context).edit().putString("cache_local_songs", json).apply()
    }

    fun getLocalSongsCache(context: Context): String? {
        return getPrefs(context).getString("cache_local_songs", null)
    }

    fun saveAudioFeatures(context: Context, json: String) {
        getPrefs(context).edit().putString(KEY_AUDIO_FEATURES_CACHE, json).apply()
    }

    fun getAudioFeatures(context: Context): String? {
        return getPrefs(context).getString(KEY_AUDIO_FEATURES_CACHE, null)
    }

    fun saveAudioFocusMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_AUDIO_FOCUS_MODE, mode).apply()
    }

    fun getAudioFocusMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUDIO_FOCUS_MODE, 0) // 0: Duck, 1: Pause
    }

    fun saveAllowDucking(context: Context, allow: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_DUCKING, allow).apply()
    }

    fun getAllowDucking(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ALLOW_DUCKING, false)
    }

    fun savePauseOnNoisy(context: Context, pause: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PAUSE_ON_NOISY, pause).apply()
    }

    fun getPauseOnNoisy(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PAUSE_ON_NOISY, true)
    }

    fun saveAutoResumeUsbAudio(context: Context, resume: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_RESUME_USB_AUDIO, resume).apply()
    }

    fun getAutoResumeUsbAudio(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_RESUME_USB_AUDIO, false)
    }
}
