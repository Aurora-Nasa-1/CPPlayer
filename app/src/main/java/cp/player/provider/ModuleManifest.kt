package cp.player.provider

data class ModuleManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: String, // "jni", "binary", "http"
    val entryPoint: String,
    val apiMap: Map<String, String>? = null,
    /** 检查更新 URL（可选），指向返回最新版本信息的 JSON 端点 */
    val updateUrl: String? = null,
    /**
     * 支持的 ABI 列表（可选）。
     * 用于声明模块支持的 CPU 架构，如 ["arm64-v8a", "armeabi-v7a"]。
     * 当 zip 包含 per-ABI 目录结构（lib/{abi}/）时，
     * 加载器会自动根据设备 ABI 选择正确的 native library。
     *
     * 为 null 或空时，表示单架构模块（向后兼容旧格式）。
     */
    val supportedAbis: List<String>? = null
)
