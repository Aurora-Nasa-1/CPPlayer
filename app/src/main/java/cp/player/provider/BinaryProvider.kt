package cp.player.provider

import android.content.Context
import android.os.Build
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
    override val apiMap: Map<String, String>? = null,
    override val updateUrl: String? = null
) : BackendProvider {
    override val type = ProviderType.BINARY
    private var process: Process? = null
    private var port: Int = 3000
    private val client = OkHttpClient()
    private val gson = com.google.gson.Gson()
    private var loadError: String? = null

    init {
        // 校验 ELF 架构是否匹配当前设备
        val file = File(binaryPath)
        if (file.exists()) {
            validateElfHeader(file)
        }
    }

    /**
     * 校验 ELF 文件头，检查架构是否匹配当前设备。
     * 架构不匹配时设置 loadError。
     */
    private fun validateElfHeader(file: File) {
        try {
            file.inputStream().use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4 ||
                    magic[0] != 0x7F.toByte() ||
                    magic[1] != 'E'.code.toByte() ||
                    magic[2] != 'L'.code.toByte() ||
                    magic[3] != 'F'.code.toByte()
                ) {
                    return // 非标准格式，允许尝试执行
                }

                if (fis.skip(0x0EL) != 0x0EL) return
                val eMachine = ByteArray(2)
                if (fis.read(eMachine) != 2) return

                val machine = (eMachine[0].toInt() and 0xFF) or ((eMachine[1].toInt() and 0xFF) shl 8)
                val currentArch = when (Build.SUPPORTED_ABIS.firstOrNull()) {
                    "arm64-v8a" -> 0xB7
                    "armeabi-v7a" -> 0x28
                    "x86_64" -> 0x3E
                    "x86" -> 0x03
                    else -> 0
                }
                if (currentArch != 0 && machine != currentArch) {
                    loadError = "Binary 架构不匹配: 文件=0x${machine.toString(16)}, 设备=0x${currentArch.toString(16)} (${Build.SUPPORTED_ABIS.firstOrNull()})"
                    Log.e(TAG, loadError!!)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ELF 格式校验跳过: ${e.message}")
        }
    }

    /** 获取加载失败的错误信息 */
    fun getLoadError(): String? = loadError

    override fun isReady(): Boolean = loadError == null

    override fun startServer(context: Context, port: Int) {
        if (loadError != null) {
            Log.e(TAG, "Binary 架构不匹配，跳过启动: $id ($loadError)")
            return
        }
        this.port = port
        val file = File(binaryPath)
        if (!file.exists()) {
            Log.e(TAG, "Binary not found: $binaryPath")
            return
        }
        try {
            file.setExecutable(true)
            process = ProcessBuilder(binaryPath, "--port", port.toString())
                .directory(file.parentFile)
                .redirectErrorStream(true)
                .start()
            Log.i(TAG, "Started binary: $binaryPath on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start binary", e)
        }
    }

    override fun stopServer() {
        process?.destroy()
        process = null
    }

    override fun callApi(method: String, params: Map<String, String>): String {
        if (loadError != null) return """{"code": 500, "msg": "Binary not ready: $loadError"}"""
        val json = gson.toJson(params)
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

    companion object {
        private const val TAG = "BinaryProvider"
    }
}
