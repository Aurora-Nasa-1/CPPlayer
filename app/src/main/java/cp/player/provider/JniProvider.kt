package cp.player.provider

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

class JniProvider(
    override val id: String,
    override val name: String,
    override val version: String,
    private val soPath: String,
    override val apiMap: Map<String, String>? = null,
    override val updateUrl: String? = null
) : BackendProvider {
    override val type = ProviderType.JNI
    private var isLoaded = false
    private var loadError: String? = null

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        val soFile = File(soPath)

        // 1. 检查 .so 文件是否存在
        if (!soFile.exists()) {
            loadError = "SO 文件不存在: $soPath"
            Log.e(TAG, loadError!!)
            return
        }

        // 2. 检查文件大小（空文件或过小的文件一定不是有效 .so）
        if (soFile.length() < 1024) {
            loadError = "SO 文件过小 (${soFile.length()} bytes)，可能已损坏: $soPath"
            Log.e(TAG, loadError!!)
            return
        }

        // 3. 检查文件是否可读
        if (!soFile.canRead()) {
            loadError = "SO 文件无法读取（权限不足）: $soPath"
            Log.e(TAG, loadError!!)
            return
        }

        // 4. 检查 ELF 魔数（0x7F454C46 = "\x7FELF"）
        validateElfHeader(soFile)

        // 5. 尝试加载 .so
        try {
            System.load(soPath)
            isLoaded = true
            Log.i(TAG, "JNI 库加载成功: $soPath")
        } catch (e: UnsatisfiedLinkError) {
            loadError = "JNI 链接失败: ${e.message}"
            Log.e(TAG, "加载 SO 失败: $soPath", e)
        } catch (e: Exception) {
            loadError = "JNI 加载异常: ${e.message}"
            Log.e(TAG, "加载 SO 异常: $soPath", e)
        }
    }

    /**
     * 校验 ELF 文件头，检查魔数和架构是否匹配当前设备。
     * 校验失败不阻止加载（仅警告），因为某些特殊格式可能不标准。
     */
    private fun validateElfHeader(soFile: File) {
        try {
            soFile.inputStream().use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4 ||
                    magic[0] != 0x7F.toByte() ||
                    magic[1] != 'E'.code.toByte() ||
                    magic[2] != 'L'.code.toByte() ||
                    magic[3] != 'F'.code.toByte()
                ) {
                    Log.w(TAG, "SO 文件不是有效的 ELF 格式: $soPath")
                    return
                }

                // 检查 ELF 架构（e_machine 字段在偏移 0x12 处）
                // 0xB4 = EM_AARCH64 (arm64), 0x28 = EM_ARM (arm32), 0x3E = EM_386 (x86)
                if (fis.skip(0x0EL) != 0x0EL) return // 0x12 - 4 = 0x0E
                val eMachine = ByteArray(2)
                if (fis.read(eMachine) != 2) return

                val machine = (eMachine[0].toInt() and 0xFF) or ((eMachine[1].toInt() and 0xFF) shl 8)
                val currentArch = when (Build.SUPPORTED_ABIS.firstOrNull()) {
                    "arm64-v8a" -> 0xB4
                    "armeabi-v7a" -> 0x28
                    "x86_64" -> 0x3E
                    "x86" -> 0x03
                    else -> 0
                }
                if (currentArch != 0 && machine != currentArch) {
                    loadError = "SO 架构不匹配: 文件=0x${machine.toString(16)}, 设备=0x${currentArch.toString(16)} (${Build.SUPPORTED_ABIS.firstOrNull()})"
                    Log.e(TAG, loadError!!)
                }
            }
        } catch (e: Exception) {
            // ELF 检查失败不阻止加载（可能是特殊格式），仅警告
            Log.w(TAG, "ELF 格式校验跳过: ${e.message}")
        }
    }

    /**
     * 获取加载失败的错误信息（用于 UI 展示）。
     * 如果加载成功则返回 null。
     */
    fun getLoadError(): String? = loadError

    override fun isReady(): Boolean = isLoaded

    external fun startNativeServer(host: String, port: Int)
    external fun nativeCallApi(method: String, paramsJson: String): String
    external fun analyzeAudioFile(path: String): String

    override fun startServer(context: Context, port: Int) {
        if (!isLoaded) {
            Log.w(TAG, "JNI 未加载，跳过 startServer: $id")
            return
        }
        try {
            startNativeServer("127.0.0.1", port)
        } catch (e: Throwable) {
            // 捕获 Throwable 以处理 JNI 崩溃（如 SIGSEGV 转换的 Error）
            isLoaded = false
            loadError = "JNI 服务启动崩溃: ${e.message}"
            Log.e(TAG, "startNativeServer 崩溃", e)
        }
    }

    override fun stopServer() {}

    override fun callApi(method: String, params: Map<String, String>): String {
        if (!isLoaded) return """{"code": 500, "msg": "JNI not loaded: ${loadError ?: "unknown"}"}"""
        val json = com.google.gson.Gson().toJson(params)
        return try {
            nativeCallApi(method, json)
        } catch (e: Throwable) {
            // 捕获 Throwable 以处理 JNI 崩溃
            isLoaded = false
            loadError = "JNI 调用崩溃: ${e.message}"
            Log.e(TAG, "nativeCallApi 崩溃: $method", e)
            """{"code": 500, "msg": "JNI call crashed: ${e.message}"}"""
        }
    }

    override fun analyzeAudio(path: String): String {
        if (!isLoaded) return """{"code": 500, "msg": "JNI not loaded: ${loadError ?: "unknown"}"}"""
        return try {
            analyzeAudioFile(path)
        } catch (e: Throwable) {
            isLoaded = false
            loadError = "JNI 分析崩溃: ${e.message}"
            Log.e(TAG, "analyzeAudioFile 崩溃", e)
            """{"code": 500, "msg": "JNI analyze crashed: ${e.message}"}"""
        }
    }

    companion object {
        private const val TAG = "JniProvider"
    }
}
