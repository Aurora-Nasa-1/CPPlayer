package cp.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import cp.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 应用自更新检查工具。
 *
 * 通过 GitHub Releases API 获取最新 release 信息，
 * 与当前版本比较判断是否有可用更新。
 *
 * ### GitHub Release 格式
 * - `tag_name`：版本标签，格式 `v1.2.3`
 * - `body`：更新日志（Markdown）
 * - `assets`：附件列表，包含 APK 下载链接
 * - `html_url`：release 页面链接
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val REPO_OWNER = "Aurora-Nasa-1"
    private const val REPO_NAME = "CPPlayer"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 更新检查结果。
     */
    data class UpdateResult(
        /** 最新版本名（如 "1.2.3"） */
        val versionName: String,
        /** 最新版本号（数字） */
        val versionCode: Int,
        /** APK 下载地址 */
        val downloadUrl: String?,
        /** 更新日志 */
        val changelog: String?,
        /** 发布时间 */
        val publishedAt: String?,
        /** Release 页面链接 */
        val releaseUrl: String
    )

    /**
     * GitHub Release API 响应（部分字段）。
     */
    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("name") val name: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("html_url") val htmlUrl: String,
        @SerializedName("published_at") val publishedAt: String?,
        @SerializedName("assets") val assets: List<GitHubAsset>?
    )

    private data class GitHubAsset(
        @SerializedName("name") val name: String,
        @SerializedName("browser_download_url") val browserDownloadUrl: String,
        @SerializedName("content_type") val contentType: String?
    )

    /**
     * 检查更新（阻塞调用，需在 IO 线程执行）。
     *
     * @return 如果有可用更新返回 [UpdateResult]，否则返回 null
     */
    fun checkUpdate(): UpdateResult? {
        return try {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Update check failed: HTTP ${response.code}")
                    return null
                }
                val body = response.body.string()
                val release = gson.fromJson(body, GitHubRelease::class.java)

                // 解析 tag_name 为版本号（去掉前缀 v）
                val remoteVersionName = release.tagName.removePrefix("v")

                // 从 release assets 中找 APK 下载链接
                val apkAsset = release.assets?.find { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                        (asset.contentType == "application/vnd.android.package-archive" ||
                         asset.contentType == null) // 有些 release 没设 content_type
                }

                // 如果没有 APK asset，尝试用 release 页面兜底
                val downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl

                // 比较版本：优先用版本名比较（因为 versionCode 可能不连续）
                if (compareVersions(BuildConfig.VERSION_NAME, remoteVersionName) < 0) {
                    Log.i(TAG, "Update available: ${BuildConfig.VERSION_NAME} → $remoteVersionName")
                    UpdateResult(
                        versionName = remoteVersionName,
                        versionCode = 0, // 无法从 GitHub 获取 versionCode
                        downloadUrl = downloadUrl,
                        changelog = release.body,
                        publishedAt = release.publishedAt,
                        releaseUrl = release.htmlUrl
                    )
                } else {
                    Log.d(TAG, "Already up to date: ${BuildConfig.VERSION_NAME}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update", e)
            null
        }
    }

    /**
     * 比较两个语义化版本号。
     *
     * @return 负数 = v1 < v2，0 = 相等，正数 = v1 > v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    /**
     * 打开浏览器跳转到下载页面。
     */
    fun openDownloadPage(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download page: $url", e)
        }
    }

    /**
     * 打开 GitHub Release 页面。
     */
    fun openReleasesPage(context: Context) {
        openDownloadPage(context, RELEASES_PAGE)
    }
}
