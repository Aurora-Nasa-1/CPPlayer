package cp.player.provider

import android.content.Context

/**
 * 音乐后端 Provider 接口。
 *
 * 每个音乐数据源（如 NeteaseCloudMusicApi、自建后端等）都实现此接口。
 * CPPlayer 通过此接口与不同的音乐服务通信，无需关心底层实现。
 *
 * ### 实现类型
 * - [HttpProvider] — 连接到已有的 HTTP API 服务
 * - [BinaryProvider] — 启动独立可执行文件作为后端
 * - [JniProvider] — 通过 JNI 调用本地库
 *
 * ### 账号隔离
 * 每个 Provider 维护独立的账号体系。用户的 cookie、歌单、推荐等数据
 * 都与特定 Provider 绑定。切换 Provider 时，会自动切换到该 Provider 的账号。
 *
 * @see ProviderManager 管理 Provider 生命周期和切换
 * @see ModuleManager 从模块包加载 Provider
 * @see cp.player.api.MusicApiService 统一的 API 调用入口
 */
interface BackendProvider {
    /** Provider 唯一标识符（如 "netease"、"my-custom"） */
    val id: String

    /** Provider 显示名称（如 "NeteaseCloudMusicApi"） */
    val name: String

    /** Provider 版本号（语义化版本） */
    val version: String

    /** Provider 实现类型 */
    val type: ProviderType

    /**
     * API 方法名映射表。
     *
     * key = CPPlayer 内部标准方法名（如 "cloudsearch"）
     * value = Provider 实际端点名（如 "search"）
     *
     * - 如果映射为 "unsupported"，该功能会被标记为不支持
     * - 如果为 null，CPPlayer 会直接使用内部方法名
     */
    val apiMap: Map<String, String>?

    /** 检查更新 URL（可选），指向返回最新版本信息的 JSON 端点 */
    val updateUrl: String?

    /**
     * 启动 Provider 服务。
     *
     * 对于 BinaryProvider，会启动可执行文件；
     * 对于 JniProvider，会启动本地服务；
     * 对于 HttpProvider，此方法为空操作（服务由外部启动）。
     */
    fun startServer(context: Context, port: Int)

    /**
     * 停止 Provider 服务。
     */
    fun stopServer()

    /**
     * 调用 Provider API。
     *
     * @param method 已通过 [apiMap] 映射后的实际方法名
     * @param params 请求参数（包含 cookie 等认证信息）
     * @return JSON 格式的响应字符串
     */
    fun callApi(method: String, params: Map<String, String>): String

    /**
     * 分析音频文件（可选）。
     *
     * @param path 音频文件路径
     * @return JSON 格式的分析结果
     */
    fun analyzeAudio(path: String): String
}

/**
 * Provider 实现类型枚举。
 */
enum class ProviderType {
    /** JNI 本地库（C/C++/Rust 编写的 .so 文件） */
    JNI,
    /** 独立可执行文件（通过 HTTP 与 CPPlayer 通信） */
    BINARY,
    /** WebSocket 通信（预留） */
    WEBSOCKET,
    /** HTTP API 服务（如 NeteaseCloudMusicApi） */
    HTTP
}
