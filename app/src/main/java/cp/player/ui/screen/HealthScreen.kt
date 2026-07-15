package cp.player.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R
import cp.player.api.MusicApiMethod
import cp.player.monitor.HealthMonitor
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.StyledModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一的健康状态与调试界面。
 *
 * 六个 Tab：
 * 1. 概览 — 每个 Provider 的健康评分和统计
 * 2. 方法统计 — 按 API 方法分组的调用统计
 * 3. 调用日志 — 最近的 API 调用记录时间线
 * 4. API 测试 — 手动测试 API 方法
 * 5. apiMap — 提供商 API 映射可视化
 * 6. Provider 状态 — 已加载的 Provider 信息
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthScreen(
    onBackPressed: () -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }

    AppScaffold(
        title = stringResource(R.string.health_and_debug),
        onBackPressed = onBackPressed,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HealthTabRow(currentTab) { currentTab = it }
            HealthTabContent(currentTab, scrollable = true)
        }
    }
}

/**
 * 内嵌版本，用于 SettingsScreen 内部导航。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthScreenInline() {
    var currentTab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HealthTabRow(currentTab, compact = true) { currentTab = it }
        HealthTabContent(currentTab, scrollable = false)
    }
}

// ======================== 共享 Tab 基础设施 ========================

/**
 * M3 Expressive 风格的水平滚动导航芯片行。
 * 替代传统的 TabRow，更符合 Expressive 设计语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthTabRow(currentTab: Int, compact: Boolean = false, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        stringResource(R.string.tab_overview) to Icons.Default.Dashboard,
        stringResource(R.string.tab_methods) to Icons.Default.Analytics,
        stringResource(R.string.tab_logs) to Icons.AutoMirrored.Filled.List,
        stringResource(R.string.tab_test) to Icons.AutoMirrored.Filled.Send,
        "apiMap" to Icons.Default.Map,
        stringResource(R.string.tab_status) to Icons.Default.Info
    )
    val chipSize = if (compact) 32.dp else 36.dp
    val iconSize = if (compact) 16.dp else 18.dp

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val selected = currentTab == index
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            label,
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(icon, null, Modifier.size(iconSize)) }
                    } else {
                        { Icon(icon, null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    modifier = Modifier.height(chipSize),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = selected
                    )
                )
            }
        }
    }
}

@Composable
private fun HealthTabContent(currentTab: Int, scrollable: Boolean) {
    when (currentTab) {
        0 -> HealthOverviewTab(scrollable)
        1 -> HealthMethodStatsTab(scrollable)
        2 -> HealthCallLogTab(scrollable)
        3 -> ApiTestTab(scrollable)
        4 -> ApiMapTab(scrollable)
        5 -> ProviderStatusTab(scrollable)
    }
}

// ======================== Tab 1: 概览 ========================

@Composable
private fun HealthOverviewTab(scrollable: Boolean) {
    val records by HealthMonitor.recordsFlow.collectAsState()
    val allStats = remember(records) { HealthMonitor.getAllStats() }
    val providers by ModuleManager.providersFlow.collectAsState()

    val columnModifier = if (scrollable) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (allStats.isEmpty() && records.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.HealthAndSafety, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.no_monitoring_data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.health_auto_record), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            return@Column
        }

        // ── 全局概览卡片 ──
        if (records.isNotEmpty()) {
            GlobalStatsCard(records)
        }

        // ── Provider 健康卡片 ──
        Text(stringResource(R.string.provider_health_status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        val providerMap = providers.associateBy { it.id }
        val statsToShow = (allStats.keys + providers.map { it.id }).distinct()
        statsToShow.forEach { providerId ->
            ProviderHealthCard(
                providerName = providerMap[providerId]?.name ?: providerId,
                providerType = providerMap[providerId]?.type?.name ?: "",
                stats = allStats[providerId]
            )
        }
    }
}

@Composable
private fun GlobalStatsCard(records: List<HealthMonitor.ApiCallRecord>) {
    val totalCalls = records.size
    val totalSuccess = records.count { it.success }
    val totalFail = totalCalls - totalSuccess
    val totalFallbacks = records.count { it.wasFallback }
    val totalWarnings = records.count { it.responseWarnings.isNotEmpty() }
    val globalSuccessRate = if (totalCalls > 0) totalSuccess * 100f / totalCalls else 0f
    val avgResponse = if (totalCalls > 0) records.sumOf { it.durationMs } / totalCalls else 0L

    val accentColor = when {
        globalSuccessRate >= 90f -> Color(0xFF4CAF50)
        globalSuccessRate >= 70f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // 标题行 + 大分数
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.global_health), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.calls_avg_time, totalCalls, avgResponse), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                // 大号评分
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${String.format("%.0f", globalSuccessRate)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accentColor)
                        Text("%", style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))

            // 指标网格
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricChip(stringResource(R.string.metric_success), "$totalSuccess", Color(0xFF4CAF50), Icons.Default.CheckCircle)
                MetricChip(stringResource(R.string.metric_fail), "$totalFail", Color(0xFFF44336), Icons.Default.Error)
                MetricChip(stringResource(R.string.metric_fallback), "$totalFallbacks", Color(0xFFFF9800), Icons.Default.SwapHoriz)
                MetricChip(stringResource(R.string.metric_warning), "$totalWarnings", Color(0xFFFF9800), Icons.Default.Warning)
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: Color, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = color)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ProviderHealthCard(providerName: String, providerType: String, stats: HealthMonitor.ProviderHealthStats?) {
    val hasData = stats != null && stats.totalCalls > 0
    val score = stats?.healthScore ?: 0f
    val scoreColor = when {
        score >= 0.8f -> Color(0xFF4CAF50)
        score >= 0.5f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val borderColor = if (hasData) scoreColor.copy(alpha = 0.3f) else Color.Transparent

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(width = if (hasData) 1.5.dp else 1.dp/*, brush = SolidColor(borderColor)*/)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 头部：名称 + 评分
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态指示灯
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (hasData) scoreColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(providerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (providerType.isNotEmpty()) {
                            Text(providerType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (hasData) {
                    // 评分徽章
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scoreColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${(score * 100).toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scoreColor)
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.score_unit), style = MaterialTheme.typography.labelSmall, color = scoreColor.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (!hasData) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.no_call_records), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            } else {
                Spacer(Modifier.height(16.dp))

                // 进度条式成功率
                val successRate = stats.successCount.toFloat() / stats.totalCalls
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.success_rate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("${String.format("%.1f", successRate * 100)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = scoreColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { successRate },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = scoreColor,
                        trackColor = scoreColor.copy(alpha = 0.12f),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 指标行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat(stringResource(R.string.metric_calls), "${stats.totalCalls}")
                    MiniStat(stringResource(R.string.metric_success), "${stats.successCount}", Color(0xFF4CAF50))
                    MiniStat(stringResource(R.string.metric_fail), "${stats.failCount}", if (stats.failCount > 0) Color(0xFFF44336) else MaterialTheme.colorScheme.outline)
                    MiniStat(stringResource(R.string.metric_fallback), "${stats.fallbackCount}", if (stats.fallbackCount > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.outline)
                }

                // 响应时间
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.avg_response), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("${stats.avgResponseMs}ms  ·  P95: ${stats.p95ResponseMs}ms", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }

                // 警告
                if (stats.warningCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF9800).copy(alpha = 0.08f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.compatibility_warnings_count, stats.warningCount), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                            if (stats.lastWarning != null) {
                                Text(" · ${formatWarning(stats.lastWarning)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800).copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // 最近错误
                if (stats.lastError != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(stats.lastError.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ======================== Tab 2: 方法统计 ========================

@Composable
private fun HealthMethodStatsTab(scrollable: Boolean) {
    val records by HealthMonitor.recordsFlow.collectAsState()
    val methodStats = remember(records) { HealthMonitor.getStatsByMethod() }

    val columnModifier = if (scrollable) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().padding(16.dp)
    }
    Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (methodStats.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_method_stats), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
            return@Column
        }
        Text(stringResource(R.string.method_stats_desc), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        methodStats.values.sortedByDescending { it.totalCalls }.forEach { MethodStatRow(it) }
    }
}

@Composable
private fun MethodStatRow(stat: HealthMonitor.MethodStats) {
    var expanded by remember { mutableStateOf(false) }
    val records by HealthMonitor.recordsFlow.collectAsState()
    val successRate = if (stat.totalCalls > 0) stat.successCount * 100f / stat.totalCalls else 0f

    val containerColor = when {
        stat.warningCount > 0 -> Color(0xFFFF9800).copy(alpha = 0.08f)
        successRate >= 90f -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        successRate >= 50f -> Color(0xFFFFC107).copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }

    Column {
        Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(8.dp), color = containerColor, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stat.method, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    Text(
                        stringResource(R.string.method_calls_success_rate, stat.totalCalls, String.format("%.0f", successRate), stat.avgResponseMs),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
                    )
                }
                if (stat.failCount > 0) Text(stringResource(R.string.fail_count, stat.failCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (stat.warningCount > 0) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.warning_count, stat.warningCount), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800)) }
                if (stat.fallbackCount > 0) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.fallback_count, stat.fallbackCount), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800)) }
                Spacer(Modifier.width(8.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        AnimatedVisibility(visible = expanded) {
            val methodRecords = remember(records, expanded) {
                if (expanded) records.filter { it.method == stat.method }.takeLast(10).reversed() else emptyList()
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // 警告类型分布
                if (stat.warningTypes.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF9800).copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(stringResource(R.string.compatibility_warning_dist), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9800))
                            Spacer(Modifier.height(4.dp))
                            stat.warningTypes.forEach { (warning, count) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatWarning(warning), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                                    Text("×$count", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800))
                                }
                            }
                        }
                    }
                }
                methodRecords.forEach { CallRecordRow(it, compact = true) }
            }
        }
    }
}

