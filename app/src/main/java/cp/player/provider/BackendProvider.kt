package cp.player.provider

import android.content.Context

interface BackendProvider {
    val id: String
    val name: String
    val version: String
    val type: ProviderType
    val apiMap: Map<String, String>?

    fun startServer(context: Context, port: Int)
    fun stopServer()
    fun callApi(method: String, params: Map<String, String>): String
    fun analyzeAudio(path: String): String
}

enum class ProviderType {
    JNI,
    BINARY,
    WEBSOCKET,
    HTTP
}
