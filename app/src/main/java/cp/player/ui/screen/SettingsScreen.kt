package cp.player.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R
import cp.player.ui.component.ExpressiveShapes
import cp.player.ui.component.AppScaffold
import cp.player.util.LogManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentQualityWifi: String,
    onQualityWifiChange: (String) -> Unit,
    currentQualityCellular: String,
    onQualityCellularChange: (String) -> Unit,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    fadeDuration: Float,
    onFadeChange: (Float) -> Unit,
    fadeMode: Int,
    onFadeModeChange: (Int) -> Unit,
    autoAudioFocus: Boolean,
    onAutoAudioFocusChange: (Boolean) -> Unit,
    cacheSize: Int,
    onCacheSizeChange: (Int) -> Unit,
    useCellularCache: Boolean,
    onUseCellularCacheChange: (Boolean) -> Unit,
    allowCellularDownload: Boolean,
    onAllowCellularDownloadChange: (Boolean) -> Unit,
    pureBlackMode: Boolean,
    onPureBlackModeChange: (Boolean) -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    followCoverApp: Boolean,
    onFollowCoverAppChange: (Boolean) -> Unit,
    followCoverMini: Boolean,
    onFollowCoverMiniChange: (Boolean) -> Unit,
    followCoverPlayer: Boolean,
    onFollowCoverPlayerChange: (Boolean) -> Unit,
    useFluidBackground: Boolean,
    onUseFluidBackgroundChange: (Boolean) -> Unit,
    useWavyProgress: Boolean,
    onUseWavyProgressChange: (Boolean) -> Unit,
    audioFocusMode: Int,
    onAudioFocusModeChange: (Int) -> Unit,
    allowDucking: Boolean,
    onAllowDuckingChange: (Boolean) -> Unit,
    pauseOnNoisy: Boolean,
    onPauseOnNoisyChange: (Boolean) -> Unit,
    downloadDir: String?,
    onDownloadDirChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var currentScreen by rememberSaveable { mutableStateOf("main") }

    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onDownloadDirChange(it.toString())
        }
    }

    BackHandler(enabled = currentScreen != "main") {
        currentScreen = "main"
    }

    val titleRes = when (currentScreen) {
        "appearance" -> R.string.appearance
        "audio" -> R.string.playback_quality_cat
        "download" -> R.string.download_settings
        "storage" -> R.string.storage_cache
        "debug" -> R.string.debug
        "provider" -> R.string.provider_management
        else -> R.string.settings
    }

    AppScaffold(
        title = stringResource(titleRes),
        onBackPressed = {
            if (currentScreen == "main") onBackPressed() else currentScreen = "main"
        },
        scrollBehavior = scrollBehavior
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState != "main" && initialState == "main") {
                    (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                } else if (targetState == "main" && initialState != "main") {
                    (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300)))
                } else {
                    fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
                }.using(SizeTransform(clip = false))
            },
            label = "SettingsScreenTransition"
        ) { screen ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (screen) {
                    "main" -> {
                        SettingsSection(title = stringResource(R.string.settings)) {
                            ExpressiveClickItem(
                                title = stringResource(R.string.appearance),
                                subtitle = stringResource(R.string.settings_appearance_desc),
                                onClick = { currentScreen = "appearance" },
                                shape = ExpressiveShapes.calculateShape(0, 5)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.playback_quality_cat),
                                subtitle = stringResource(R.string.settings_audio_desc),
                                onClick = { currentScreen = "audio" },
                                shape = ExpressiveShapes.calculateShape(1, 5)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.download_settings),
                                subtitle = stringResource(R.string.settings_download_desc),
                                onClick = { currentScreen = "download" },
                                shape = ExpressiveShapes.calculateShape(2, 5)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.storage_cache),
                                subtitle = stringResource(R.string.settings_storage_desc),
                                onClick = { currentScreen = "storage" },
                                shape = ExpressiveShapes.calculateShape(3, 6)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.debug),
                                subtitle = stringResource(R.string.settings_debug_desc),
                                onClick = { currentScreen = "debug" },
                                shape = ExpressiveShapes.calculateShape(4, 6)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.provider_management),
                                subtitle = stringResource(R.string.provider_management_desc),
                                onClick = { currentScreen = "provider" },
                                shape = ExpressiveShapes.calculateShape(5, 6)
                            )
                        }
                    }
                    "appearance" -> {
                        // Appearance Section
                        SettingsSection(title = stringResource(R.string.appearance)) {
                            val themeOptions = listOf(
                                stringResource(R.string.theme_mode_system),
                                stringResource(R.string.theme_mode_cover),
                                stringResource(R.string.theme_mode_fixed)
                            )
                            
                            val appearanceItemsCount = if (themeMode == 1) 7 else 3

                            ExpressiveDropdownItem(
                                title = stringResource(R.string.theme_mode),
                                subtitle = themeOptions.getOrElse(themeMode) { themeOptions[0] },
                                options = themeOptions,
                                selectedIndex = themeMode,
                                onSelect = onThemeModeChange,
                                shape = ExpressiveShapes.calculateShape(0, appearanceItemsCount)
                            )

                            if (themeMode == 1) {
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_app),
                                    subtitle = stringResource(R.string.follow_cover_app_desc),
                                    checked = followCoverApp,
                                    onCheckedChange = onFollowCoverAppChange,
                                    shape = ExpressiveShapes.calculateShape(1, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_mini),
                                    subtitle = stringResource(R.string.follow_cover_mini_desc),
                                    checked = followCoverMini,
                                    onCheckedChange = onFollowCoverMiniChange,
                                    shape = ExpressiveShapes.calculateShape(2, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_player),
                                    subtitle = stringResource(R.string.follow_cover_player_desc),
                                    checked = followCoverPlayer,
                                    onCheckedChange = onFollowCoverPlayerChange,
                                    shape = ExpressiveShapes.calculateShape(3, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.fluid_background),
                                    subtitle = stringResource(R.string.fluid_background_desc),
                                    checked = useFluidBackground,
                                    onCheckedChange = onUseFluidBackgroundChange,
                                    shape = ExpressiveShapes.calculateShape(4, appearanceItemsCount)
                                )
                            }
                            
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.pure_black_mode),
                                subtitle = stringResource(R.string.pure_black_desc),
                                checked = pureBlackMode,
                                onCheckedChange = onPureBlackModeChange,
                                shape = ExpressiveShapes.calculateShape(if (themeMode == 1) 5 else 1, appearanceItemsCount)
                            )
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.wavy_progress_bar),
                                subtitle = stringResource(R.string.wavy_progress_bar_desc),
                                checked = useWavyProgress,
                                onCheckedChange = onUseWavyProgressChange,
                                shape = ExpressiveShapes.calculateShape(if (themeMode == 1) 6 else 2, appearanceItemsCount)
                            )
                        }
                    }
                    "audio" -> {
                        // Audio Quality Section
                        SettingsSection(title = stringResource(R.string.playback_quality_cat)) {
                            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
                            
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.wifi_quality_label),
                                subtitle = currentQualityWifi.replaceFirstChar { it.uppercase() },
                                options = qualities.map { it.replaceFirstChar { it.uppercase() } },
                                selectedIndex = qualities.indexOf(currentQualityWifi).coerceAtLeast(0),
                                onSelect = { onQualityWifiChange(qualities[it]) },
                                shape = ExpressiveShapes.calculateShape(0, 2)
                            )
                            
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.cellular_quality_label),
                                subtitle = currentQualityCellular.replaceFirstChar { it.uppercase() },
                                options = qualities.map { it.replaceFirstChar { it.uppercase() } },
                                selectedIndex = qualities.indexOf(currentQualityCellular).coerceAtLeast(0),
                                onSelect = { onQualityCellularChange(qualities[it]) },
                                shape = ExpressiveShapes.calculateShape(1, 2)
                            )
                        }

                        // Audio Focus Section
                        SettingsSection(title = stringResource(R.string.audio_focus_management)) {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.auto_audio_focus),
                                subtitle = stringResource(R.string.auto_audio_focus_desc),
                                checked = autoAudioFocus,
                                onCheckedChange = onAutoAudioFocusChange,
                                shape = ExpressiveShapes.calculateShape(0, if (autoAudioFocus) 1 else 4)
                            )
                            
                            if (!autoAudioFocus) {
                                val focusModes = listOf(stringResource(R.string.focus_mode_duck), stringResource(R.string.focus_mode_pause))
                                ExpressiveDropdownItem(
                                    title = stringResource(R.string.transient_focus_loss_behavior),
                                    subtitle = focusModes.getOrElse(audioFocusMode) { focusModes[0] },
                                    options = focusModes,
                                    selectedIndex = audioFocusMode,
                                    onSelect = onAudioFocusModeChange,
                                    shape = ExpressiveShapes.calculateShape(1, 4)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.allow_ducking),
                                    subtitle = stringResource(R.string.allow_ducking_desc),
                                    checked = allowDucking,
                                    onCheckedChange = onAllowDuckingChange,
                                    shape = ExpressiveShapes.calculateShape(2, 4)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.pause_on_noisy),
                                    subtitle = stringResource(R.string.pause_on_noisy_desc),
                                    checked = pauseOnNoisy,
                                    onCheckedChange = onPauseOnNoisyChange,
                                    shape = ExpressiveShapes.calculateShape(3, 4)
                                )
                            }
                        }

                        // Audio Effects (Crossfade)
                        SettingsSection(title = stringResource(R.string.audio_effects)) {
                            val fadeModes = listOf(
                                stringResource(R.string.fade_mode_crossfade),
                                stringResource(R.string.fade_mode_single),
                                stringResource(R.string.fade_mode_off)
                            )
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.fade_mode),
                                subtitle = fadeModes.getOrElse(fadeMode) { fadeModes[0] },
                                options = fadeModes,
                                selectedIndex = fadeMode,
                                onSelect = onFadeModeChange,
                                shape = ExpressiveShapes.calculateShape(0, if (fadeMode == 2) 1 else 2)
                            )
                            
                            if (fadeMode != 2) {
                                ExpressiveSliderItem(
                                    title = stringResource(R.string.crossfade_duration, fadeDuration.toInt()),
                                    value = fadeDuration,
                                    onValueChange = onFadeChange,
                                    valueRange = 0f..10f,
                                    steps = 10,
                                    shape = ExpressiveShapes.calculateShape(1, 2)
                                )
                            }
                        }
                    }
                    "download" -> {
                        // Download Section
                        SettingsSection(title = stringResource(R.string.download_settings)) {
                            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
                            
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.download_quality_label),
                                subtitle = downloadQuality.replaceFirstChar { it.uppercase() },
                                options = qualities.map { it.replaceFirstChar { it.uppercase() } },
                                selectedIndex = qualities.indexOf(downloadQuality).coerceAtLeast(0),
                                onSelect = { onDownloadQualityChange(qualities[it]) },
                                shape = ExpressiveShapes.calculateShape(0, 3)
                            )

                            ExpressiveSwitchItem(
                                title = stringResource(R.string.allow_cellular_download),
                                subtitle = stringResource(R.string.allow_cellular_download_desc),
                                checked = allowCellularDownload,
                                onCheckedChange = onAllowCellularDownloadChange,
                                shape = ExpressiveShapes.calculateShape(1, 3)
                            )

                            ExpressiveClickItem(
                                title = stringResource(R.string.download_dir),
                                subtitle = downloadDir?.substringAfterLast("%2F") ?: stringResource(R.string.system_music_folder),
                                onClick = { dirPicker.launch(null) },
                                shape = ExpressiveShapes.calculateShape(2, 3)
                            )
                        }
                    }
                    "storage" -> {
                        // Storage & Cache
                        SettingsSection(title = stringResource(R.string.storage_cache)) {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.cellular_caching),
                                subtitle = stringResource(R.string.cellular_caching_desc),
                                checked = useCellularCache,
                                onCheckedChange = onUseCellularCacheChange,
                                shape = ExpressiveShapes.calculateShape(0, 3)
                            )

                            ExpressiveSliderItem(
                                title = stringResource(R.string.max_cache_size, cacheSize),
                                value = cacheSize.toFloat(),
                                onValueChange = { onCacheSizeChange(it.toInt()) },
                                valueRange = 100f..2048f,
                                steps = 19,
                                shape = ExpressiveShapes.calculateShape(1, 3)
                            )

                            ExpressiveButtonItem(
                                text = stringResource(R.string.clear_cache),
                                onClick = onClearCache,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = ExpressiveShapes.calculateShape(2, 3)
                            )
                        }
                    }
                    "provider" -> {
                        SettingsSection(title = stringResource(R.string.provider_management)) {
                            val providers = cp.player.provider.ModuleManager.getAvailableProviders()
                            val coroutineScope = rememberCoroutineScope()
                            val launcher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent()
                            ) { uri ->
                                uri?.let {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            context.contentResolver.openInputStream(it)?.use { input ->
                                                val tempFile = java.io.File(context.cacheDir, "temp_module.zip")
                                                java.io.FileOutputStream(tempFile).use { output ->
                                                    input.copyTo(output)
                                                }
                                                val success = cp.player.provider.ModuleManager.importModule(context, tempFile)
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    if (success) {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.module_import_success), android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.module_import_failed), android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        } catch(e: Exception) {
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.module_import_error), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            
                            providers.forEachIndexed { index, provider ->
                                val isCurrent = cp.player.provider.ProviderManager.currentProvider?.id == provider.id
                                val activeStr = if (isCurrent) stringResource(R.string.provider_active) else ""
                                ExpressiveClickItem(
                                    title = provider.name + activeStr,
                                    subtitle = stringResource(R.string.provider_info, provider.type, provider.version),
                                    onClick = { 
                                        cp.player.provider.ProviderManager.currentProvider = provider
                                        android.widget.Toast.makeText(context, context.getString(R.string.provider_switched, provider.name), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = ExpressiveShapes.calculateShape(index, providers.size + 1)
                                )
                            }
                            
                            ExpressiveButtonItem(
                                text = stringResource(R.string.import_new_module),
                                onClick = { launcher.launch("application/zip") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = ExpressiveShapes.calculateShape(providers.size, providers.size + 1)
                            )
                        }
                    }
                    "debug" -> {
                        // Debug
                        SettingsSection(title = stringResource(R.string.debug)) {
                            val logsCopiedMsg = stringResource(R.string.logs_copied)
                            ExpressiveButtonItem(
                                text = stringResource(R.string.copy_debug_logs),
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val clip = ClipData.newPlainText("CP Player Logs", LogManager.getAllLogsString())
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, logsCopiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = ExpressiveShapes.calculateShape(0, 1)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp + bottomContentPadding.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 12.dp),
            letterSpacing = 0.5.sp
        )
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
fun ExpressiveClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) },
        trailingContent = { 
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) },
        trailingContent = {
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Switch(
                    checked = checked, 
                    onCheckedChange = onCheckedChange,
                    thumbContent = if (checked) {
                        { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveDropdownItem(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    var showDialog by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) },
        trailingContent = { 
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    options.forEachIndexed { index, option ->
                        val isSelected = index == selectedIndex
                        Surface(
                            onClick = {
                                onSelect(index)
                                showDialog = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    option, 
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun ExpressiveSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    shape: androidx.compose.ui.graphics.Shape
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveButtonItem(
    text: String,
    onClick: () -> Unit,
    containerColor: Color = Color.Unspecified, 
    contentColor: Color = Color.Unspecified,
    shape: androidx.compose.ui.graphics.Shape
) {
    val finalContainerColor = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surface else containerColor
    val finalContentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor

    ListItem(
        headlineContent = { Text(text, color = finalContentColor, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = finalContainerColor)
    )
}