// ======================== Tab 3: 调用日志 ========================

@Composable
private fun HealthCallLogTab(scrollable: Boolean) {
    val records by HealthMonitor.recordsFlow.collectAsState()
    var filterFailuresOnly by remember { mutableStateOf(false) }
    var filterWarningsOnly by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, filterFailuresOnly, filterWarningsOnly) {
        HealthMonitor.getRecentRecords(limit = 200, onlyFailures = filterFailuresOnly, onlyWarnings = filterWarningsOnly)
    }

    Column(modifier = if (scrollable) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxWidth().padding(16.dp)) {
        // M3 Expressive: 使用 Surface 包裹过滤器区域
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterFailuresOnly,
                    onClick = { filterFailuresOnly = !filterFailuresOnly; filterWarningsOnly = false },
                    label = { Text(stringResource(R.string.filter_failures_only), fontWeight = FontWeight.Medium) },
                    leadingIcon = if (filterFailuresOnly) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
                FilterChip(
                    selected = filterWarningsOnly,
                    onClick = { filterWarningsOnly = !filterWarningsOnly; filterFailuresOnly = false },
                    label = { Text(stringResource(R.string.filter_warnings_only), fontWeight = FontWeight.Medium) },
                    leadingIcon = if (filterWarningsOnly) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFFE65100)
                    )
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { HealthMonitor.clearRecords() }) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.clear_records), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filteredRecords.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_call_records), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            Text(stringResource(R.string.total_records, filteredRecords.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = if (scrollable) Modifier.fillMaxWidth().verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filteredRecords.forEach { CallRecordRow(it, compact = false) }
            }
        }
    }
}

