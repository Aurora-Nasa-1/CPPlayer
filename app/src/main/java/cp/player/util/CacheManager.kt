package cp.player.util

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一缓存管理器。
 *
 * 提供两层缓存：
 * 1. **内存缓存**（带 TTL）— 快速访问，进程内有效
 * 2. **SharedPreferences 持久化缓存** — 冷启动恢复数据
 *
 * 所有缓存 key 自动按当前 Provider 隔离。
 *
 * ### 使用方式
 * ```kotlin
 * // 保存
 * CacheManager.save(context, CacheType.PLAYLIST_DETAIL, "12345", playlistJson)
 *
 * // 加载（先内存，后磁盘）
 * val json = CacheManager.load(context, CacheType.PLAYLIST_DETAIL, "12345")
 *
 * // 清除所有
 * CacheManager.clearAll(context)
 * ```
 */
object CacheManager {

    private const val PREFS_NAME = "cp_player_prefs"
    private const val DEFAULT_TTL_MS = 30 * 60 * 1000L // 30 分钟

    /** 缓存数据类型 */
    enum class CacheType(val prefix: String, val defaultTtlMs: Long = DEFAULT_TTL_MS) {
        USER_PROFILE("cache_user_profile"),
        USER_PLAYLISTS("cache_user_playlists"),
        RECOMMENDED_SONGS("cache_recommended_songs"),
        PLAYLIST_DETAIL("cache_playlist", 60 * 60 * 1000L),       // 歌单详情 1 小时
        ALBUM_DETAIL("cache_album", 60 * 60 * 1000L),              // 专辑详情 1 小时
        CLOUD_SONGS("cache_cloud_songs"),
        AUDIO_FEATURES("cache_audio_features", Long.MAX_VALUE),   // 永不过期
        LOCAL_SONGS("cache_local_songs", Long.MAX_VALUE),         // 永不过期
        SEARCH_HISTORY("search_history_v2", Long.MAX_VALUE)       // 永不过期（非缓存，是历史）
    }

    // ── 内存缓存层 ──────────────────────────────────────────────

    private data class MemoryEntry(
        val data: String,
        val timestamp: Long = System.currentTimeMillis(),
        val ttlMs: Long = DEFAULT_TTL_MS
    ) {
        fun isExpired(): Boolean =
            ttlMs != Long.MAX_VALUE && System.currentTimeMillis() - timestamp > ttlMs
    }

    private val memoryCache = ConcurrentHashMap<String, MemoryEntry>()

    private fun memoryKey(type: CacheType, key: String): String {
        val provider = cp.player.provider.ProviderManager.currentProvider?.id ?: "default"
        return "${type.prefix}_${key}_$provider"
    }

    // ── 公开 API ────────────────────────────────────────────────

    /**
     * 保存数据到缓存（内存 + SharedPreferences）。
     *
     * @param context Android Context
     * @param type 缓存数据类型
     * @param key 缓存 key（如歌单 ID）
     * @param data 要缓存的 JSON 字符串
     * @param ttlMs 内存缓存 TTL（毫秒），默认使用类型默认值
     */
    fun save(context: Context, type: CacheType, key: String, data: String, ttlMs: Long = type.defaultTtlMs) {
        val mKey = memoryKey(type, key)
        memoryCache[mKey] = MemoryEntry(data, ttlMs = ttlMs)

        val prefsKey = prefsKey(type, key)
        getPrefs(context).edit().putString(prefsKey, data).apply()
    }

    /**
     * 从缓存加载数据。优先内存缓存，回退到 SharedPreferences。
     *
     * @return 缓存的 JSON 字符串，或 null（不存在/已过期）
     */
    fun load(context: Context, type: CacheType, key: String): String? {
        val mKey = memoryKey(type, key)
        val memEntry = memoryCache[mKey]
        if (memEntry != null && !memEntry.isExpired()) {
            return memEntry.data
        }
        // 内存未命中或已过期，从磁盘加载
        val prefsKey = prefsKey(type, key)
        val diskData = getPrefs(context).getString(prefsKey, null)
        if (diskData != null) {
            // 回填内存缓存
            memoryCache[mKey] = MemoryEntry(diskData, ttlMs = type.defaultTtlMs)
        }
        return diskData
    }

    /**
     * 使指定类型的缓存失效。
     *
     * @param key 为 null 时清除该类型下所有缓存
     */
    fun invalidate(context: Context, type: CacheType, key: String? = null) {
        if (key != null) {
            val mKey = memoryKey(type, key)
            memoryCache.remove(mKey)
            val prefsKey = prefsKey(type, key)
            getPrefs(context).edit().remove(prefsKey).apply()
        } else {
            // 清除该类型所有 key
            val prefix = "${type.prefix}_"
            memoryCache.keys.removeAll { it.startsWith(prefix) }
            val editor = getPrefs(context).edit()
            getPrefs(context).all.keys
                .filter { it.startsWith(prefix) }
                .forEach { editor.remove(it) }
            editor.apply()
        }
    }

    /**
     * 清除所有数据缓存。
     *
     * @param providerOnly true = 只清除当前 provider 的缓存；false = 清除所有 provider
     */
    fun clearAll(context: Context, providerOnly: Boolean = true) {
        memoryCache.clear()
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        val provider = cp.player.provider.ProviderManager.currentProvider?.id ?: "default"

        CacheType.entries.forEach { type ->
            if (providerOnly) {
                editor.remove("${type.prefix}_${provider}")
            }
            // 总是清除带 provider 后缀的 key
            prefs.all.keys
                .filter { it.startsWith("${type.prefix}_") && (it.endsWith("_$provider") || !providerOnly) }
                .forEach { editor.remove(it) }
        }
        editor.apply()
    }

    /**
     * 估算 SharedPreferences 中缓存数据的总大小（字节）。
     */
    fun getCacheSizeBytes(context: Context): Long {
        val prefs = getPrefs(context)
        var totalBytes = 0L
        CacheType.entries.forEach { type ->
            prefs.all.keys
                .filter { it.startsWith("${type.prefix}_") }
                .forEach { key ->
                    val value = prefs.all[key]
                    totalBytes += when (value) {
                        is String -> value.toByteArray().size.toLong()
                        else -> 0L
                    }
                }
        }
        return totalBytes
    }

    // ── 内部工具 ────────────────────────────────────────────────

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prefsKey(type: CacheType, key: String): String {
        val provider = cp.player.provider.ProviderManager.currentProvider?.id ?: "default"
        return if (key.isNotEmpty()) {
            "${type.prefix}_${key}_$provider"
        } else {
            "${type.prefix}_$provider"
        }
    }
}
