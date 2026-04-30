package cp.player

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class CPApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        android.util.Log.i("CPApplication", "Application onCreate")
        cp.player.manager.DownloadRegistry.init(this)
        // Init module manager
        cp.player.provider.ModuleManager.init(this)
        // Start current provider if available
        cp.player.provider.ProviderManager.startServer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