@Composable
private fun CallRecordRow(record: HealthMonitor.ApiCallRecord, compact: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    val bgColor = when {
        record.responseWarnings.isNotEmpty() -> Color(0xFFFF9800).copy(alpha = 0.06f)
        !record.success && record.wasFallback -> Color(0xFFFF9800).copy(alpha = 0.08f)
        !record.success -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        record.wasFallback -> Color(0xFFFF9800).copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    val resultIcon = when {
        record.responseWarnings.isNotEmpty() && record.success -> "⚠️"
        record.success && !record.wasFallback -> "✅"
        record.success && record.wasFallback -> "↩️"
        !record.success && record.wasFallback -> "⚠️"
        else -> "❌"
    }

    val timeStr = remember(record.timestamp) {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))
    }

    Surface(
        onClick = { if (!compact) expanded = !expanded },
        shape = RoundedCornerShape(6.dp), color = bgColor, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(resultIcon, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(timeStr, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(8.dp))
                Text(record.providerId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(record.method, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                if (record.responseWarnings.isNotEmpty()) {
                    Text("${record.responseWarnings.size}⚠", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
                    Spacer(Modifier.width(4.dp))
                }
                Text("${record.durationMs}ms", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
            }
            if (!compact && expanded) {
                Spacer(Modifier.height(6.dp))
                // 详情用卡片包裹
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (record.responseCode != null) {
                            DetailRow(stringResource(R.string.response_code), "${record.responseCode}", if (record.responseCode == 200 || record.responseCode == 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                        }
                        if (record.errorCode != null) {
                            DetailRow(stringResource(R.string.error_code), "${record.errorCode}", MaterialTheme.colorScheme.error)
                        }
                        if (record.errorMessage != null) {
                            Text(record.errorMessage.take(400), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                        if (record.responseWarnings.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Text(stringResource(R.string.compatibility_warning), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9800))
                            record.responseWarnings.forEach { w ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("• ", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                                    Column {
                                        Text(formatWarning(w), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                                        if (w == HealthMonitor.ResponseWarning.MISSING_DATA_FIELD && record.expectedField != null) {
                                            Text("  " + stringResource(R.string.expected_field, record.expectedField), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color(0xFFFF9800).copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }
                        if (record.wasFallback && record.fallbackFrom != null) {
                            DetailRow(stringResource(R.string.fallback_from), record.fallbackFrom, Color(0xFFFF9800))
                        }
                    }
                }
            }
        }
    }
}

// ======================== Tab 4: API 测试 (原 ProviderTestScreen) ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTestTab(scrollable: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allMethods = remember {
        listOf(
            "login/qr/key", "login/qr/create", "login/qr/check",
            "login", "login/cellphone", "captcha/sent", "logout",
            "register/anonimous", "login/status",
            "user/playlist", "user/detail", "user/cloud", "likelist", "like",
            "recommend/songs", "recommend/resource",
            "playlist/detail", "playlist/track/all", "playlist/tracks",
            "playlist/create", "playlist/delete", "playlist/subscribe",
            "album", "artist/detail", "artist/songs", "artist/album",
            "cloudsearch", "search/hot/detail", "search/suggest",
            "song/url/v1/302", "song/url/v1", "song/download/url/v1",
            "song/detail", "personal_fm", "playmode/intelligence/list",
            "lyric/new",
            "comment/music", "comment/playlist", "comment/album",
            "comment/floor", "comment/like", "comment",
            "pl/count", "msg/recentcontact", "msg/private",
            "msg/private/history", "msg/private/mark/read", "send/text"
        )
    }

    var selectedMethod by remember { mutableStateOf(allMethods[0]) }
    var showMethodSheet by remember { mutableStateOf(false) }
    var paramsText by remember { mutableStateOf("{\n  \"id\": \"\"\n}") }
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var responseTime by remember { mutableStateOf(0L) }

    val columnModifier = if (scrollable) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().padding(16.dp)
    }

    Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.select_api_method), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedMethod, onValueChange = {}, readOnly = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    .also { interactionSource ->
                        LaunchedEffect(interactionSource) {
                            interactionSource.interactions.collect { interaction ->
                                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showMethodSheet = true
                                }
                            }
                        }
                    }
            )
        }

        Text(stringResource(R.string.request_params_json), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = paramsText, onValueChange = { paramsText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )

        Button(
            onClick = {
                isLoading = true; responseText = ""
                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    try {
                        val params = parseJsonParams(paramsText)
                        val result = withContext(Dispatchers.IO) { ProviderManager.callApi(selectedMethod, params) }
                        responseTime = System.currentTimeMillis() - startTime
                        responseText = formatJson(result)
                    } catch (e: Exception) {
                        responseTime = System.currentTimeMillis() - startTime
                        responseText = context.getString(R.string.error_message, e.message ?: "")
                    } finally { isLoading = false }
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sending))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.send_request))
            }
        }

        if (responseText.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.response), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${responseTime}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                SelectionContainer {
                    Text(responseText, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showMethodSheet) {
        StyledModalBottomSheet(onDismissRequest = { showMethodSheet = false }) {
            Text(
                stringResource(R.string.select_api_method),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(allMethods) { method ->
                    val isSelected = method == selectedMethod
                    Surface(
                        onClick = {
                            selectedMethod = method
                            showMethodSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(method, style = MaterialTheme.typography.bodyMedium)
                                val provider = ProviderManager.currentProvider
                                val mapped = provider?.apiMap?.get(method)
                                if (mapped != null) {
                                    Text(
                                        "→ $mapped",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (mapped.equals("unsupported", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                } else if (provider?.apiMap != null) {
                                    Text(stringResource(R.string.unmapped_passthrough), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

// ======================== Tab 5: apiMap ========================

@Composable
private fun ApiMapTab(scrollable: Boolean) {
    val provider = ProviderManager.currentProvider
    val allMethods = remember {
        listOf(
            "login/qr/key", "login/qr/create", "login/qr/check", "login", "login/cellphone", "captcha/sent", "logout",
            "register/anonimous", "login/status", "user/playlist", "user/playlist/create", "user/playlist/collect",
            "user/detail", "user/cloud", "likelist", "like", "recommend/songs", "recommend/resource", "recommend/songs/dislike",
            "playlist/detail", "playlist/track/all", "playlist/tracks", "playlist/create", "playlist/delete", "playlist/subscribe",
            "album", "artist/detail", "artist/songs", "artist/album", "cloudsearch", "search/hot/detail", "search/suggest",
            "song/url/v1/302", "song/url/v1", "song/download/url/v1", "song/detail", "personal_fm", "playmode/intelligence/list",
            "lyric/new", "comment/music", "comment/playlist", "comment/album", "comment/mv", "comment/dj", "comment/video",
            "comment/floor", "comment/like", "comment", "pl/count", "msg/recentcontact", "msg/private",
            "msg/private/history", "msg/private/mark/read", "send/text"
        )
    }

    val columnModifier = if (scrollable) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().padding(16.dp)
    }

    Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (provider == null) {
            Text(stringResource(R.string.no_active_provider), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.current_provider, provider.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("ID: ${provider.id}  |  类型: ${provider.type}  |  版本: ${provider.version}", style = MaterialTheme.typography.bodySmall)
                Text("apiMap 条目数: ${provider.apiMap?.size ?: 0}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        val mappedCount = allMethods.count { provider.apiMap?.containsKey(it) == true }
        val unsupportedCount = allMethods.count { provider.apiMap?.get(it)?.equals("unsupported", ignoreCase = true) == true }
        val passthroughCount = allMethods.size - mappedCount
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(stringResource(R.string.mapped), "$mappedCount", MaterialTheme.colorScheme.primaryContainer)
            StatChip(stringResource(R.string.unsupported), "$unsupportedCount", MaterialTheme.colorScheme.errorContainer)
            StatChip(stringResource(R.string.passthrough), "$passthroughCount", MaterialTheme.colorScheme.surfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.all_api_methods), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        allMethods.forEachIndexed { index, method ->
            val mapped = provider.apiMap?.get(method)
            val isUnsupported = mapped?.equals("unsupported", ignoreCase = true) == true
            val isMapped = mapped != null && !isUnsupported
            val containerColor = when {
                isUnsupported -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                isMapped -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(containerColor).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(method, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    if (isMapped) Text("→ $mapped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = when { isUnsupported -> stringResource(R.string.unsupported); isMapped -> stringResource(R.string.mapped); else -> stringResource(R.string.passthrough) },
                    style = MaterialTheme.typography.labelSmall,
                    color = when { isUnsupported -> MaterialTheme.colorScheme.error; isMapped -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.outline }
                )
            }
        }
    }
}

// ======================== Tab 6: Provider 状态 ========================

@Composable
private fun ProviderStatusTab(scrollable: Boolean) {
    val providers by ModuleManager.providersFlow.collectAsState()
    val currentProvider = ProviderManager.currentProvider

    val columnModifier = if (scrollable) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    } else {
        Modifier.fillMaxWidth().padding(16.dp)
    }

    Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.loaded_providers), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        providers.forEach { provider ->
            val isCurrent = provider.id == currentProvider?.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isCurrent) {
                            AssistChip(onClick = {}, label = { Text(stringResource(R.string.active_label)) }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("ID: ${provider.id}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.type_label, provider.type.toString()), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.version_label, provider.version), style = MaterialTheme.typography.bodySmall)
                    Text("apiMap: ${provider.apiMap?.size ?: 0} 条", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (providers.isEmpty()) {
            Text(stringResource(R.string.no_loaded_providers), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ======================== 工具函数 ========================

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = valueColor)
    }
}

private fun formatWarning(warning: HealthMonitor.ResponseWarning): String = when (warning) {
    HealthMonitor.ResponseWarning.MISSING_CODE -> "缺少 code 字段"
    HealthMonitor.ResponseWarning.UNEXPECTED_CODE -> "异常响应码"
    HealthMonitor.ResponseWarning.MISSING_DATA_FIELD -> "缺少数据字段"
    HealthMonitor.ResponseWarning.EMPTY_DATA_ARRAY -> "空数据数组"
    HealthMonitor.ResponseWarning.EMPTY_DATA_OBJECT -> "空数据对象"
    HealthMonitor.ResponseWarning.MALFORMED_RESPONSE -> "响应格式异常"
    HealthMonitor.ResponseWarning.UNSUPPORTED_BY_PROVIDER -> "Provider 不支持"
    HealthMonitor.ResponseWarning.SLOW_RESPONSE -> "响应过慢(>5s)"
}

private fun parseJsonParams(json: String): Map<String, String> {
    val params = mutableMapOf<String, String>()
    try {
        val cleaned = json.trim()
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            val content = cleaned.removeSurrounding("{", "}").trim()
            if (content.isNotEmpty()) {
                content.split(",").forEach { pair ->
                    val parts = pair.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().removeSurrounding("\"").trim()
                        val value = parts[1].trim().removeSurrounding("\"").trim()
                        if (key.isNotEmpty()) params[key] = value
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return params
}

private fun formatJson(json: String): String {
    return try {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val element = com.google.gson.JsonParser.parseString(json)
        gson.toJson(element)
    } catch (_: Exception) { json }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer(content = content)
}
