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
import androidx.compose.material3.ListItemDefaults
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.StyledModalBottomSheet
import cp.player.util.LogManager
import cp.player.viewmodel.LoginViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class SettingsPage(val id: String, val usesVerticalScroll: Boolean = true) {
    Main("main"),
    Appearance("appearance"),
    Audio("audio"),
    StorageDownload("storage_download"),
    Provider("provider"),
    ProviderSettings("providerSettings", usesVerticalScroll = false),
    About("about"),
    Sponsor("sponsor"),
    Debug("debug"),
    Health("health"),
    LogViewer("logViewer"),
    Dsp("dsp", usesVerticalScroll = false),
    ProviderTest("providerTest"),
    UiLogic("uiLogic");

    val parent: SettingsPage?
        get() = when (this) {
            Health, LogViewer, ProviderTest -> Debug
            About, UiLogic -> Main
            Dsp -> Audio
            ProviderSettings -> Provider
            else -> null
        }

    companion object {
        fun fromId(id: String): SettingsPage = entries.find { it.id == id } ?: Main
    }
}
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
    audioFocusMode: Int,
    onAudioFocusModeChange: (Int) -> Unit,
    allowDucking: Boolean,
    onAllowDuckingChange: (Boolean) -> Unit,
    pauseOnNoisy: Boolean,
    onPauseOnNoisyChange: (Boolean) -> Unit,
    autoResumeUsbAudio: Boolean,
    onAutoResumeUsbAudioChange: (Boolean) -> Unit,
    downloadDir: String?,
    onDownloadDirChange: (String) -> Unit,
    audioEngine: Int,
    onAudioEngineChange: (Int) -> Unit,
    dsdOutputMode: Int,
    onDsdOutputModeChange: (Int) -> Unit,
    dapBitPerfect: Boolean,
    onDapBitPerfectChange: (Boolean) -> Unit,
    usbExclusive: Boolean,
    onUsbExclusiveChange: (Boolean) -> Unit,
    fontRoundness: Int,
    onFontRoundnessChange: (Int) -> Unit,
    playImmediately: Boolean,
    onPlayImmediatelyChange: (Boolean) -> Unit,
    lyricsSource: Int,
    onLyricsSourceChange: (Int) -> Unit,
    amllPlatform: String,
    onAmllPlatformChange: (String) -> Unit,
    hideNavbarOnScroll: Boolean,
    onHideNavbarOnScrollChange: (Boolean) -> Unit,
    wavyProgress: Boolean,
    onWavyProgressChange: (Boolean) -> Unit,
    restoreLastQueue: Boolean,
    onRestoreLastQueueChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp),
    isPlayerExpanded: Boolean = false,
    useSideNav: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var currentScreenId by rememberSaveable { mutableStateOf(SettingsPage.Main.id) }
    val currentScreen = SettingsPage.fromId(currentScreenId)


    var currentProviderSettingsId by rememberSaveable { mutableStateOf("") }

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

    BackHandler(enabled = currentScreen != SettingsPage.Main && !isPlayerExpanded) {
        currentScreenId = currentScreen.parent?.id ?: SettingsPage.Main.id
    }

    val titleRes = when (currentScreen) {
        SettingsPage.Appearance -> R.string.appearance
        SettingsPage.Audio -> R.string.playback_quality_cat
        SettingsPage.StorageDownload -> R.string.storage_cache
        SettingsPage.Debug -> R.string.debug
        SettingsPage.Provider -> R.string.provider_management
        SettingsPage.ProviderSettings -> R.string.settings // Will be overridden in ProviderSettingsScreen itself
        SettingsPage.About -> R.string.about
        SettingsPage.Sponsor -> R.string.sponsor
        SettingsPage.Health -> R.string.health_status
        SettingsPage.Dsp -> R.string.dsp_equalizer // Just a fallback, handled in DspSettingsScreen
        SettingsPage.UiLogic -> R.string.settings_ui_logic
        SettingsPage.LogViewer -> R.string.app_logs
        else -> R.string.settings
    }

    AppScaffold(
        title = stringResource(titleRes),
        onBackPressed = {
            if (currentScreen == SettingsPage.Main) onBackPressed() else currentScreenId = currentScreen.parent?.id ?: SettingsPage.Main.id
        },
        scrollBehavior = scrollBehavior,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            // 横屏：左侧分类导航列表
            if (useSideNav) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 8.dp, top = innerPadding.calculateTopPadding(), bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val navCategories = listOf(
                        R.string.appearance to SettingsPage.Appearance,
                        R.string.playback_quality_cat to SettingsPage.Audio,
                        R.string.settings_ui_logic to SettingsPage.UiLogic,
                        R.string.storage_cache to SettingsPage.StorageDownload,
                        R.string.debug to SettingsPage.Debug,
                        R.string.provider_management to SettingsPage.Provider,
                        R.string.about to SettingsPage.About,
                        R.string.sponsor to SettingsPage.Sponsor
                    )
                    navCategories.forEachIndexed { index, (titleRes, page) ->
                        ListItem(
                            headlineContent = { Text(stringResource(titleRes)) },
                            leadingContent = {
                                Icon(
                                    imageVector = when (page) {
                                        SettingsPage.Appearance -> Icons.Default.Palette
                                        SettingsPage.Audio -> Icons.Default.Audiotrack
                                        SettingsPage.UiLogic -> Icons.Default.TouchApp
                                        SettingsPage.StorageDownload -> Icons.Default.Storage
                                        SettingsPage.Debug -> Icons.Default.BugReport
                                        SettingsPage.Provider -> Icons.Default.Extension
                                        SettingsPage.About -> Icons.Default.HelpOutline
                                        SettingsPage.Sponsor -> Icons.Default.Favorite
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (currentScreen == page)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentScreenId = page.id }
                        )
                    }
                }
            }
            // 右侧/全屏：详情内容（原有 AnimatedContent）
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
            targetState = currentScreen,
            contentKey = { it.id },
            transitionSpec = {
                if (targetState != SettingsPage.Main && initialState == SettingsPage.Main) {
                    (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                } else if (targetState == SettingsPage.Main && initialState != SettingsPage.Main) {
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
                    .then(if (screen.usesVerticalScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (screen) {
                    SettingsPage.Main -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ExpressiveClickItem(
                                title = stringResource(R.string.appearance),
                                subtitle = stringResource(R.string.settings_appearance_desc),
                                icon = { MonetIcon(Icons.Default.Palette, Color(0xFFE8F5E9), Color(0xFF2E7D32)) },
                                onClick = { currentScreenId = SettingsPage.Appearance.id },
                                shapes = ListItemDefaults.segmentedShapes(0, 7)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.playback_quality_cat),
                                subtitle = stringResource(R.string.settings_audio_desc),
                                icon = { MonetIcon(Icons.Default.Audiotrack, Color(0xFFE3F2FD), Color(0xFF1565C0)) },
                                onClick = { currentScreenId = SettingsPage.Audio.id },
                                shapes = ListItemDefaults.segmentedShapes(1, 7)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.settings_ui_logic),
                                subtitle = stringResource(R.string.settings_ui_logic_desc),
                                icon = { MonetIcon(Icons.Default.TouchApp, Color(0xFFE8F5E9), Color(0xFF388E3C)) },
                                onClick = { currentScreenId = SettingsPage.UiLogic.id },
                                shapes = ListItemDefaults.segmentedShapes(2, 7)
                            )
                            ExpressiveClickItem(
                                title = "${stringResource(R.string.storage_cache)} & ${stringResource(R.string.download_settings)}",
                                subtitle = stringResource(R.string.settings_storage_desc) + " " + stringResource(R.string.settings_download_desc),
                                icon = { MonetIcon(Icons.Default.Storage, Color(0xFFFFF3E0), Color(0xFFEF6C00)) },
                                onClick = { currentScreenId = SettingsPage.StorageDownload.id },
                                shapes = ListItemDefaults.segmentedShapes(3, 7)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.debug),
                                subtitle = stringResource(R.string.settings_debug_desc),
                                icon = { MonetIcon(Icons.Default.BugReport, Color(0xFFFCE4EC), Color(0xFFC2185B)) },
                                onClick = { currentScreenId = SettingsPage.Debug.id },
                                shapes = ListItemDefaults.segmentedShapes(4, 7)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.provider_management),
                                subtitle = stringResource(R.string.manage_music_source_modules),
                                icon = { MonetIcon(Icons.Default.Extension, Color(0xFFFFFDE7), Color(0xFFF57F17)) },
                                onClick = { currentScreenId = SettingsPage.Provider.id },
                                shapes = ListItemDefaults.segmentedShapes(5, 7)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.about),
                                subtitle = stringResource(R.string.settings_about_desc),
                                icon = { MonetIcon(Icons.Default.HelpOutline, Color(0xFFEFEBE9), Color(0xFF4E342E)) },
                                onClick = { currentScreenId = SettingsPage.About.id },
                                shapes = ListItemDefaults.segmentedShapes(6, 8)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.sponsor),
                                subtitle = stringResource(R.string.settings_sponsor_desc),
                                icon = { MonetIcon(Icons.Default.Favorite, Color(0xFFFCE4EC), Color(0xFFE91E63)) },
                                onClick = { currentScreenId = SettingsPage.Sponsor.id },
                                shapes = ListItemDefaults.segmentedShapes(7, 8)
                            )
                        }
                    }
                    SettingsPage.Appearance -> {
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
                                shapes = ListItemDefaults.segmentedShapes(0, appearanceItemsCount)
                            )

                            if (themeMode == 1) {
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_app),
                                    subtitle = stringResource(R.string.follow_cover_app_desc),
                                    checked = followCoverApp,
                                    onCheckedChange = onFollowCoverAppChange,
                                    shapes = ListItemDefaults.segmentedShapes(1, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_mini),
                                    subtitle = stringResource(R.string.follow_cover_mini_desc),
                                    checked = followCoverMini,
                                    onCheckedChange = onFollowCoverMiniChange,
                                    shapes = ListItemDefaults.segmentedShapes(2, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.follow_cover_player),
                                    subtitle = stringResource(R.string.follow_cover_player_desc),
                                    checked = followCoverPlayer,
                                    onCheckedChange = onFollowCoverPlayerChange,
                                    shapes = ListItemDefaults.segmentedShapes(3, appearanceItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.fluid_background),
                                    subtitle = stringResource(R.string.fluid_background_desc),
                                    checked = useFluidBackground,
                                    onCheckedChange = onUseFluidBackgroundChange,
                                    shapes = ListItemDefaults.segmentedShapes(4, appearanceItemsCount)
                                )
                            }
                            
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.pure_black_mode),
                                subtitle = stringResource(R.string.pure_black_desc),
                                checked = pureBlackMode,
                                onCheckedChange = onPureBlackModeChange,
                                shapes = ListItemDefaults.segmentedShapes(if (themeMode == 1) 5 else 1, appearanceItemsCount)
                            )

                            // Font Roundness Setting (Android 16 QPR2 style)
                            val fontRoundnessOptions = listOf(
                                stringResource(R.string.font_roundness_standard),
                                stringResource(R.string.font_roundness_expressive),
                                stringResource(R.string.font_roundness_default)
                            )
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.font_roundness),
                                subtitle = fontRoundnessOptions.getOrElse(fontRoundness) { fontRoundnessOptions[0] },
                                options = fontRoundnessOptions,
                                selectedIndex = fontRoundness,
                                onSelect = onFontRoundnessChange,
                                shapes = ListItemDefaults.segmentedShapes(if (themeMode == 1) 6 else 2, appearanceItemsCount)
                            )
                        }
                    }
                    SettingsPage.Audio -> {
                        // 播放引擎选择
                        SettingsSection(title = stringResource(R.string.playback_engine)) {
                            val engines = listOf(
                                stringResource(id = R.string.engine_exoplayer),
                                stringResource(id = R.string.engine_flick)
                            )
                            ExpressiveDropdownItem(
                                title = stringResource(R.string.engine_selection),
                                subtitle = engines.getOrElse(audioEngine) { engines[0] },
                                options = engines,
                                selectedIndex = audioEngine,
                                onSelect = { onAudioEngineChange(it) },
                                shapes = ListItemDefaults.segmentedShapes(0, 1)
                            )
                        }

                        // 音频焦点设置
                        SettingsSection(title = stringResource(R.string.audio_focus_management)) {
                            val isFlick = audioEngine == 1
                            // Flick 始终显示手动设置；ExoPlayer 根据 autoAudioFocus 决定
                            val showManual = isFlick || !autoAudioFocus
                            val focusItemsCount = if (isFlick) 4 else if (autoAudioFocus) 2 else 5

                            // 引擎提示
                            ExpressiveClickItem(
                                title = stringResource(R.string.audio_focus_management),
                                subtitle = stringResource(
                                    if (isFlick) R.string.audio_focus_engine_hint_flick
                                    else R.string.audio_focus_engine_hint_exoplayer
                                ),
                                onClick = {},
                                shapes = ListItemDefaults.segmentedShapes(0, focusItemsCount)
                            )

                            // ExoPlayer 显示自动焦点开关；Flick 始终手动，不显示
                            if (!isFlick) {
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.auto_audio_focus),
                                    subtitle = stringResource(R.string.auto_audio_focus_desc),
                                    checked = autoAudioFocus,
                                    onCheckedChange = onAutoAudioFocusChange,
                                    shapes = ListItemDefaults.segmentedShapes(1, focusItemsCount)
                                )
                            }

                            if (showManual) {
                                val manualStartIdx = if (isFlick) 1 else 2
                                val focusModes = listOf(stringResource(R.string.focus_mode_duck), stringResource(R.string.focus_mode_pause), stringResource(R.string.focus_mode_continue))
                                ExpressiveDropdownItem(
                                    title = stringResource(R.string.transient_focus_loss_behavior),
                                    subtitle = focusModes.getOrElse(audioFocusMode) { focusModes[0] },
                                    options = focusModes,
                                    selectedIndex = audioFocusMode,
                                    onSelect = onAudioFocusModeChange,
                                    shapes = ListItemDefaults.segmentedShapes(manualStartIdx, focusItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.allow_ducking),
                                    subtitle = stringResource(R.string.allow_ducking_desc),
                                    checked = allowDucking,
                                    onCheckedChange = onAllowDuckingChange,
                                    shapes = ListItemDefaults.segmentedShapes(manualStartIdx + 1, focusItemsCount)
                                )
                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.pause_on_noisy),
                                    subtitle = stringResource(R.string.pause_on_noisy_desc),
                                    checked = pauseOnNoisy,
                                    onCheckedChange = onPauseOnNoisyChange,
                                    shapes = ListItemDefaults.segmentedShapes(manualStartIdx + 2, focusItemsCount)
                                )
                            }
                        }

                        // 淡入淡出设置（仅 ExoPlayer 支持，Flick 引擎不支持）
                        if (audioEngine != 1) {
                            SettingsSection(title = stringResource(R.string.fade_mode)) {
                                val fadeModes = listOf(
                                    stringResource(R.string.fade_mode_crossfade),
                                    stringResource(R.string.fade_mode_single),
                                    stringResource(R.string.fade_mode_off)
                                )
                                val fadeItemCount = if (fadeMode == 2) 1 else 2

                                ExpressiveDropdownItem(
                                    title = stringResource(R.string.fade_mode),
                                    subtitle = fadeModes.getOrElse(fadeMode) { fadeModes[0] },
                                    options = fadeModes,
                                    selectedIndex = fadeMode,
                                    onSelect = onFadeModeChange,
                                    shapes = ListItemDefaults.segmentedShapes(0, fadeItemCount)
                                )

                                if (fadeMode != 2) {
                                    ExpressiveSliderItem(
                                        title = stringResource(R.string.crossfade_duration, fadeDuration.toInt()),
                                        value = fadeDuration,
                                        onValueChange = onFadeChange,
                                        valueRange = 0f..10f,
                                        steps = 10,
                                        shapes = ListItemDefaults.segmentedShapes(1, fadeItemCount)
                                    )
                                }
                            }
                        }

                        // Flick 引擎专属设置
                        if (audioEngine == 1) {
                            SettingsSection(title = stringResource(R.string.engine_flick)) {
                                val dsdModes = listOf("PCM Decimation", "DoP", "Native DSD", "Auto")
                                ExpressiveDropdownItem(
                                    title = stringResource(R.string.dsd_output_mode),
                                    subtitle = dsdModes.getOrElse(dsdOutputMode) { dsdModes[0] },
                                    options = dsdModes,
                                    selectedIndex = dsdOutputMode,
                                    onSelect = onDsdOutputModeChange,
                                    shapes = ListItemDefaults.segmentedShapes(0, 4)
                                )

                                ExpressiveSwitchItem(
                                    title = stringResource(R.string.dap_bit_perfect),
                                    subtitle = stringResource(R.string.dap_bit_perfect_desc),
                                    checked = dapBitPerfect,
                                    onCheckedChange = onDapBitPerfectChange,
                                    shapes = ListItemDefaults.segmentedShapes(1, 4)
                                )

                                ExpressiveSwitchItem(
                                    title = stringResource(id = R.string.usb_exclusive_title),
                                    subtitle = stringResource(id = R.string.usb_exclusive_subtitle),
                                    checked = usbExclusive,
                                    onCheckedChange = onUsbExclusiveChange,
                                    shapes = ListItemDefaults.segmentedShapes(2, 4)
                                )

                                ExpressiveClickItem(
                                    title = stringResource(R.string.copy_rust_debug_state),
                                    subtitle = stringResource(R.string.copy_rust_debug_state_desc),
                                    onClick = {
                                        val state = cp.player.engine.RustEngine.getRustAudioDebugStateJson()
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Rust Debug State", state)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shapes = ListItemDefaults.segmentedShapes(3, 4)
                                )
                            }
                        }

                        SettingsSection(title = stringResource(R.string.dsp_effects)) {
                            ExpressiveClickItem(
                                title = stringResource(R.string.dsp_equalizer),
                                subtitle = stringResource(R.string.dsp_equalizer_desc),
                                icon = { MonetIcon(Icons.Default.Tune, Color(0xFFE8EAF6), Color(0xFF283593)) },
                                onClick = { currentScreenId = SettingsPage.Dsp.id },
                                shapes = ListItemDefaults.segmentedShapes(0, 1)
                            )
                        }

                        // 流媒体音质设置
                        SettingsSection(title = stringResource(R.string.playback_quality_cat)) {
                            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
                            val qualityLabels = listOf(
                                stringResource(R.string.quality_standard),
                                stringResource(R.string.quality_higher),
                                stringResource(R.string.quality_exhigh),
                                stringResource(R.string.quality_lossless),
                                stringResource(R.string.quality_hires)
                            )

                            ExpressiveDropdownItem(
                                title = stringResource(R.string.wifi_quality_label),
                                subtitle = qualityLabels.getOrElse(qualities.indexOf(currentQualityWifi).coerceAtLeast(0)) { currentQualityWifi },
                                options = qualityLabels,
                                selectedIndex = qualities.indexOf(currentQualityWifi).coerceAtLeast(0),
                                onSelect = { onQualityWifiChange(qualities[it]) },
                                shapes = ListItemDefaults.segmentedShapes(0, 2)
                            )

                            ExpressiveDropdownItem(
                                title = stringResource(R.string.cellular_quality_label),
                                subtitle = qualityLabels.getOrElse(qualities.indexOf(currentQualityCellular).coerceAtLeast(0)) { currentQualityCellular },
                                options = qualityLabels,
                                selectedIndex = qualities.indexOf(currentQualityCellular).coerceAtLeast(0),
                                onSelect = { onQualityCellularChange(qualities[it]) },
                                shapes = ListItemDefaults.segmentedShapes(1, 2)
                            )
                        }

                    }
                    SettingsPage.UiLogic -> {
                        // 播放行为
                        SettingsSection(title = stringResource(R.string.playback_behavior)) {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.play_immediately),
                                subtitle = stringResource(R.string.play_immediately_desc),
                                checked = playImmediately,
                                onCheckedChange = onPlayImmediatelyChange,
                                shapes = ListItemDefaults.segmentedShapes(0, 3)
                            )
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.usb_audio_auto_resume),
                                subtitle = stringResource(R.string.usb_audio_auto_resume_desc),
                                checked = autoResumeUsbAudio,
                                onCheckedChange = onAutoResumeUsbAudioChange,
                                shapes = ListItemDefaults.segmentedShapes(1, 3)
                            )
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.restore_last_queue),
                                subtitle = stringResource(R.string.restore_last_queue_desc),
                                checked = restoreLastQueue,
                                onCheckedChange = onRestoreLastQueueChange,
                                shapes = ListItemDefaults.segmentedShapes(2, 3)
                            )
                        }

                        // 歌词来源设置
                        SettingsSection(title = stringResource(R.string.lyrics)) {
                            val lyricsSourceOptions = listOf(
                                stringResource(R.string.lyrics_source_provider_api),
                                stringResource(R.string.lyrics_source_amll_ttml),
                                stringResource(R.string.lyrics_source_amll_only)
                            )
                            val amllPlatformOptions = listOf(
                                "auto" to stringResource(R.string.auto_detect),
                                "ncm" to stringResource(R.string.netease_cloud_music),
                                "qq" to stringResource(R.string.qq_music),
                                "am" to "Apple Music",
                                "spotify" to "Spotify"
                            )
                            val lyricsItemCount = if (lyricsSource == 0) 1 else 2

                            ExpressiveDropdownItem(
                                title = stringResource(R.string.lyrics_source),
                                subtitle = lyricsSourceOptions.getOrElse(lyricsSource) { lyricsSourceOptions[0] },
                                options = lyricsSourceOptions,
                                selectedIndex = lyricsSource,
                                onSelect = onLyricsSourceChange,
                                shapes = ListItemDefaults.segmentedShapes(0, lyricsItemCount)
                            )

                            if (lyricsSource != 0) {
                                ExpressiveDropdownItem(
                                    title = stringResource(R.string.amll_lyrics_platform),
                                    subtitle = amllPlatformOptions.find { it.first == amllPlatform }?.second ?: stringResource(R.string.auto_detect),
                                    options = amllPlatformOptions.map { it.second },
                                    selectedIndex = amllPlatformOptions.indexOfFirst { it.first == amllPlatform }.coerceAtLeast(0),
                                    onSelect = { onAmllPlatformChange(amllPlatformOptions[it].first) },
                                    shapes = ListItemDefaults.segmentedShapes(1, lyricsItemCount)
                                )
                            }
                        }

                        // 导航栏行为
                        SettingsSection(title = stringResource(R.string.navigation_bar)) {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.hide_navbar_on_scroll),
                                subtitle = stringResource(R.string.hide_navbar_on_scroll_desc),
                                checked = hideNavbarOnScroll,
                                onCheckedChange = onHideNavbarOnScrollChange,
                                shapes = ListItemDefaults.segmentedShapes(0, 1)
                            )
                        }

                        // 播放器样式
                        SettingsSection(title = stringResource(R.string.player_style)) {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.wavy_progress),
                                subtitle = stringResource(R.string.wavy_progress_desc),
                                checked = wavyProgress,
                                onCheckedChange = onWavyProgressChange,
                                shapes = ListItemDefaults.segmentedShapes(0, 1)
                            )
                        }
                    }
                    SettingsPage.StorageDownload -> {
                        // Merged Storage & Download Section
                        SettingsSection(title = "${stringResource(R.string.storage_cache)} & ${stringResource(R.string.download_settings)}") {
                            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
                            val qualityLabels = listOf(
                                stringResource(R.string.quality_standard),
                                stringResource(R.string.quality_higher),
                                stringResource(R.string.quality_exhigh),
                                stringResource(R.string.quality_lossless),
                                stringResource(R.string.quality_hires)
                            )

                            ExpressiveSwitchItem(
                                title = stringResource(R.string.cellular_caching),
                                subtitle = stringResource(R.string.cellular_caching_desc),
                                checked = useCellularCache,
                                onCheckedChange = onUseCellularCacheChange,
                                shapes = ListItemDefaults.segmentedShapes(0, 6)
                            )

                            ExpressiveSliderItem(
                                title = stringResource(R.string.max_cache_size, cacheSize),
                                value = cacheSize.toFloat(),
                                onValueChange = { onCacheSizeChange(it.toInt()) },
                                valueRange = 100f..2048f,
                                steps = 19,
                                shapes = ListItemDefaults.segmentedShapes(1, 6)
                            )

                            ExpressiveDropdownItem(
                                title = stringResource(R.string.download_quality_label),
                                subtitle = qualityLabels.getOrElse(qualities.indexOf(downloadQuality).coerceAtLeast(0)) { downloadQuality },
                                options = qualityLabels,
                                selectedIndex = qualities.indexOf(downloadQuality).coerceAtLeast(0),
                                onSelect = { onDownloadQualityChange(qualities[it]) },
                                shapes = ListItemDefaults.segmentedShapes(2, 6)
                            )

                            ExpressiveSwitchItem(
                                title = stringResource(R.string.allow_cellular_download),
                                subtitle = stringResource(R.string.allow_cellular_download_desc),
                                checked = allowCellularDownload,
                                onCheckedChange = onAllowCellularDownloadChange,
                                shapes = ListItemDefaults.segmentedShapes(3, 6)
                            )

                            ExpressiveClickItem(
                                title = stringResource(R.string.download_dir),
                                subtitle = downloadDir?.substringAfterLast("%2F") ?: stringResource(R.string.system_music_folder),
                                onClick = {
                                    try {
                                        dirPicker.launch(null)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.no_file_manager_found),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shapes = ListItemDefaults.segmentedShapes(4, 6)
                            )
                            
                            ExpressiveButtonItem(
                                text = stringResource(R.string.clear_cache),
                                onClick = onClearCache,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shapes = ListItemDefaults.segmentedShapes(5, 6)
                            )
                        }
                    }
                    SettingsPage.Provider -> {
                        SettingsSection(title = stringResource(R.string.provider_management)) {
                            val providers by cp.player.provider.ModuleManager.providersFlow.collectAsState()
                            val coroutineScope = rememberCoroutineScope()
                            // 导入模块的 launcher
                            val importLauncher = rememberLauncherForActivityResult(
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
                                                val errorMsg = cp.player.provider.ModuleManager.lastLoadError
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    if (success) {
                                                        cp.player.provider.ProviderManager.startServer(context)
                                                        android.widget.Toast.makeText(context, context.getString(R.string.module_import_success), android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val msg = if (!errorMsg.isNullOrBlank()) {
                                                            context.getString(R.string.module_import_failed_detail, errorMsg)
                                                        } else {
                                                            context.getString(R.string.module_import_failed)
                                                        }
                                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
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
                            // 手动更新模块的 launcher
                            var updatingProviderId by remember { mutableStateOf<String?>(null) }
                            val updateLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent()
                            ) { uri ->
                                uri?.let { selectedUri ->
                                    val targetId = updatingProviderId ?: return@let
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                                                val tempFile = java.io.File(context.cacheDir, "temp_update.zip")
                                                java.io.FileOutputStream(tempFile).use { output ->
                                                    input.copyTo(output)
                                                }
                                                val success = cp.player.provider.ModuleManager.updateModule(context, targetId, tempFile)
                                                val errorMsg = cp.player.provider.ModuleManager.lastLoadError
                                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    if (success) {
                                                        cp.player.provider.ProviderManager.startServer(context)
                                                        android.widget.Toast.makeText(context, context.getString(R.string.module_update_success), android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val msg = if (!errorMsg.isNullOrBlank()) {
                                                            context.getString(R.string.module_update_error, errorMsg)
                                                        } else {
                                                            context.getString(R.string.module_update_failed)
                                                        }
                                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        } catch(e: Exception) {
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.module_update_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }

                            // 提供者更多操作底部面板状态
                            var selectedProvider by remember { mutableStateOf<cp.player.provider.BackendProvider?>(null) }
                            // 自动更新检查结果（provider id → UpdateInfo）
                            var updateResults by remember { mutableStateOf<Map<String, cp.player.provider.ProviderUpdateChecker.UpdateInfo>>(emptyMap()) }

                            val loginViewModel: LoginViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                            val currentProviders by cp.player.provider.ModuleManager.providersFlow.collectAsState()

                            // 进入 provider 页面时自动后台检查所有有 updateUrl 的模块更新
                            LaunchedEffect(currentProviders) {
                                val toCheck = currentProviders.filter { !it.updateUrl.isNullOrBlank() }
                                toCheck.forEach { provider ->
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val info = cp.player.provider.ProviderUpdateChecker.checkUpdate(
                                            provider.updateUrl!!, provider.version
                                        )
                                        if (info != null) {
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                updateResults = updateResults + (provider.id to info)
                                            }
                                        }
                                    }
                                }
                            }

                            currentProviders.forEachIndexed { index, provider ->
                                val isCurrent = cp.player.provider.ProviderManager.currentProvider?.id == provider.id
                                val activeStr = if (isCurrent) stringResource(R.string.provider_active) else ""
                                val hasUpdate = updateResults.containsKey(provider.id)

                                cp.player.ui.component.UnifiedListItem(
                                    onClick = {
                                        loginViewModel.switchProvider(provider)
                                        android.widget.Toast.makeText(context, context.getString(R.string.provider_switched, provider.name), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shapes = ListItemDefaults.segmentedShapes(index, currentProviders.size + 1),
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                provider.name + activeStr,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp
                                            )
                                            if (hasUpdate) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        stringResource(R.string.has_update),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    supportingContent = {
                                        if (hasUpdate) {
                                            val updateInfo = updateResults[provider.id]!!
                                            Text(
                                                "${stringResource(R.string.provider_info, provider.type, provider.version)}  →  v${updateInfo.version}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Normal
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.provider_info, provider.type, provider.version),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        // 更多按钮（参考 SongItem 的 MoreVert 按钮样式）
                                        Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = if (hasUpdate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable { selectedProvider = provider }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.more_actions),
                                                    tint = if (hasUpdate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 68.dp),
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
                                    )
                                )
                            }

                            ExpressiveButtonItem(
                                text = stringResource(R.string.import_new_module),
                                onClick = { importLauncher.launch("application/zip") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shapes = ListItemDefaults.segmentedShapes(currentProviders.size, currentProviders.size + 1)
                            )

                            // 提供者操作底部面板
                            selectedProvider?.let { provider ->
                                cp.player.ui.component.ProviderOptionsBottomSheet(
                                    provider = provider,
                                    onDismissRequest = { selectedProvider = null },
                                    onDeleted = {
                                        selectedProvider = null
                                        updateResults = updateResults - provider.id
                                    },
                                    onUpdated = {
                                        selectedProvider = null
                                        updateResults = updateResults - provider.id
                                    },
                                    onUpdateZipSelected = {
                                        updatingProviderId = provider.id
                                        updateLauncher.launch("application/zip")
                                    },
                                    onSettingsSelected = {
                                        currentProviderSettingsId = provider.id
                                        currentScreenId = SettingsPage.ProviderSettings.id
                                    },
                                    preCheckedUpdate = updateResults[provider.id]
                                )
                            }
                        }
                    }
                    SettingsPage.ProviderSettings -> {
                        ProviderSettingsScreen(
                            providerId = currentProviderSettingsId,
                            onNavigateBack = { currentScreenId = SettingsPage.ProviderSettings.parent?.id ?: SettingsPage.Main.id }
                        )
                    }
                    SettingsPage.About -> {
                        // 关于
                        AboutScreenInline()
                    }
                    SettingsPage.Sponsor -> {
                        // 赞助
                        SponsorScreen()
                    }
                    SettingsPage.Debug -> {
                        // Debug
                        SettingsSection(title = stringResource(R.string.debug)) {
                            val logsCopiedMsg = stringResource(R.string.logs_copied)
                            ExpressiveClickItem(
                                title = stringResource(R.string.app_logs),
                                subtitle = stringResource(R.string.view_app_and_system_logs),
                                onClick = { currentScreenId = SettingsPage.LogViewer.id },
                                shapes = ListItemDefaults.segmentedShapes(0, 3)
                            )
                            ExpressiveClickItem(
                                title = stringResource(R.string.health_status),
                                subtitle = stringResource(R.string.api_monitor_desc),
                                onClick = { currentScreenId = SettingsPage.Health.id },
                                shapes = ListItemDefaults.segmentedShapes(1, 3)
                            )
                            ExpressiveButtonItem(
                                text = stringResource(R.string.copy_debug_logs),
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val clip = ClipData.newPlainText("CP Player Logs", LogManager.getAllLogsString())
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, logsCopiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shapes = ListItemDefaults.segmentedShapes(2, 3)
                            )
                        }
                    }
                    SettingsPage.Health -> {
                        // 健康状态 & 调试界面 - 内嵌在设置中
                        cp.player.ui.screen.HealthScreenInline()
                    }
                    SettingsPage.LogViewer -> {
                        // 日志查看器 - 内嵌在设置中
                        cp.player.ui.screen.LogViewerInline()
                    }
                    SettingsPage.Dsp -> {
                        // DSP & Equalizer
                        cp.player.ui.screen.DspSettingsScreen(
                            onNavigateBack = { currentScreenId = SettingsPage.Dsp.parent?.id ?: SettingsPage.Main.id }
                        )
                    }
                    else -> {
                        // Do nothing or handle fallback
                    }
                }
                Spacer(modifier = Modifier.height(32.dp + bottomContentPadding.calculateBottomPadding()))
            }
        }
            } // Box (AnimatedContent 容器)
        } // Row
    } // AppScaffold
} // SettingsScreen

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
fun MonetIcon(icon: ImageVector, containerColor: Color, contentColor: Color) {
    // Increased icon size slightly
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun ExpressiveClickItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    shapes: androidx.compose.material3.ListItemShapes
) {
    cp.player.ui.component.UnifiedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = icon,
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        supportingContent = if (!subtitle.isNullOrEmpty()) { { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal) } } else null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        colors = ListItemDefaults.colors(containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: androidx.compose.material3.ListItemShapes
) {
    cp.player.ui.component.UnifiedListItem(
    onClick = { onCheckedChange(!checked) },
        shapes = shapes,
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
            
            ,
        colors = ListItemDefaults.colors(containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveDropdownItem(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shapes: androidx.compose.material3.ListItemShapes
) {
    var showDialog by remember { mutableStateOf(false) }
    
    cp.player.ui.component.UnifiedListItem(
    onClick = { showDialog = true },
        shapes = shapes,
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
            
            ,
        colors = ListItemDefaults.colors(containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
    )

    if (showDialog) {
        StyledModalBottomSheet(onDismissRequest = { showDialog = false }) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun ExpressiveSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    shapes: androidx.compose.material3.ListItemShapes
) {
    cp.player.ui.component.UnifiedListItem(
        shapes = shapes,
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
            ,
        colors = ListItemDefaults.colors(containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ExpressiveButtonItem(
    text: String,
    onClick: () -> Unit,
    containerColor: Color = Color.Unspecified, 
    contentColor: Color = Color.Unspecified,
    shapes: androidx.compose.material3.ListItemShapes
) {
    val finalContainerColor = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surface else containerColor
    val finalContentColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor

    cp.player.ui.component.UnifiedListItem(
        onClick = onClick,
        shapes = shapes,
        headlineContent = { Text(text, color = finalContentColor, fontWeight = FontWeight.Medium, fontSize = 16.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        colors = ListItemDefaults.colors(containerColor = finalContainerColor)
    )
}