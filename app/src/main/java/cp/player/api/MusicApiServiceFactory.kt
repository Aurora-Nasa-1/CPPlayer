package cp.player.api

import android.content.Context

/**
 * [MusicApiService] 的工厂与单例持有者。
 *
 * 在 [cp.player.CPApplication.onCreate] 中调用 [init] 后，
 * 全局可通过 [instance] 获取唯一实例。
 */
object MusicApiServiceFactory {

    @Volatile
    private var _instance: MusicApiServiceImpl? = null

    /** 当前实例，必须先调用 [init] */
    val instance: MusicApiServiceImpl
        get() = _instance ?: throw IllegalStateException(
            "MusicApiService not initialized. Call MusicApiServiceFactory.init(context) in Application.onCreate()."
        )

    /**
     * 初始化全局 MusicApiService 实例。
     * 应在 Application.onCreate() 中、ProviderManager 初始化之后调用。
     */
    fun init(context: Context) {
        if (_instance == null) {
            synchronized(this) {
                if (_instance == null) {
                    _instance = MusicApiServiceImpl(context.applicationContext)
                }
            }
        }
    }
}
