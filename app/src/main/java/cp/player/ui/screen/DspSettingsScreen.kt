package cp.player.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R
import cp.player.engine.ExoAudioFxManager
import cp.player.engine.RustEngine
import cp.player.util.AutoEqParser
import cp.player.util.DspPreferences
import cp.player.util.PeqBand
import cp.player.util.UserPreferences
import androidx.compose.runtime.setValue
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private data class EqNode(val freq: Float, val gain: Float, val q: Float = 0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    ExoAudioFxManager.initPrefs(context)
    val engineType = UserPreferences.getAudioEngine(context)

    // ExoPlayer 状态
    var eqEnabled by remember { mutableStateOf(ExoAudioFxManager.getEqualizerEnabled()) }
    var currentPreset by remember { mutableStateOf(ExoAudioFxManager.currentPreset) }
    val numBands = ExoAudioFxManager.getNumberOfBands()
    val numPresets = ExoAudioFxManager.getNumberOfPresets()
    val bandLevels = remember { mutableStateMapOf<Short, Short>() }
    val bandRange = remember { ExoAudioFxManager.getBandLevelRange() }
    // getCenterFreq 返回毫赫兹，除以1000得到Hz
    val bandFreqs = remember(numBands) { (0 until numBands).map { ExoAudioFxManager.getCenterFreq(it.toShort()) / 1000f } }
    LaunchedEffect(numBands, currentPreset) { for (i in 0 until numBands) bandLevels[i.toShort()] = ExoAudioFxManager.getBandLevel(i.toShort()) }

    // FlickPlayer 状态
    var flickEqEnabled by remember { mutableStateOf(DspPreferences.getEqEnabled(context)) }
    val peqBands = remember { mutableStateListOf<PeqBand>().apply { addAll(DspPreferences.getPeqBands(context)) } }
    var fxEnabled by remember { mutableStateOf(DspPreferences.getFxEnabled(context)) }
    var fxSize by remember { mutableStateOf(DspPreferences.getFxSize(context)) }
    var fxMix by remember { mutableStateOf(DspPreferences.getFxMix(context)) }
    var fxWidth by remember { mutableStateOf(DspPreferences.getFxWidth(context)) }

    // 进入屏幕时同步 DSP 状态到引擎（上次退出时可能已启用）
    LaunchedEffect(Unit) {
        // EQ
        if (flickEqEnabled) {
            if (peqBands.isEmpty()) {
                RustEngine.setEqualizer(true, floatArrayOf(), floatArrayOf(), floatArrayOf())
            } else {
                val freqs = FloatArray(peqBands.size)
                val gains = FloatArray(peqBands.size)
                val qs = FloatArray(peqBands.size)
                for (i in peqBands.indices) {
                    val band = peqBands[i]
                    freqs[i] = band.freq
                    gains[i] = band.gain
                    qs[i] = band.q
                }
                RustEngine.setEqualizer(true, freqs, gains, qs)
            }
        }
        // FX
        if (fxEnabled) {
            RustEngine.setFx(true, 0f, 1f, 0.35f, 6800f, 240f, fxSize, fxMix, 0.35f, fxWidth)
        }
    }

    var selectedBandIndex by remember { mutableIntStateOf(0) }
    if (selectedBandIndex >= peqBands.size) {
        selectedBandIndex = maxOf(0, peqBands.size - 1)
    }

    fun applyEq() {
        DspPreferences.setEqEnabled(context, flickEqEnabled); DspPreferences.setPeqBands(context, peqBands.toList())
        if (peqBands.isEmpty()) {
            RustEngine.setEqualizer(flickEqEnabled, floatArrayOf(), floatArrayOf(), floatArrayOf())
            return
        }
        val freqs = FloatArray(peqBands.size)
        val gains = FloatArray(peqBands.size)
        val qs = FloatArray(peqBands.size)
        for (i in peqBands.indices) {
            val band = peqBands[i]
            freqs[i] = band.freq
            gains[i] = band.gain
            qs[i] = band.q
        }
        RustEngine.setEqualizer(flickEqEnabled, freqs, gains, qs)
    }
    fun applyFx() {
        DspPreferences.setFxEnabled(context, fxEnabled); DspPreferences.setFxSize(context, fxSize); DspPreferences.setFxMix(context, fxMix); DspPreferences.setFxWidth(context, fxWidth)
        RustEngine.setFx(fxEnabled, 0f, 1f, 0.35f, 6800f, 240f, fxSize, fxMix, 0.35f, fxWidth)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { AutoEqParser.parse(context, it)?.let { p -> peqBands.clear(); peqBands.addAll(p); selectedBandIndex = 0; applyEq() } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ══════ FlickPlayer 工具栏 ══════
        if (engineType == 1) {
            item(key = "flick_toolbar") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 均衡器开关
                    Text("均衡器", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(
                        checked = flickEqEnabled,
                        onCheckedChange = { flickEqEnabled = it; applyEq() },
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    // 导入 AutoEQ
                    IconButton(onClick = {
                        try {
                            launcher.launch("text/plain")
                        } catch (e: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, context.getString(R.string.no_suitable_app_found), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.FolderOpen, "导入", modifier = Modifier.size(20.dp))
                    }
                    // 重置
                    if (peqBands.isNotEmpty()) {
                        IconButton(onClick = { peqBands.clear(); selectedBandIndex = 0; applyEq() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Refresh, "重置", modifier = Modifier.size(20.dp))
                        }
                    }
                    // 添加频段
                    IconButton(onClick = {
                        peqBands.add(PeqBand(1000f, 0f, 1f))
                        selectedBandIndex = peqBands.size - 1
                        flickEqEnabled = true
                        applyEq()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, "添加频段", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ══════ 频谱图（两个引擎共用，放在最上方）══════
        // ══════ 频谱图（两个引擎共用，放在最上方）══════
        if (engineType == 0 && eqEnabled) {
            item(key = "exo_chart") {
                val nodes = remember(bandLevels.toMap(), bandFreqs) {
                    bandFreqs.mapIndexed { i, f -> EqNode(f, (bandLevels[i.toShort()] ?: 0) / 100f) }
                }
                val maxG = (bandRange.getOrNull(1) ?: 1500) / 100f
                val minG = (bandRange.getOrNull(0) ?: -1500) / 100f
                ChartCard(nodes, minG..maxG) {
                    // 预设选择
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = currentPreset == (-1).toShort(), onClick = { currentPreset = -1; DspPreferences.setExoPreset(context, -1) }, label = { Text("自定义") }, leadingIcon = if (currentPreset == (-1).toShort()) { { Icon(Icons.Filled.Tune, null, Modifier.size(16.dp)) } } else null)
                        for (i in 0 until numPresets) { val p = i.toShort(); FilterChip(selected = currentPreset == p, onClick = { currentPreset = p; ExoAudioFxManager.usePreset(p); DspPreferences.setExoPreset(context, p); for (b in 0 until numBands) bandLevels[b.toShort()] = ExoAudioFxManager.getBandLevel(b.toShort()) }, label = { Text(ExoAudioFxManager.getPresetName(p)) }) }
                    }
                }
            }
            // 频段滑块
            if (numBands > 0) {
                item(key = "eq_bands") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), MaterialTheme.shapes.large, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 0 until numBands) {
                                val band = i.toShort(); val freq = bandFreqs[i]; val level = bandLevels[band] ?: 0
                                val label = if (freq >= 1000f) "${(freq / 1000f).roundToInt()}k" else "${freq.roundToInt()}"
                                val db = level / 100f; val sign = if (db > 0) "+" else ""
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(0.5f)).padding(horizontal = 4.dp, vertical = 8.dp)) {
                                    Text("$sign${db.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
                                    Spacer(Modifier.height(4.dp))
                                    VerticalSlider(level.toFloat(), bandRange[0].toFloat()..bandRange[1].toFloat(), onValueChange = { v -> val nl = v.toInt().toShort(); bandLevels[band] = nl; ExoAudioFxManager.setBandLevel(band, nl); currentPreset = -1; DspPreferences.setExoPreset(context, -1); DspPreferences.setExoEqGains(context, ExoAudioFxManager.eqGains) })
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (engineType == 1 && flickEqEnabled && peqBands.isNotEmpty()) {
            item(key = "flick_chart") {
                val nodes = remember(peqBands.toList()) { peqBands.map { EqNode(it.freq, it.gain, it.q) } }
                ChartCard(nodes, (-24f)..24f, selectedIndex = selectedBandIndex) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        peqBands.forEachIndexed { index, _ ->
                            FilterChip(selected = selectedBandIndex == index, onClick = { selectedBandIndex = index }, label = { Text("频段 ${index + 1}") })
                        }
                    }
                }
            }
        }

        if (engineType == 0) {
            // ══════ ExoPlayer ══════

            // 均衡器开关 + 重置
            item(key = "eq_toggle") {
                Column {
                    CompactToggle(
                        title = "均衡器",
                        subtitle = "${numBands} 段",
                        icon = Icons.Default.Equalizer,
                        enabled = eqEnabled,
                        onToggle = { eqEnabled = it; ExoAudioFxManager.setEqualizerEnabled(it); DspPreferences.setExoEqEnabled(context, it) },
                        iconTint = Color(0xFF2E7D32)
                    )
                    if (eqEnabled) {
                        CompactAction(
                            title = "重置均衡器",
                            icon = Icons.Outlined.Refresh,
                            onClick = { ExoAudioFxManager.usePreset(0); currentPreset = 0; DspPreferences.setExoPreset(context, 0); DspPreferences.setExoEqGains(context, ExoAudioFxManager.eqGains); for (b in 0 until numBands) bandLevels[b.toShort()] = ExoAudioFxManager.getBandLevel(b.toShort()) },
                            iconTint = Color(0xFFEF6C00)
                        )
                    }
                }
            }


        } else {
            // ══════ FlickPlayer ══════

            // PEQ 频段编辑
            if (flickEqEnabled && peqBands.isNotEmpty() && selectedBandIndex in peqBands.indices) {
                item(key = "peq_band_edit") {
                    val band = peqBands[selectedBandIndex]
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), MaterialTheme.shapes.large, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("频段 ${selectedBandIndex + 1} · ${formatFreq(band.freq)} · ${"%.1f".format(band.gain)}dB · Q=${"%.2f".format(band.q)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = {
                                    peqBands.removeAt(selectedBandIndex)
                                    if (selectedBandIndex >= peqBands.size) selectedBandIndex = maxOf(0, peqBands.size - 1)
                                    applyEq()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            FreqSliderItem("频率", formatFreq(band.freq), band.freq, { peqBands[selectedBandIndex] = band.copy(freq = it); applyEq() }, valueRange = 20f..20000f)
                            SliderItem("增益", "${"%.1f".format(band.gain)}dB", band.gain, { peqBands[selectedBandIndex] = band.copy(gain = it); applyEq() }, valueRange = -24f..24f)
                            // Q 值使用对数刻度滑块（0.1~10 跨两个数量级）
                            val qMinLog = log10(0.1f); val qMaxLog = log10(10f)
                            val qSliderPos = (log10(band.q.coerceIn(0.1f, 10f)) - qMinLog) / (qMaxLog - qMinLog)
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Q 值", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text("${"%.2f".format(band.q)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                }
                                Slider(qSliderPos, { pos -> peqBands[selectedBandIndex] = band.copy(q = 10f.pow(qMinLog + pos * (qMaxLog - qMinLog))); applyEq() }, Modifier.fillMaxWidth(), valueRange = 0f..1f)
                            }
                        }
                    }
                }
            }

            // 空间效果
            item(key = "fx") {
                Column {
                    Text("空间效果", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CompactToggle(
                        title = "空间音效",
                        subtitle = if (fxEnabled) "大小${"%.0f".format(fxSize * 100)}% · 混响${"%.0f".format(fxMix * 100)}% · 宽度${"%.0f".format(fxWidth * 100)}%" else "关闭",
                        icon = Icons.Default.SpatialAudio,
                        enabled = fxEnabled,
                        onToggle = { fxEnabled = it; applyFx() },
                        iconTint = Color(0xFF3949AB)
                    )
                    AnimatedVisibility(visible = fxEnabled, enter = expandVertically(tween(250)), exit = shrinkVertically(tween(180))) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), MaterialTheme.shapes.large, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                SliderItem("空间大小", "${"%.0f".format(fxSize * 100)}%", fxSize, { fxSize = it; applyFx() }, valueRange = 0f..1f)
                                SliderItem("混响混合", "${"%.0f".format(fxMix * 100)}%", fxMix, { fxMix = it; applyFx() }, valueRange = 0f..1f)
                                SliderItem("声场宽度", "${"%.0f".format(fxWidth * 100)}%", fxWidth, { fxWidth = it; applyFx() }, valueRange = 0f..2f)
                            }
                        }
                    }
                }
            }

        }

        item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════
