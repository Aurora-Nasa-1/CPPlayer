package cp.player.provider

import android.content.Context
import android.util.Log

object ProviderManager {
    var currentProvider: BackendProvider? = null

    fun startServer(context: Context, port: Int = 3000) {
        currentProvider?.startServer(context, port)
    }

    fun stopServer() {
        currentProvider?.stopServer()
    }

    fun callApi(method: String, params: Map<String, String>): String {
        val provider = currentProvider ?: return """{"code": 500, "msg": "No active provider"}"""
        val mappedMethod = provider.apiMap?.get(method) ?: method
        
        if (mappedMethod.isEmpty() || mappedMethod.equals("unsupported", ignoreCase = true)) {
            return """{"code": -1, "msg": "该提供商不支持此功能"}"""
        }
        
        return provider.callApi(mappedMethod, params)
    }

    fun analyzeAudio(path: String): String {
        return currentProvider?.analyzeAudio(path) ?: """{"code": 500, "msg": "No active provider"}"""
    }
}
