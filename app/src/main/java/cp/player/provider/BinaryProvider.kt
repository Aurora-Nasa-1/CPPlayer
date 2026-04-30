package cp.player.provider

import android.content.Context
import android.util.Log
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class BinaryProvider(
    override val id: String,
    override val name: String,
    override val version: String,
    private val binaryPath: String,
    override val apiMap: Map<String, String>? = null
) : BackendProvider {
    override val type = ProviderType.BINARY
    private var process: Process? = null
    private var port: Int = 3000
    private val client = OkHttpClient()

    override fun startServer(context: Context, port: Int) {
        this.port = port
        val file = File(binaryPath)
        if (!file.exists()) {
            Log.e("BinaryProvider", "Binary not found: $binaryPath")
            return
        }
        try {
            file.setExecutable(true)
            process = ProcessBuilder(binaryPath, "--port", port.toString())
                .directory(file.parentFile)
                .redirectErrorStream(true)
                .start()
            Log.i("BinaryProvider", "Started binary: $binaryPath on port $port")
        } catch (e: Exception) {
            Log.e("BinaryProvider", "Failed to start binary", e)
        }
    }

    override fun stopServer() {
        process?.destroy()
        process = null
    }

    override fun callApi(method: String, params: Map<String, String>): String {
        val json = com.google.gson.Gson().toJson(params)
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/api/$method")
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { response ->
                response.body.string()
            }
        } catch (e: Exception) {
            """{"code": 500, "msg": "Binary call failed: ${e.message}"}"""
        }
    }

    override fun analyzeAudio(path: String): String = """{"code": 500}"""
}