// 频谱图卡片（共用）
// ═══════════════════════════════════════════════════════

@Composable
private fun ChartCard(
    nodes: List<EqNode>,
    gainRange: ClosedFloatingPointRange<Float>,
    selectedIndex: Int = -1,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), MaterialTheme.shapes.large, CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            DspVisualizer(nodes, gainRange, selectedIndex = selectedIndex)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════
// 紧凑开关行
// ═══════════════════════════════════════════════════════

@Composable
private fun CompactToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = { onToggle(!enabled) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(iconTint.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.height(28.dp), thumbContent = if (enabled) { { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) } } else null)
        }
    }
}

// ═══════════════════════════════════════════════════════
// 紧凑操作行
// ═══════════════════════════════════════════════════════

@Composable
private fun CompactAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(iconTint.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// Bell Curve 频响计算
// gain(f) = Σ gain_i · exp(-C_i · ln²(f/f0_i))
// C = 8·ln2·Q² → -3dB 带宽 = f0/Q
// ═══════════════════════════════════════════════════════

private val LN2 = kotlin.math.ln(2f)

/** 计算单频段 bell curve 在频率 f 处的 dB 增益 */
private fun bellCurveDb(f: Float, f0: Float, gainDb: Float, q: Float): Float {
    if (gainDb == 0f || q <= 0f || f0 <= 0f) return 0f
    val ratio = f / f0
    if (ratio <= 0f) return 0f
    val lnr = kotlin.math.ln(ratio)
    val c = 8f * LN2 * q * q
    return gainDb * kotlin.math.exp(-c * lnr * lnr)
}

// ═══════════════════════════════════════════════════════
// 频谱可视化
// ═══════════════════════════════════════════════════════

@Composable
private fun DspVisualizer(
    nodes: List<EqNode>,
    gainRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1
) {
    val curve = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val errorColor = MaterialTheme.colorScheme.error

    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = text, fontSize = 9.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width
        val h = size.height
        val px = 28.dp.toPx()
        val py = 16.dp.toPx()

        val plotW = w - px * 1.5f
        val plotH = h - py * 2f

        val minGain = gainRange.start
        val maxGain = gainRange.endInclusive

        fun gainToY(g: Float) = py + plotH * (1f - (g - minGain) / (maxGain - minGain))
        val minLog = log10(20f)
        val maxLog = log10(20000f)
        fun freqToX(f: Float) = px + (log10(f.coerceIn(20f, 20000f)) - minLog) / (maxLog - minLog) * plotW

        val zeroY = gainToY(0f)
        drawLine(grid, Offset(px, zeroY), Offset(px + plotW, zeroY), 1.5f.dp.toPx())

        listOf(minGain, 0f, maxGain).forEach { db ->
            val y = gainToY(db)
            drawLine(grid, Offset(px, y), Offset(px + plotW, y), 1.dp.toPx())
            drawText(
                textMeasurer,
                if (db > 0) "+${db.toInt()}" else "${db.toInt()}",
                topLeft = Offset(2.dp.toPx(), y - 6.dp.toPx()),
                style = textStyle
            )
        }

        val freqs = listOf(20f, 100f, 1000f, 10000f, 20000f)
        freqs.forEach { f ->
            val x = freqToX(f)
            drawLine(grid, Offset(x, py), Offset(x, py + plotH), 1.dp.toPx())
            val label = if (f >= 1000f) "${(f/1000f).toInt()}k" else "${f.toInt()}"
            drawText(
                textMeasurer,
                label,
                topLeft = Offset(x - 8.dp.toPx(), py + plotH + 4.dp.toPx()),
                style = textStyle
            )
        }

        if (nodes.isEmpty()) return@Canvas

        val sortedNodes = nodes.mapIndexed { i, n -> i to n }.sortedBy { it.second.freq }
        val hasQ = sortedNodes.any { it.second.q > 0f }

        if (hasQ) {
            // ═══ Bell Curve 频响（Q 值影响带宽）═══
            val sampleCount = 200
            val logStep = log10(20000f / 20f) / sampleCount
            val responsePts = (0 until sampleCount).map { i ->
                val f = 20f * 10f.pow(i * logStep)
                var totalDb = 0f
                for ((_, n) in sortedNodes) {
                    if (n.q > 0f) totalDb += bellCurveDb(f, n.freq, n.gain, n.q)
                }
                Offset(freqToX(f), gainToY(totalDb))
            }

            val fillPath = Path().apply {
                moveTo(responsePts.first().x, zeroY)
                for (p in responsePts) lineTo(p.x, p.y)
                lineTo(responsePts.last().x, zeroY)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(fill.copy(0.4f), fill.copy(0.05f)), startY = py, endY = zeroY))

            val linePath = Path().apply {
                moveTo(responsePts.first().x, responsePts.first().y)
                for (i in 1 until responsePts.size) lineTo(responsePts[i].x, responsePts[i].y)
            }
            drawPath(linePath, color = curve, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            // ═══ 无 Q 值时平滑插值 ═══
            val pts = sortedNodes.map { (_, n) -> Offset(freqToX(n.freq), gainToY(n.gain)) }

            val path = Path().apply {
                moveTo(px, zeroY)
                if (pts.isNotEmpty()) {
                    lineTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val cp = (pts[i-1].x + pts[i].x) / 2f
                        cubicTo(cp, pts[i-1].y, cp, pts[i].y, pts[i].x, pts[i].y)
                    }
                    lineTo(px + plotW, pts.last().y)
                }
                lineTo(px + plotW, zeroY)
            }

            val fillPath = Path().apply {
                addPath(path)
                lineTo(px + plotW, zeroY)
                lineTo(px, zeroY)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(fill.copy(0.4f), fill.copy(0.05f)), startY = py, endY = zeroY))

            val linePath = Path().apply {
                moveTo(px, zeroY)
                if (pts.isNotEmpty()) {
                    lineTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val cp = (pts[i-1].x + pts[i].x) / 2f
                        cubicTo(cp, pts[i-1].y, cp, pts[i].y, pts[i].x, pts[i].y)
                    }
                    lineTo(px + plotW, pts.last().y)
                }
            }
            drawPath(linePath, color = curve, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // 节点圆点
        sortedNodes.forEach { (originalIndex, n) ->
            val p = Offset(freqToX(n.freq), gainToY(n.gain))
            val isSelected = originalIndex == selectedIndex
            val nodeColor = if (isSelected) errorColor else curve
            val radius = if (isSelected) 5.dp.toPx() else 3.5f.dp.toPx()

            drawCircle(nodeColor, radius + 1.dp.toPx(), p)
            drawCircle(surface, radius - 1.dp.toPx(), p)
            if (isSelected) {
                drawCircle(nodeColor, radius - 2.dp.toPx(), p)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 垂直滑块
// ═══════════════════════════════════════════════════════

@Composable
private fun VerticalSlider(value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest; val trackFill = MaterialTheme.colorScheme.primary
    val thumbFill = MaterialTheme.colorScheme.primary; val thumbDot = MaterialTheme.colorScheme.onPrimary; val zeroLine = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.width(40.dp).height(120.dp)
        .pointerInput(Unit) { detectDragGestures(onDragEnd = { onValueChangeFinished?.invoke() }, onDragCancel = { onValueChangeFinished?.invoke() }) { change, _ -> val frac = 1f - (change.position.y / size.height).coerceIn(0f, 1f); onValueChange(valueRange.start + frac * (valueRange.endInclusive - valueRange.start)) } }
        .pointerInput(Unit) { detectTapGestures { offset -> val frac = 1f - (offset.y / size.height).coerceIn(0f, 1f); onValueChange(valueRange.start + frac * (valueRange.endInclusive - valueRange.start)); onValueChangeFinished?.invoke() } }
    ) {
        val w = size.width; val h = size.height; val tw = 5.dp.toPx(); val cx = w / 2f; val pad = 6.dp.toPx()
        drawRoundRect(trackBg, Offset(cx - tw / 2f, pad), Size(tw, h - pad * 2), CornerRadius(tw / 2f))
        val zeroFrac = ((0f - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        drawLine(zeroLine, Offset(cx - tw, h * (1f - zeroFrac)), Offset(cx + tw, h * (1f - zeroFrac)), 1.5f.dp.toPx())
        val frac = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val topY = h * (1f - frac)
        if (frac > 0f) drawRoundRect(trackFill.copy(0.4f), Offset(cx - tw / 2f, topY.coerceAtLeast(pad)), Size(tw, (h - pad - topY).coerceAtLeast(0f)), CornerRadius(tw / 2f))
        val thumbY = topY.coerceIn(pad, h - pad)
        drawCircle(thumbFill, 9.dp.toPx(), Offset(cx, thumbY)); drawCircle(thumbDot, 3.dp.toPx(), Offset(cx, thumbY))
    }
}

// ═══════════════════════════════════════════════════════
// 滑块条目
// ═══════════════════════════════════════════════════════

@Composable
private fun SliderItem(label: String, valueText: String, value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null, valueRange: ClosedFloatingPointRange<Float>) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
        Slider(value, onValueChange, Modifier.fillMaxWidth(), onValueChangeFinished = onValueChangeFinished, valueRange = valueRange)
    }
}

@Composable
private fun FreqSliderItem(label: String, valueText: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    val minLog = log10(valueRange.start)
    val maxLog = log10(valueRange.endInclusive)
    val sliderPos = (log10(value.coerceIn(valueRange)) - minLog) / (maxLog - minLog)

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
        Slider(
            value = sliderPos,
            onValueChange = { pos ->
                val freq = 10f.pow(minLog + pos * (maxLog - minLog))
                onValueChange(freq)
            },
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..1f
        )
    }
}

private fun formatFreq(freq: Float): String = if (freq >= 1000) "${"%.1f".format(freq / 1000)}kHz" else "${freq.roundToInt()}Hz"
