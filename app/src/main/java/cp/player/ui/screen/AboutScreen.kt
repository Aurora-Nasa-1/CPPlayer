package cp.player.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.BuildConfig
import cp.player.R
import cp.player.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关于页面（内联 Composable，嵌入 SettingsScreen 使用）。
 *
 * 展示项目信息、版本、维护者、贡献者，支持手动/自动检查更新。
 */
@Composable
fun AboutScreenInline() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 更新检查状态
    var isChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<AppUpdateChecker.UpdateResult?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // 自动异步检查更新
    LaunchedEffect(Unit) {
        scope.launch {
            isChecking = true
            checkError = null
            val result = withContext(Dispatchers.IO) {
                try {
                    AppUpdateChecker.checkUpdate()
                } catch (e: Exception) {
                    null
                }
            }
            updateResult = result
            isChecking = false
            if (result != null) showUpdateDialog = true
        }
    }

    val shapes = ListItemDefaults.shapes()

    // 版本信息区
    SettingsSection(title = stringResource(R.string.about_version_info)) {
        // 应用版本
        ExpressiveClickItem(
            title = stringResource(R.string.current_version),
            subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            icon = { MonetIcon(Icons.Default.Info, Color(0xFFE3F2FD), Color(0xFF1565C0)) },
            onClick = {
                copyToClipboard(context, "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            },
            shapes = ListItemDefaults.segmentedShapes(0, 3)
        )

        // Git SHA
        ExpressiveClickItem(
            title = stringResource(R.string.commit_hash),
            subtitle = BuildConfig.GIT_SHA,
            icon = { MonetIcon(Icons.Default.Code, Color(0xFFF3E5F5), Color(0xFF7B1FA2)) },
            onClick = { copyToClipboard(context, BuildConfig.GIT_SHA) },
            shapes = ListItemDefaults.segmentedShapes(1, 3)
        )

        // 检查更新
        ExpressiveClickItem(
            title = stringResource(R.string.check_for_updates),
            subtitle = when {
                isChecking -> stringResource(R.string.checking_update)
                checkError != null -> checkError
                updateResult != null -> stringResource(R.string.update_available) + ": v${updateResult!!.versionName}"
                else -> stringResource(R.string.already_latest)
            },
            icon = {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    MonetIcon(Icons.Default.SystemUpdate, Color(0xFFE8F5E9), Color(0xFF2E7D32))
                }
            },
            onClick = {
                if (!isChecking) {
                    scope.launch {
                        isChecking = true
                        checkError = null
                        val result = withContext(Dispatchers.IO) {
                            try {
                                AppUpdateChecker.checkUpdate()
                            } catch (e: Exception) {
                                checkError = e.message
                                null
                            }
                        }
                        updateResult = result
                        isChecking = false
                        if (result != null) showUpdateDialog = true
                        else if (checkError == null) {
                            Toast.makeText(context, context.getString(R.string.already_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            shapes = ListItemDefaults.segmentedShapes(2, 3)
        )
    }

    // 项目信息
    SettingsSection(title = stringResource(R.string.about_project)) {
        // GitHub 项目地址
        ExpressiveClickItem(
            title = stringResource(R.string.project_address),
            subtitle = "github.com/Aurora-Nasa-1/CPPlayer",
            icon = { MonetIcon(Icons.Default.Link, Color(0xFFE0F2F1), Color(0xFF00695C)) },
            onClick = { AppUpdateChecker.openReleasesPage(context) },
            shapes = ListItemDefaults.segmentedShapes(0, 1)
        )
    }

    // 维护者
    SettingsSection(title = stringResource(R.string.about_maintainers)) {
        // 主要维护者
        ExpressiveClickItem(
            title = "Aurora-Nasa-1",
            subtitle = stringResource(R.string.about_main_developer),
            icon = { MonetIcon(Icons.Default.Person, Color(0xFFFFF3E0), Color(0xFFEF6C00)) },
            onClick = { openUrl(context, "https://github.com/Aurora-Nasa-1") },
            shapes = ListItemDefaults.segmentedShapes(0, 2)
        )

        // QQ 群（占位）
        ExpressiveClickItem(
            title = stringResource(R.string.qq_group),
            subtitle = stringResource(R.string.qq_group_placeholder),
            icon = { MonetIcon(Icons.Default.Group, Color(0xFFE3F2FD), Color(0xFF1565C0)) },
            onClick = {
                // TODO: 用户输入 QQ 群链接后替换
                Toast.makeText(context, context.getString(R.string.qq_group_link_pending), Toast.LENGTH_SHORT).show()
            },
            shapes = ListItemDefaults.segmentedShapes(1, 2)
        )
    }

    // 贡献者
    SettingsSection(title = stringResource(R.string.about_contributors)) {
        val contributors = listOf(
            Contributor("Aurora-Nasa-1", stringResource(R.string.creator_and_main_maintainer), "https://github.com/Aurora-Nasa-1")
        )

        contributors.forEachIndexed { index, contributor ->
            ExpressiveClickItem(
                title = contributor.name,
                subtitle = contributor.role,
                icon = { MonetIcon(Icons.Default.Engineering, Color(0xFFFCE4EC), Color(0xFFC2185B)) },
                onClick = { openUrl(context, contributor.githubUrl) },
                shapes = ListItemDefaults.segmentedShapes(index, contributors.size)
            )
        }
    }

    // 更新对话框
    if (showUpdateDialog && updateResult != null) {
        UpdateAvailableDialog(
            result = updateResult!!,
            onDismiss = { showUpdateDialog = false },
            onDownload = {
                updateResult!!.downloadUrl?.let { url ->
                    AppUpdateChecker.openDownloadPage(context, url)
                }
                showUpdateDialog = false
            }
        )
    }
}

/**
 * 发现新版本对话框。
 */
@Composable
private fun UpdateAvailableDialog(
    result: AppUpdateChecker.UpdateResult,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "v${result.versionName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (result.publishedAt != null) {
                    Text(
                        text = result.publishedAt.take(10), // 只取日期部分
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!result.changelog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.update_changelog),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.changelog.take(500), // 限制长度
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDownload) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 贡献者数据类。
 */
private data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String
)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CPPlayer", text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (_: Exception) {}
}
