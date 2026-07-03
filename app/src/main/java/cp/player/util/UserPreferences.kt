package cp.player.util

import android.content.Context
import android.content.SharedPreferences
import cp.player.util.CacheManager

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
    private const val KEY_AUDIO_FOCUS_MODE = "audio_focus_mode"
    private const val KEY_ALLOW_DUCKING = "allow_ducking"
    private const val KEY_PAUSE_ON_NOISY = "pause_on_noisy"
    private const val KEY_AUTO_RESUME_USB_AUDIO = "auto_resume_usb_audio"
    private const val KEY_FADE_MODE = "fade_mode" // 0: Crossfade, 1: Single Fade, 2: Off
    private const val KEY_AUTO_AUDIO_FOCUS = "auto_audio_focus"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts"
    private const val KEY_AUDIO_ENGINE = "audio_engine"
    private const val KEY_DSD_OUTPUT_MODE = "dsd_output_mode"
    private const val KEY_DAP_BIT_PERFECT = "dap_bit_perfect"
    private const val KEY_USB_EXCLUSIVE = "usb_exclusive"
    private const val KEY_FONT_ROUNDNESS = "font_roundness" // 0: Standard, 1: Expressive
    private const val KEY_PLAY_IMMEDIATELY = "play_immediately"
    private const val KEY_LYRICS_SOURCE = "lyrics_source" // 0: Provider API 优先, 1: AMLL 优先, 2: 仅 AMLL
    private const val KEY_AMLL_PLATFORM = "amll_platform" // "auto", "ncm", "qq", "am", "spotify"
    private const val KEY_HIDE_NAVBAR_ON_SCROLL = "hide_navbar_on_scroll"
    private const val KEY_WAVY_PROGRESS = "wavy_progress"

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
        val cookie: String,
        /** 此账号所属的 Provider ID */
        val providerId: String = "",
        /** 此账号所属的 Provider 显示名 */
        val providerName: String = ""
    )

    /**
     * 保存账号到当前提供商的账号列表。
     * 如果 [SavedAccount.providerId] 为空，自动填充当前 Provider 信息。
     */
    fun saveAccount(context: Context, account: SavedAccount) {
        val enriched = if (account.providerId.isEmpty()) {
            account.copy(
                providerId = getProviderPrefix(),
                providerName = cp.player.provider.ProviderManager.currentProvider?.name ?: "Unknown"
            )
        } else account

        // 按 provider 分桶存储
        val providerKey = enriched.providerId.ifEmpty { getProviderPrefix() }
        val currentList = getSavedAccountsForProvider(context, providerKey).toMutableList()
        val index = currentList.indexOfFirst { it.uid == enriched.uid }
        if (index >= 0) {
            currentList[index] = enriched
        } else {
            currentList.add(enriched)
        }
        val json = com.google.gson.Gson().toJson(currentList)
        getPrefs(context).edit().putString("${KEY_SAVED_ACCOUNTS}_${providerKey}", json).apply()

        // 同步更新全局账号索引（用于跨提供商显示所有账号）
        syncGlobalAccountIndex(context)
    }

    /**
     * 移除指定提供商下的账号。如果 [providerId] 为 null，使用当前 Provider。
     */
    fun removeAccount(context: Context, uid: Long, providerId: String? = null) {
        val providerKey = providerId ?: getProviderPrefix()
        val currentList = getSavedAccountsForProvider(context, providerKey).toMutableList()
        currentList.removeAll { it.uid == uid }
        val json = com.google.gson.Gson().toJson(currentList)
        getPrefs(context).edit().putString("${KEY_SAVED_ACCOUNTS}_${providerKey}", json).apply()

        syncGlobalAccountIndex(context)
    }

    /**
     * 获取当前提供商下保存的账号列表（向后兼容）。
     */
    fun getSavedAccounts(context: Context): List<SavedAccount> {
        return getSavedAccountsForProvider(context, getProviderPrefix())
    }

    /**
     * 获取指定提供商下保存的账号列表。
     */
    fun getSavedAccountsForProvider(context: Context, providerId: String): List<SavedAccount> {
        val json = getPrefs(context).getString("${KEY_SAVED_ACCOUNTS}_${providerId}", null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<SavedAccount>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有提供商下的所有账号（全局视图）。
     * 用于账号管理界面显示跨提供商账号。
     */
    fun getAllSavedAccounts(context: Context): List<SavedAccount> {
        val globalIndexJson = getPrefs(context).getString("${KEY_SAVED_ACCOUNTS}_global_index", null)
        if (globalIndexJson.isNullOrEmpty()) return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val providerIds: List<String> = com.google.gson.Gson().fromJson(globalIndexJson, type)
            providerIds.flatMap { pid -> getSavedAccountsForProvider(context, pid) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 同步全局账号索引：扫描所有存储的提供商 ID 列表。
     */
    private fun syncGlobalAccountIndex(context: Context) {
        val prefs = getPrefs(context)
        val allKeys = prefs.all.keys
        val providerIds = allKeys
            .filter { it.startsWith("${KEY_SAVED_ACCOUNTS}_") && !it.endsWith("_global_index") }
            .map { it.removePrefix("${KEY_SAVED_ACCOUNTS}_") }
            .filter { it.isNotEmpty() && it != "default" }

        // 包含当前 provider
        val currentId = getProviderPrefix()
        val merged = (providerIds + currentId).distinct()

        val json = com.google.gson.Gson().toJson(merged)
        prefs.edit().putString("${KEY_SAVED_ACCOUNTS}_global_index", json).apply()
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
        return getPrefs(context).getInt(KEY_THEME_MODE, 1) // 默认跟随封面
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

    fun saveAudioFeatures(context: Context, json: String) {
        CacheManager.save(context, CacheManager.CacheType.AUDIO_FEATURES, "", json)
    }

    fun getAudioFeatures(context: Context): String? {
        return CacheManager.load(context, CacheManager.CacheType.AUDIO_FEATURES, "")
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

    fun saveAudioEngine(context: Context, engine: Int) {
        getPrefs(context).edit().putInt(KEY_AUDIO_ENGINE, engine).apply()
    }

    fun getAudioEngine(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUDIO_ENGINE, 0)
    }

    fun saveDsdOutputMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_DSD_OUTPUT_MODE, mode).apply()
    }

    fun getDsdOutputMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_DSD_OUTPUT_MODE, 0)
    }

    fun saveDapBitPerfect(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DAP_BIT_PERFECT, enabled).apply()
    }

    fun getDapBitPerfect(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DAP_BIT_PERFECT, false)
    }

    fun saveUsbExclusive(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USB_EXCLUSIVE, enabled).apply()
    }

    fun getUsbExclusive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USB_EXCLUSIVE, false)
    }

    fun saveFontRoundness(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_FONT_ROUNDNESS, mode).apply()
    }

    fun getFontRoundness(context: Context): Int {
        return getPrefs(context).getInt(KEY_FONT_ROUNDNESS, 1) // 默认 Expressive
    }

    fun savePlayImmediately(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PLAY_IMMEDIATELY, enabled).apply()
    }

    fun getPlayImmediately(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PLAY_IMMEDIATELY, false)
    }

    fun saveLyricsSource(context: Context, source: Int) {
        getPrefs(context).edit().putInt(KEY_LYRICS_SOURCE, source).apply()
    }

    fun getLyricsSource(context: Context): Int {
        return getPrefs(context).getInt(KEY_LYRICS_SOURCE, 0) // 0: Provider API 优先
    }

    fun saveAmllPlatform(context: Context, platform: String) {
        getPrefs(context).edit().putString(KEY_AMLL_PLATFORM, platform).apply()
    }

    fun getAmllPlatform(context: Context): String {
        return getPrefs(context).getString(KEY_AMLL_PLATFORM, "auto") ?: "auto"
    }

    fun saveHideNavbarOnScroll(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_NAVBAR_ON_SCROLL, enabled).apply()
    }

    fun getHideNavbarOnScroll(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HIDE_NAVBAR_ON_SCROLL, true) // 默认开启
    }

    fun saveWavyProgress(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WAVY_PROGRESS, enabled).apply()
    }

    fun getWavyProgress(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WAVY_PROGRESS, false) // 默认平直
    }
}
