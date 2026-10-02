package cp.player.provider

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class HttpProvider(
    override val id: String,
    override val name: String,
    override val version: String,
    private val baseUrl: String,
    override val apiMap: Map<String, String>? = null,
    override val updateUrl: String? = null
) : BackendProvider {
    override val type = ProviderType.HTTP
    private val client = OkHttpClient.Builder().apply {
        // Remove Sentry auto-instrumentation interceptors to prevent spamming Sentry with user-backend 502s
        interceptors().removeAll { it.javaClass.name.contains("sentry", ignoreCase = true) }
        networkInterceptors().removeAll { it.javaClass.name.contains("sentry", ignoreCase = true) }
    }.build()
    private val gson = com.google.gson.Gson()

    override fun startServer(context: Context, port: Int) {}
    override fun stopServer() {}

    override fun callApi(method: String, params: Map<String, String>): String {
        val json = gson.toJson(params)
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/$method")
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: """{"code": 200, "msg": "Empty response"}"""
                } else {
                    """{"code": ${response.code}, "msg": "HTTP Error ${response.code}"}"""
                }
            }
        } catch (e: Throwable) {
            """{"code": 500, "msg": "HTTP call failed: ${e.message}"}"""
        }
    }

    override fun analyzeAudio(path: String): String = """{"code": 500, "msg": "Not supported"}"""
}
