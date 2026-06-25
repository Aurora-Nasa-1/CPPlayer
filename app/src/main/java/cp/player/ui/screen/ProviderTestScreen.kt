package cp.player.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.api.MusicApiMethod
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.StyledModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开发者测试 API 供应商界面。
 *
 * 功能：
 * 1. apiMap 可视化 - 显示所有方法及其映射状态
 * 2. 单个 API 方法测试 - 选择方法、编辑参数、发送请求、查看响应
 * 3. Provider 状态监控
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProviderTestScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(0) }

    AppScaffold(
        title = "Provider 测试",
        onBackPressed = onBackPressed,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab row
            PrimaryTabRow(selectedTabIndex = currentTab) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("API 测试") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("apiMap") },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    text = { Text("状态") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            when (currentTab) {
                0 -> ApiTestTab()
                1 -> ApiMapTab()
                2 -> ProviderStatusTab()
            }
        }
    }
}

/**
 * API 测试 Tab - 选择方法、编辑参数、发送请求
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTestTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 所有 API 方法列表
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 方法选择
        Text("选择 API 方法", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedMethod,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                interactionSource = remember { MutableInteractionSource() }
                    .also { interactionSource ->
                        LaunchedEffect(interactionSource) {
                            interactionSource.interactions.collect { interaction ->
                                if (interaction is PressInteraction.Release) {
                                    showMethodSheet = true
                                }
                            }
                        }
                    }
            )
        }

        // 参数编辑
        Text("请求参数 (JSON)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = paramsText,
            onValueChange = { paramsText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )

        // 发送按钮
        Button(
            onClick = {
                isLoading = true
                responseText = ""
                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    try {
                        val params = parseJsonParams(paramsText)
                        val result = withContext(Dispatchers.IO) {
                            ProviderManager.callApi(selectedMethod, params)
                        }
                        responseTime = System.currentTimeMillis() - startTime
                        responseText = formatJson(result)
                    } catch (e: Exception) {
                        responseTime = System.currentTimeMillis() - startTime
                        responseText = "错误: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("发送中...")
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("发送请求")
            }
        }

        // 响应
        if (responseText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("响应", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${responseTime}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SelectionContainer {
                    Text(
                        text = responseText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showMethodSheet) {
        StyledModalBottomSheet(onDismissRequest = { showMethodSheet = false }) {
            Text(
                "选择 API 方法",
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
                                        color = if (mapped.equals("unsupported", ignoreCase = true))
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                } else if (provider?.apiMap != null) {
                                    Text("(未映射，直通)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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

/**
 * apiMap 可视化 Tab
 */
@Composable
private fun ApiMapTab() {
    val provider = ProviderManager.currentProvider
    val allMethods = remember {
        listOf(
            "login/qr/key", "login/qr/create", "login/qr/check",
            "login", "login/cellphone", "captcha/sent", "logout",
            "register/anonimous", "login/status",
            "user/playlist", "user/playlist/create", "user/playlist/collect",
            "user/detail", "user/cloud", "likelist", "like",
            "recommend/songs", "recommend/resource", "recommend/songs/dislike",
            "playlist/detail", "playlist/track/all", "playlist/tracks",
            "playlist/create", "playlist/delete", "playlist/subscribe",
            "album", "artist/detail", "artist/songs", "artist/album",
            "cloudsearch", "search/hot/detail", "search/suggest",
            "song/url/v1/302", "song/url/v1", "song/download/url/v1",
            "song/detail", "personal_fm", "playmode/intelligence/list",
            "lyric/new",
            "comment/music", "comment/playlist", "comment/album",
            "comment/mv", "comment/dj", "comment/video",
            "comment/floor", "comment/like", "comment",
            "pl/count", "msg/recentcontact", "msg/private",
            "msg/private/history", "msg/private/mark/read", "send/text"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (provider == null) {
            Text("没有活跃的 Provider", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            return@Column
        }

        // Provider 信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前 Provider: ${provider.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("ID: ${provider.id}", style = MaterialTheme.typography.bodySmall)
                Text("类型: ${provider.type}", style = MaterialTheme.typography.bodySmall)
                Text("版本: ${provider.version}", style = MaterialTheme.typography.bodySmall)
                val apiMapSize = provider.apiMap?.size ?: 0
                Text("apiMap 条目数: $apiMapSize", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 统计
        val mappedCount = allMethods.count { provider.apiMap?.containsKey(it) == true }
        val unsupportedCount = allMethods.count {
            provider.apiMap?.get(it)?.equals("unsupported", ignoreCase = true) == true
        }
        val passthroughCount = allMethods.size - mappedCount

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("已映射", "$mappedCount", MaterialTheme.colorScheme.primaryContainer)
            StatChip("不支持", "$unsupportedCount", MaterialTheme.colorScheme.errorContainer)
            StatChip("直通", "$passthroughCount", MaterialTheme.colorScheme.surfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        // 方法列表
        Text("所有 API 方法", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        allMethods.forEachIndexed { index, method ->
            val mapped = provider.apiMap?.get(method)
            val isUnsupported = mapped?.equals("unsupported", ignoreCase = true) == true
            val isMapped = mapped != null && !isUnsupported
            val isPassthrough = mapped == null

            val containerColor = when {
                isUnsupported -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                isMapped -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(method, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    if (isMapped) {
                        Text("→ $mapped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = when {
                        isUnsupported -> "不支持"
                        isMapped -> "已映射"
                        else -> "直通"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isUnsupported -> MaterialTheme.colorScheme.error
                        isMapped -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

/**
 * Provider 状态 Tab
 */
@Composable
private fun ProviderStatusTab() {
    val providers = ModuleManager.getAvailableProviders()
    val currentProvider = ProviderManager.currentProvider

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("已加载的 Providers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        providers.forEach { provider ->
            val isCurrent = provider.id == currentProvider?.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isCurrent) {
                            AssistChip(
                                onClick = {},
                                label = { Text("活跃") },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("ID: ${provider.id}", style = MaterialTheme.typography.bodySmall)
                    Text("类型: ${provider.type}", style = MaterialTheme.typography.bodySmall)
                    Text("版本: ${provider.version}", style = MaterialTheme.typography.bodySmall)
                    Text("apiMap: ${provider.apiMap?.size ?: 0} 条", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (providers.isEmpty()) {
            Text("没有已加载的 Provider", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 解析简单的 JSON 参数字符串为 Map
 */
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
                        if (key.isNotEmpty()) {
                            params[key] = value
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return params
}

/**
 * 简单的 JSON 格式化
 */
private fun formatJson(json: String): String {
    return try {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val element = com.google.gson.JsonParser.parseString(json)
        gson.toJson(element)
    } catch (_: Exception) {
        json
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer(content = content)
}

/**
 * 内嵌版本的 Provider 测试界面，用于 SettingsScreen 内部导航。
 */
@Composable
fun ProviderTestInline() {
    var currentTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab row
        PrimaryTabRow(selectedTabIndex = currentTab) {
            Tab(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                text = { Text("API 测试") },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentTab == 1,
                onClick = { currentTab = 1 },
                text = { Text("apiMap") },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentTab == 2,
                onClick = { currentTab = 2 },
                text = { Text("状态") },
                icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (currentTab) {
            0 -> ApiTestTab()
            1 -> ApiMapTab()
            2 -> ProviderStatusTab()
        }
    }
}
