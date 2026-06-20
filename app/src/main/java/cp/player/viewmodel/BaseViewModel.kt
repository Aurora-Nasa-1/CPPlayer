package cp.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cp.player.api.MusicApiService
import cp.player.api.MusicApiServiceFactory
import cp.player.util.UserPreferences

/**
 * ViewModel 基类。
 *
 * 提供统一的 [MusicApiService] 访问和通用状态管理。
 * 所有 ViewModel 应继承此类以获取 API 调用能力。
 *
 * @see cp.player.api.MusicApiService
 * @see cp.player.api.MusicApiMethod
 */
open class BaseViewModel(application: Application) : AndroidViewModel(application) {

    /** 统一的音乐 API 服务实例（类型安全，优先使用） */
    protected val api: MusicApiService = MusicApiServiceFactory.instance

    /** 当前用户的 cookie（基于当前活跃 Provider） */
    val cookie: String? get() = UserPreferences.getCookie(getApplication())
}
