package cp.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R
import cp.player.ui.component.AppScaffold
import cp.player.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日志查看器独立界面（NavHost 路由用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBackPressed: () -> Unit) {
    AppScaffold(
        title = stringResource(R.string.app_logs),
        onBackPressed = onBackPressed,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            LogViewerContent(scrollable = true)
        }
    }
}

/**
 * 日志查看器内嵌版本（SettingsScreen 子导航用）。
 */
@Composable
fun LogViewerInline() {
    LogViewerContent(scrollable = false)
}

@Composable
private fun LogViewerContent(scrollable: Boolean) {
    val logs by LogManager.logs.collectAsState()
    var systemLogs by remember { mutableStateOf("") }
    var showSystemLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(showSystemLogs) {
        if (showSystemLogs) {
            systemLogs = "Loading system logs..."
            withContext(Dispatchers.IO) {
                systemLogs = try {
                    val process = Runtime.getRuntime().exec("logcat -d -v time")
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "Failed to fetch system logs: ${e.message}"
                }
            }
        }
    }

    Column(modifier = if (scrollable) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        // 顶部操作栏 — M3 Expressive 风格
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日志源切换
                FilterChip(
                    selected = !showSystemLogs,
                    onClick = { showSystemLogs = false },
                    label = { Text("应用日志", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = showSystemLogs,
                    onClick = { showSystemLogs = true },
                    label = { Text("系统日志", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.Terminal, null, Modifier.size(16.dp)) }
                )
                Spacer(Modifier.weight(1f))
                // 分享
                IconButton(onClick = {
                    val text = if (showSystemLogs) systemLogs
                    else logs.joinToString("\n") { "${it.time} [${it.level}] ${it.message}" }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                }) {
                    Icon(Icons.Default.Share, "分享", tint = MaterialTheme.colorScheme.primary)
                }
                // 清除
                IconButton(onClick = { LogManager.clear() }) {
                    Icon(Icons.Default.DeleteOutline, "清除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }

        // 日志内容
        if (showSystemLogs) {
            // 系统日志 — 终端风格
            val scrollState = rememberScrollState()
            SelectionContainer {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = systemLogs,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (scrollable) Modifier.heightIn(max = 600.dp).verticalScroll(scrollState) else Modifier)
                            .padding(12.dp)
                    )
                }
            }
        } else {
            // 应用日志
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Article, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("暂无应用日志", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                Text(
                    "共 ${logs.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (scrollable) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = logs,
                            key = { index, entry -> "${entry.time}_${index}" }
                        ) { _, entry ->
                            LogEntryCard(entry)
                        }
                    }
                } else {
                    // 内嵌模式用 Column 滚动
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        logs.take(200).forEach { entry ->
                            LogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogManager.LogEntry) {
    val levelColor = when (entry.level) {
        "E" -> Color(0xFFF44336)
        "W" -> Color(0xFFFF9800)
        "I" -> Color(0xFF2196F3)
        "D" -> Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.outline
    }
    val bgColor = when (entry.level) {
        "E" -> Color(0xFFF44336).copy(alpha = 0.06f)
        "W" -> Color(0xFFFF9800).copy(alpha = 0.04f)
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 级别标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = levelColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        entry.level,
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.time,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (entry.level == "E") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
            if (entry.throwable != null) {
                Text(
                    entry.throwable,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFF44336).copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
