package cp.player.provider

import android.content.Context
import android.util.Log

class JniProvider(
    override val id: String,
    override val name: String,
    override val version: String,
    private val soPath: String,
    override val apiMap: Map<String, String>? = null
) : BackendProvider {
    override val type = ProviderType.JNI
    private var isLoaded = false

    init {
        try {
            System.load(soPath)
            isLoaded = true
            Log.i("JniProvider", "Native JNI library loaded: $soPath")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("JniProvider", "Failed to load $soPath", e)
        }
    }

    external fun startNativeServer(host: String, port: Int)
    external fun nativeCallApi(method: String, paramsJson: String): String
    external fun analyzeAudioFile(path: String): String

    override fun startServer(context: Context, port: Int) {
        if (isLoaded) {
            try {
                startNativeServer("127.0.0.1", port)
            } catch (e: Exception) {
                Log.e("JniProvider", "startNativeServer failed", e)
            }
        }
    }

    override fun stopServer() {}

    override fun callApi(method: String, params: Map<String, String>): String {
        if (!isLoaded) return """{"code": 500, "msg": "JNI not loaded"}"""
        val json = com.google.gson.Gson().toJson(params)
        return try {
            nativeCallApi(method, json)
        } catch (e: Exception) {
            """{"code": 500, "msg": "JNI call failed: ${e.message}"}"""
        }
    }

    override fun analyzeAudio(path: String): String {
        if (!isLoaded) return """{"code": 500, "msg": "JNI not loaded"}"""
        return try {
            analyzeAudioFile(path)
        } catch (e: Exception) {
            """{"code": 500, "msg": "JNI analyze failed: ${e.message}"}"""
        }
    }
}
