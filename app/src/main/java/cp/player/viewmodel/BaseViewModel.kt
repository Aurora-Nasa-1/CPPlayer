package cp.player.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import cp.player.api.MusicApiService
import cp.player.api.MusicApiServiceFactory
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences
import kotlinx.coroutines.launch

/**
 * ViewModel 基类。
 *
 * 提供统一的 [MusicApiService] 访问、Provider 变更监听、加载状态管理等通用能力。
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

    /**
     * 监听 Provider 变更并重置状态。
     *
     * 自动跳过首次发射（初始化），仅在 Provider 实际切换时触发 [onReset] 回调。
     * 消除了 UserViewModel、SearchViewModel、SocialViewModel 中的重复代码。
     *
     * @param tag 日志标签（通常传 TAG 常量）
     * @param onReset Provider 切换时的状态重置逻辑
     */
    protected fun observeProviderChange(tag: String, onReset: () -> Unit) {
        viewModelScope.launch {
            var isFirst = true
            ProviderManager.currentProviderFlow.collect { provider ->
                if (isFirst) { isFirst = false; return@collect }
                if (provider != null) {
                    Log.i(tag, "Provider changed to ${provider.id}, resetting ...")
                    onReset()
                }
            }
        }
    }

    /**
     * 带加载状态的 API 调用包装。
     *
     * 自动管理 loading 状态，消除 try/finally 模式的重复代码。
     *
     * @param setLoading loading 状态变更回调
     * @param block 实际的 API 调用逻辑
     * @return API 调用结果
     */
    protected suspend fun <T> withLoading(
        setLoading: (Boolean) -> Unit,
        block: suspend () -> T
    ): T {
        setLoading(true)
        return try {
            block()
        } finally {
            setLoading(false)
        }
    }

    companion object {
        /**
         * JsonObject 扩展：判断 API 响应是否成功。
         *
         * 消除 `body.get("code")?.asInt == 200` 的重复代码。
         */
        fun JsonObject.isSuccess(): Boolean {
            val code = get("code")?.asInt
            return code == 200 || code == 0 || code == 201 || code == 301
        }

        /**
         * JsonObject 扩展：安全获取嵌套的 data.profile 对象。
         *
         * 用于 getLoginStatus 等返回 data.profile 结构的 API。
         */
        fun JsonObject.getDataProfile(): JsonObject? {
            val dataElem = get("data")
            val profileElem = if (dataElem != null && dataElem.isJsonObject) {
                dataElem.asJsonObject.get("profile")
            } else {
                get("profile")
            }
            return if (profileElem != null && profileElem.isJsonObject) profileElem.asJsonObject else null
        }
    }
}
