package cp.player.provider

data class ModuleManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: String, // "jni", "binary", "http"
    val entryPoint: String,
    val apiMap: Map<String, String>? = null,
    /** 检查更新 URL（可选），指向返回最新版本信息的 JSON 端点 */
    val updateUrl: String? = null
)
