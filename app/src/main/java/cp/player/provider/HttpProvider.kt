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
    private val client = OkHttpClient()

    override fun startServer(context: Context, port: Int) {}
    override fun stopServer() {}

    override fun callApi(method: String, params: Map<String, String>): String {
        val json = com.google.gson.Gson().toJson(params)
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/$method")
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { response ->
                response.body.string()
            }
        } catch (e: Exception) {
            """{"code": 500, "msg": "HTTP call failed: ${e.message}"}"""
        }
    }

    override fun analyzeAudio(path: String): String = """{"code": 500, "msg": "Not supported"}"""
}
