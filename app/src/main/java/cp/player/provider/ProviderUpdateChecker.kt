package cp.player.provider

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import cp.player.util.VersionUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 提供者模块更新检查工具。
 *
 * 通过 HTTP GET 请求 updateUrl 获取远程版本信息，
 * 与本地版本比较判断是否有可用更新。
 *
 * ### 远程 updateUrl 响应格式
 * ```json
 * {
 *   "version": "1.1.0",
 *   "downloadUrl": "https://example.com/provider-v1.1.0.zip",
 *   "changelog": "修复了xxx问题，新增yyy功能"
 * }
 * ```
 *
 * - `version`（必填）：最新版本号，语义化版本格式
 * - `downloadUrl`（必填）：新版本 zip 下载地址
 * - `changelog`（可选）：更新日志
 */
object ProviderUpdateChecker {
    private const val TAG = "ProviderUpdateChecker"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 远程更新信息。
     */
    data class UpdateInfo(
        /** 最新版本号 */
        val version: String,
        /** 下载地址 */
        @SerializedName("downloadUrl")
        val downloadUrl: String,
        /** 更新日志（可选） */
        val changelog: String? = null
    )

    /**
     * 检查更新。
     *
     * @param updateUrl 远程 updateUrl 端点
     * @param localVersion 本地当前版本号
     * @return 如果有可用更新返回 [UpdateInfo]，否则返回 null
     */
    fun checkUpdate(updateUrl: String, localVersion: String): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(updateUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Update check failed: HTTP ${response.code}")
                    return null
                }
                val body = response.body.string()
                val info = gson.fromJson(body, UpdateInfo::class.java)
                if (info.version.isBlank() || info.downloadUrl.isBlank()) {
                    Log.w(TAG, "Invalid update info: missing version or downloadUrl")
                    return null
                }
                if (VersionUtils.compareVersions(localVersion, info.version) < 0) {
                    Log.i(TAG, "Update available: $localVersion → ${info.version}")
                    info
                } else {
                    Log.d(TAG, "Already up to date: $localVersion")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update from $updateUrl", e)
            null
        }
    }

}
