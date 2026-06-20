package cp.player.service

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import cp.player.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AMLL TTML 歌词数据库服务。
 *
 * 从 https://github.com/amll-dev/amll-ttml-db 获取 TTML 格式歌词，
 * 使用 accompanist-lyrics-core 的 AutoParser 解析为 [SyncedLyrics]。
 *
 * 支持的平台：
 * - ncm: 网易云音乐 (ncm-lyrics/)
 * - qq: QQ 音乐 (qq-lyrics/)
 * - am: Apple Music (am-lyrics/)
 * - spotify: Spotify (spotify-lyrics/)
 */
object AmllLyricService {

    private const val BASE_URL =
        "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val parser = AutoParser()

    /** 内存缓存：key = "platform:songId" */
    private val cache = LinkedHashMap<String, SyncedLyrics>(32, 0.75f, true)
    private const val MAX_CACHE_SIZE = 100

    /** 记录不存在的歌曲，避免重复请求 404 */
    private val notFoundCache = mutableSetOf<String>()
    private const val MAX_NOT_FOUND_SIZE = 500

    /**
     * 平台 ID → AMLL 数据库文件夹名映射。
     */
    private val platformFolders = mapOf(
        "ncm" to "ncm-lyrics",
        "qq" to "qq-lyrics",
        "am" to "am-lyrics",
        "spotify" to "spotify-lyrics"
    )

    /**
     * 从 AMLL 数据库获取 TTML 歌词并解析。
     *
     * @param songId 歌曲 ID（即平台原始 ID）
     * @param platform 平台标识（ncm / qq / am / spotify）
     * @return 解析后的 [SyncedLyrics]，获取失败返回 null
     */
    suspend fun fetchTtmlLyrics(songId: String, platform: String): SyncedLyrics? {
        val folder = platformFolders[platform] ?: run {
            DebugLog.w("AmllLyricService: 未知平台 '$platform'")
            return null
        }

        val cacheKey = "$platform:$songId"

        // 检查内存缓存
        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        // 检查已知不存在的歌曲
        synchronized(notFoundCache) {
            if (cacheKey in notFoundCache) return null
        }

        val url = "$BASE_URL/$folder/$songId.ttml"
        DebugLog.i("AmllLyricService: 获取 $url")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    DebugLog.w("AmllLyricService: HTTP ${response.code} for $platform/$songId")
                    if (response.code == 404) {
                        synchronized(notFoundCache) {
                            if (notFoundCache.size >= MAX_NOT_FOUND_SIZE) notFoundCache.clear()
                            notFoundCache.add(cacheKey)
                        }
                    }
                    response.close()
                    return@withContext null
                }
                val body = response.body.string()
                response.close()
                if (body.isBlank()) return@withContext null

                val result = parser.parse(body)
                if (result != null) {
                    // 存入缓存
                    synchronized(cache) {
                        if (cache.size >= MAX_CACHE_SIZE) {
                            val oldest = cache.keys.first()
                            cache.remove(oldest)
                        }
                        cache[cacheKey] = result
                    }
                    DebugLog.i("AmllLyricService: 成功获取 $platform/$songId, ${result.lines.size} 行")
                } else {
                    DebugLog.w("AmllLyricService: TTML 解析失败 for $platform/$songId")
                }
                result
            } catch (e: Exception) {
                DebugLog.e("AmllLyricService: 请求失败 $platform/$songId", e)
                null
            }
        }
    }

    /**
     * 根据 provider ID 和名称推断 AMLL 平台。
     *
     * 同时检查 provider 的 ID 和显示名称，提高识别率。
     *
     * @param providerId provider 模块的 ID
     * @param providerName provider 模块的显示名称
     * @return 平台标识，无法识别返回 null
     */
    fun detectPlatform(providerId: String?, providerName: String? = null): String? {
        if (providerId == null && providerName == null) return null
        val idLower = (providerId ?: "").lowercase()
        val nameLower = (providerName ?: "").lowercase()
        val combined = "$idLower $nameLower"
        return when {
            "netease" in combined || "ncm" in combined || "云音乐" in combined || "网易" in combined -> "ncm"
            "qq" in combined || "tencent" in combined || "qq音乐" in combined -> "qq"
            "apple" in combined || "apple music" in combined || "itunes" in combined -> "am"
            "spotify" in combined -> "spotify"
            else -> {
                DebugLog.w("AmllLyricService: 无法识别平台 from provider id='$providerId', name='$providerName'")
                null
            }
        }
    }

    /**
     * 清除缓存。
     */
    fun clearCache() {
        synchronized(cache) { cache.clear() }
        synchronized(notFoundCache) { notFoundCache.clear() }
    }
}
