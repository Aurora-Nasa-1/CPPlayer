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
    private const val RELEASES_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
    private const val RELEASES_PAGE = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val PRE_RELEASE_REGEX = Regex("^([a-zA-Z]*)(\\d*)$")

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
                val releases = gson.fromJson(body, Array<GitHubRelease>::class.java)
                if (releases.isNullOrEmpty()) return null

                val latest = releases.first()

                // 解析 tag_name 为版本号（去掉前缀 v）
                val remoteVersionName = latest.tagName.removePrefix("v")

                // 比较版本：优先用版本名比较（因为 versionCode 可能不连续）
                if (compareVersions(BuildConfig.VERSION_NAME, remoteVersionName) >= 0) {
                    Log.d(TAG, "Already up to date: ${BuildConfig.VERSION_NAME}")
                    return null
                }

                // 从当前版本到最新版本之间的所有 release 更新日志
                val currentVersion = BuildConfig.VERSION_NAME
                val changelogBuilder = StringBuilder()
                for (release in releases) {
                    val ver = release.tagName.removePrefix("v")
                    // 只收集比当前版本新的 release
                    if (compareVersions(ver, currentVersion) <= 0) break
                    if (!release.body.isNullOrBlank()) {
                        changelogBuilder.appendLine("### ${release.tagName}")
                        changelogBuilder.appendLine()
                        changelogBuilder.appendLine(release.body.trim())
                        changelogBuilder.appendLine()
                    }
                }

                // 从最新 release assets 中找 APK 下载链接
                val apkAsset = latest.assets?.find { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                        (asset.contentType == "application/vnd.android.package-archive" ||
                         asset.contentType == null)
                }
                val downloadUrl = apkAsset?.browserDownloadUrl ?: latest.htmlUrl

                Log.i(TAG, "Update available: ${BuildConfig.VERSION_NAME} → $remoteVersionName")
                UpdateResult(
                    versionName = remoteVersionName,
                    versionCode = 0,
                    downloadUrl = downloadUrl,
                    changelog = changelogBuilder.toString().trimEnd().ifBlank { latest.body },
                    publishedAt = latest.publishedAt,
                    releaseUrl = latest.htmlUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update", e)
            null
        }
    }

    /**
     * 比较两个语义化版本号（支持 pre-release 后缀，如 `1.0.0-beta3`）。
     *
     * 规则：
     * 1. 先去掉 `v` 前缀，再按 `.` 分段比较数字部分
     * 2. 数字部分相同时，有 pre-release 后缀的版本 **小于** 没有后缀的（如 `1.0.0-beta1` < `1.0.0`）
     * 3. 都有后缀时，提取共同前缀字母后的数字部分比较（`beta2` < `beta3` < `beta123`）
     *
     * @return 负数 = v1 < v2，0 = 相等，正数 = v1 > v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        // 分离数字部分和 pre-release 后缀，同时去掉 v 前缀
        fun parseVersion(version: String): Pair<List<Int>, String?> {
            val v = version.removePrefix("v")
            val dashIndex = v.indexOf('-')
            val numericPart = if (dashIndex >= 0) v.substring(0, dashIndex) else v
            val preRelease = if (dashIndex >= 0) v.substring(dashIndex + 1) else null
            val parts = numericPart.split(".").map { it.toIntOrNull() ?: 0 }
            return parts to preRelease
        }

        // 比较 pre-release 后缀，支持数字部分按数值比较（如 beta2 < beta123）
        fun comparePreRelease(pre1: String, pre2: String): Int {
            // 提取共同字母前缀后的数字部分
            val match1 = PRE_RELEASE_REGEX.matchEntire(pre1)
            val match2 = PRE_RELEASE_REGEX.matchEntire(pre2)

            if (match1 != null && match2 != null) {
                val prefix1 = match1.groupValues[1]
                val prefix2 = match2.groupValues[1]
                // 先比较字母前缀
                val prefixCmp = prefix1.compareTo(prefix2)
                if (prefixCmp != 0) return prefixCmp
                // 字母前缀相同，按数值比较数字部分
                val num1 = match1.groupValues[2].toIntOrNull() ?: 0
                val num2 = match2.groupValues[2].toIntOrNull() ?: 0
                return num1 - num2
            }
            // 无法解析时回退到字符串比较
            return pre1.compareTo(pre2)
        }

        val (parts1, pre1) = parseVersion(v1)
        val (parts2, pre2) = parseVersion(v2)

        // 比较数字部分
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }

        // 数字部分相同，比较 pre-release 后缀
        // 有 pre-release < 无 pre-release（如 1.0.0-beta1 < 1.0.0）
        return when {
            pre1 != null && pre2 != null -> comparePreRelease(pre1, pre2)
            pre1 != null && pre2 == null -> -1
            pre1 == null && pre2 != null -> 1
            else -> 0
        }
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
