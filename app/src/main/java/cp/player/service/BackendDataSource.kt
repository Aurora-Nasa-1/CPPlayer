package cp.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ContentDataSource
import cp.player.api.MusicApiServiceImpl
import cp.player.api.MusicApiServiceFactory
import cp.player.manager.DownloadRegistry
import cp.player.monitor.HealthMonitor
import cp.player.provider.ProviderManager
import kotlinx.coroutines.runBlocking
import cp.player.util.JsonUtils
import cp.player.util.DebugLog
import cp.player.util.UserPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * 自定义 DataSource，用于解析 `cp://` 协议的音乐 URI。
 *
 * 解析优先级：
 * 1. 本地已下载文件（DownloadRegistry）
 * 2. URL 缓存（15分钟有效）
 * 3. 通过 [MusicApiService] 解析远程 URL（多 Provider 容灾）
 *
 * @see cp.player.api.MusicApiService
 */
@OptIn(UnstableApi::class)
class BackendDataSource(
    private val context: Context,
    private val httpDataSource: DataSource
) : BaseDataSource(true) {

    private val fileDataSource = FileDataSource()
    private val contentDataSource = ContentDataSource(context)
    private var activeDataSource: DataSource? = null

    companion object {
        private val urlCache = ConcurrentHashMap<String, String>()
        private val cacheExpiry = ConcurrentHashMap<String, Long>()
        private const val CACHE_DURATION = 15 * 60 * 1000L // 15 minutes
    }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        try {
            activeDataSource?.close()
            val uri = dataSpec.uri
            DebugLog.d("CPDS: Opening URI: $uri at position ${dataSpec.position}")

            val songId = if (uri.scheme == "cp") {
                if (uri.host == "song") uri.pathSegments.firstOrNull() else uri.host ?: uri.authority
            } else null

            if (songId == null) {
                val ds = when (uri.scheme) {
                    "content" -> contentDataSource
                    "http", "https" -> httpDataSource
                    else -> fileDataSource
                }
                activeDataSource = ds
                val length = ds.open(dataSpec)
                transferStarted(dataSpec)
                return length
            }

            val quality = uri.getQueryParameter("quality") ?: "standard"

            // 1. Check Local Registry
            val metadata = DownloadRegistry.getMetadata(songId)
            if (metadata != null) {
                val localUri = Uri.parse(metadata.filePath)
                val localDataSpec = dataSpec.buildUpon()
                    .setUri(localUri)
                    .setKey(songId)
                    .build()
                activeDataSource = if (localUri.scheme == "content") contentDataSource else fileDataSource
                val length = activeDataSource!!.open(localDataSpec)
                transferStarted(dataSpec)
                return length
            }

            // 2. URL Cache
            val cacheKey = "${songId}_$quality"
            var cdnUrl = urlCache[cacheKey]?.takeIf { (cacheExpiry[cacheKey] ?: 0L) > System.currentTimeMillis() }

            // 3. Resolve URL via MusicApiService (multi-provider failover)
            if (cdnUrl == null) {
                val api = MusicApiServiceFactory.instance
                val cookie = UserPreferences.getCookie(context)
                val params = mutableMapOf("id" to songId, "level" to quality)
                if (!cookie.isNullOrEmpty()) params["cookie"] = cookie

                val resolveStartTime = System.currentTimeMillis()
                // 使用 callWithAllProviders 进行多 Provider 容灾
                // open() 在 Media3 IO 线程调用，用 runBlocking 桥接 suspend 函数
                val resolvedUrl = runBlocking {
                    api.callWithAllProviders(
                        cp.player.api.MusicApiMethod.SONG_URL_V1,
                        params
                    ) { body ->
                        val url = body.get("redirectUrl")?.asString ?: JsonUtils.findUrl(body)
                        if (!url.isNullOrEmpty() && url.startsWith("http")) url else null
                    }
                }

                if (resolvedUrl != null) {
                    cdnUrl = resolvedUrl
                    urlCache[cacheKey] = cdnUrl
                    cacheExpiry[cacheKey] = System.currentTimeMillis() + CACHE_DURATION
                } else {
                    // 所有 Provider 均失败
                    HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                        timestamp = resolveStartTime,
                        providerId = ProviderManager.getCurrentProviderId(),
                        method = "URL_RESOLUTION",
                        durationMs = System.currentTimeMillis() - resolveStartTime,
                        success = false,
                        errorMessage = "所有 Provider 均无法解析歌曲 $songId 的播放 URL"
                    ))
                }
            }

            if (cdnUrl == null) throw IOException("Failed to resolve audio URL for song $songId")

            DebugLog.i("CPDS: Resolved $songId to $cdnUrl")

            val resolvedDataSpec = dataSpec.buildUpon()
                .setUri(Uri.parse(cdnUrl))
                .setKey(songId)
                .build()

            activeDataSource = httpDataSource
            val length = httpDataSource.open(resolvedDataSpec)
            transferStarted(dataSpec)
            return length
        } catch (e: Exception) {
            DebugLog.e("CPDS: Final open failure for ${dataSpec.uri}", e)
            throw if (e is IOException) e else IOException(e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        try {
            val read = activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
            if (read > 0) {
                bytesTransferred(read)
            }
            return read
        } catch (e: IOException) {
            throw e
        }
    }

    override fun getUri(): Uri? = activeDataSource?.getUri()
    override fun getResponseHeaders(): Map<String, List<String>> = activeDataSource?.getResponseHeaders() ?: emptyMap()

    override fun close() {
        try {
            activeDataSource?.close()
        } catch (e: IOException) {
            DebugLog.e("CPDS: Error closing", e)
        } finally {
            activeDataSource = null
            transferEnded()
        }
    }

    class Factory(
        private val context: Context,
        private val httpDataSourceFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return BackendDataSource(context, httpDataSourceFactory.createDataSource())
        }
    }
}
