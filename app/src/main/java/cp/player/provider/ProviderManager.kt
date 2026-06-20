package cp.player.provider

import android.content.Context
import android.util.Log
import cp.player.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 提供商管理器。
 *
 * 负责管理所有已加载的 [BackendProvider] 实例、当前活跃 Provider 的切换，
 * 以及通过 [onProviderChanged] 通知 UI 层 Provider 变更。
 *
 * ### 设计原则
 * - 所有 Provider 加载由 [ModuleManager] 完成，[ProviderManager] 只负责管理活跃状态
 * - 切换 Provider 时自动停止旧 Provider 的服务并启动新 Provider 的服务
 * - 通过 [StateFlow] 暴露当前 Provider 状态供 Compose 观察
 * - 自动持久化用户选择的 Provider，重启后恢复
 */
object ProviderManager {
    private const val TAG = "ProviderManager"
    private const val PREFS_NAME = "cp_player_prefs"
    private const val KEY_LAST_PROVIDER_ID = "last_active_provider_id"

    /** 当前活跃的 Provider（向后兼容） */
    var currentProvider: BackendProvider? = null
        private set

    /** 当前活跃 Provider 的可观察状态 */
    private val _currentProviderFlow = MutableStateFlow<BackendProvider?>(null)
    val currentProviderFlow: StateFlow<BackendProvider?> = _currentProviderFlow.asStateFlow()

    /** Provider 切换监听器列表 */
    private val changeListeners = mutableListOf<(BackendProvider?) -> Unit>()

    /**
     * 启动当前 Provider 的服务。
     */
    fun startServer(context: Context, port: Int = 3000) {
        currentProvider?.startServer(context, port)
    }

    /**
     * 切换当前活跃的 Provider。
     *
     * 会自动停止旧 Provider 服务、启动新 Provider 服务，
     * 持久化用户选择，并通知所有监听器。
     *
     * @param provider 要切换到的 Provider，或 null 清除当前 Provider
     * @param context 用于启动新 Provider 服务的 Context
     * @param port 服务端口
     * @param save 是否持久化用户选择（默认 true）。自动选择第一个模块时应传 false，避免覆盖用户之前的偏好。
     * @return true 如果切换成功
     */
    fun switchProvider(provider: BackendProvider?, context: Context? = null, port: Int = 3000, save: Boolean = true): Boolean {
        if (provider?.id == currentProvider?.id) return true // 无需切换

        Log.i(TAG, "Switching provider: ${currentProvider?.id ?: "none"} → ${provider?.id ?: "none"} (save=$save)")

        // 停止旧 Provider
        try {
            currentProvider?.stopServer()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping old provider", e)
        }

        currentProvider = provider
        _currentProviderFlow.value = provider

        // 持久化用户选择的 Provider ID（仅在用户主动切换时保存）
        if (save && provider != null && context != null) {
            saveLastProviderId(context, provider.id)
        }

        // 启动新 Provider
        if (provider != null && context != null) {
            try {
                provider.startServer(context, port)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting new provider", e)
            }
        }

        // 通知监听器
        changeListeners.forEach { listener ->
            try {
                listener(provider)
            } catch (e: Exception) {
                Log.w(TAG, "Error in provider change listener", e)
            }
        }

        return true
    }

    /**
     * 通过 Provider ID 切换当前活跃 Provider。
     *
     * @return true 如果找到并切换成功
     */
    fun switchProviderById(providerId: String, context: Context? = null, port: Int = 3000): Boolean {
        val provider = ModuleManager.getProvider(providerId)
            ?: ModuleManager.getAvailableProviders().find { it.id == providerId }
        if (provider == null) {
            Log.w(TAG, "Provider not found: $providerId")
            return false
        }
        return switchProvider(provider, context, port)
    }

    /**
     * 尝试恢复上次保存的 Provider。
     * 在 [ModuleManager.init] 加载所有模块后调用。
     *
     * @return true 如果成功恢复到上次的 Provider
     */
    fun restoreLastProvider(context: Context): Boolean {
        val lastId = getLastProviderId(context) ?: return false
        if (currentProvider?.id == lastId) return true // 已经是当前 Provider
        Log.i(TAG, "Restoring last provider: $lastId")
        return switchProviderById(lastId, context)
    }

    /**
     * 添加 Provider 切换监听器。
     */
    fun addOnProviderChangedListener(listener: (BackendProvider?) -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * 移除 Provider 切换监听器。
     */
    fun removeOnProviderChangedListener(listener: (BackendProvider?) -> Unit) {
        changeListeners.remove(listener)
    }

    /**
     * 调用当前 Provider 的 API。
     *
     * 自动通过 [BackendProvider.apiMap] 映射方法名。
     * 如果方法被映射为 "unsupported"，返回不支持提示。
     * 监控记录由上层 [cp.player.api.MusicApiServiceImpl.callApi] 统一处理。
     */
    fun callApi(method: String, params: Map<String, String>): String {
        val provider = currentProvider ?: return """{"code": 500, "msg": "No active provider"}"""
        val mappedMethod = provider.apiMap?.get(method) ?: method

        // API 调用调试日志：追踪方法名映射
        if (mappedMethod != method) {
            DebugLog.d("API映射: $method → $mappedMethod (provider: ${provider.id})")
        }
        if (provider.apiMap != null && !provider.apiMap!!.containsKey(method)) {
            DebugLog.w("API未映射: $method (provider: ${provider.id}, apiMap keys: ${provider.apiMap!!.keys.take(10)})")
        }

        if (mappedMethod.isEmpty() || mappedMethod.equals("unsupported", ignoreCase = true)) {
            Log.w(TAG, "API不支持: $method → $mappedMethod (provider: ${provider.id})")
            return """{"code": -1, "msg": "该提供商不支持此功能"}"""
        }

        return try {
            provider.callApi(mappedMethod, params)
        } catch (e: Exception) {
            DebugLog.e("Provider callApi 异常: $method (provider: ${provider.id})", e)
            """{"code": 500, "msg": "Provider call failed: ${e.message}"}"""
        }
    }

    /**
     * 获取当前 Provider 的显示名称。
     */
    fun getCurrentProviderName(): String = currentProvider?.name ?: "No Provider"

    /**
     * 获取当前 Provider 的 ID。
     */
    fun getCurrentProviderId(): String = currentProvider?.id ?: "default"

    // ======================== 持久化 ========================

    private fun saveLastProviderId(context: Context, providerId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_PROVIDER_ID, providerId).apply()
    }

    private fun getLastProviderId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PROVIDER_ID, null)
    }
}
