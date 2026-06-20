package cp.player.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import cp.player.service.BackendDataSource
import cp.player.service.MusicService

@OptIn(UnstableApi::class)
object DataSourceFactoryProvider {
    private const val USER_AGENT = "NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)"
    private val REFERER_HEADERS = mapOf("Referer" to "https://music.163.com")

    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(REFERER_HEADERS)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)
    }

    fun createCpDataSourceFactory(context: Context): DataSource.Factory {
        return BackendDataSource.Factory(context, createHttpDataSourceFactory())
    }

    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(MusicService.getCache(context))
            .setUpstreamDataSourceFactory(createCpDataSourceFactory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
