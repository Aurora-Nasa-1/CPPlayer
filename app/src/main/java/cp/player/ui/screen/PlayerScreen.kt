package cp.player.ui.screen

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Comment
import cp.player.model.LyricLine
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.ui.component.*
import cp.player.ui.theme.createCustomColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import cp.player.util.ImageUtils
import cp.player.viewmodel.PlaybackViewModel
import cp.player.ui.component.AppScaffold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun PlayerScreen(
    song: Song?,
    lyrics: List<LyricLine> = emptyList(),
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    shuffleMode: Boolean = false,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    allPlaylists: List<Playlist> = emptyList(),
    onLikeClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onLyricClick: () -> Unit = {},
    onAddToPlaylist: (String, Long) -> Unit = { _, _ -> },
    queue: List<Song> = emptyList(),
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = { _ -> },
    onClearQueue: () -> Unit = {},
    qualityWifi: String = "Unknown",
    qualityCellular: String = "Unknown",
    sampleRate: Int = 0,
    bitrate: Int = 0,
    hotComments: List<Comment> = emptyList(),
    newestComments: List<Comment> = emptyList(),
    commentTotal: Int = 0,
    isCommentsLoading: Boolean = false,
    hasMoreComments: Boolean = true,
    commentSortType: Int = 1,
    onLoadMoreComments: () -> Unit = {},
    onLikeComment: (Comment) -> Unit = {},
    onReplyComment: (Comment) -> Unit = {},
    onPostComment: (String) -> Unit = {},
    onAvatarClick: (Long) -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onCommentSortChange: (Int) -> Unit = {},
    onViewFloorClick: (Comment) -> Unit = {},
    floorComments: List<Comment> = emptyList(),
    floorCommentTotal: Int = 0,
    floorHasMore: Boolean = false,
    onLoadMoreFloor: (Comment) -> Unit = {},
    onDismissFloor: () -> Unit = {},
    activeParentComment: Comment? = null,
    sleepTimerRemaining: Long = 0L,
    onSetSleepTimer: (Int) -> Unit = {},
    useCoverColor: Boolean = false,
    useFluidBackground: Boolean = false,
    useWavyProgress: Boolean = true,
    coverColor: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val activity = remember(context) {
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) break
            c = c.baseContext
        }
        c as? ComponentActivity
    }
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isWideScreen = windowSizeClass?.let { it.widthSizeClass != WindowWidthSizeClass.Compact } ?: false

    if (song == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueBottomSheet by remember { mutableStateOf(false) }
    var showSleepTimerBottomSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showQualityInfoDialog by remember { mutableStateOf(false) }

    if (showSongInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSongInfoDialog = false },
            title = { Text(stringResource(R.string.song_info)) },
            text = {
                Column {
                    Text(stringResource(R.string.title_label, song.name))
                    Text(stringResource(R.string.artist_label, song.artist))
                    Text(stringResource(R.string.album_label, song.album))
                    Text(stringResource(R.string.song_id_label, song.id))
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showQualityInfoDialog) {
        AlertDialog(
            onDismissRequest = { showQualityInfoDialog = false },
            title = { Text(stringResource(R.string.quality_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wifi_quality, qualityWifi), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.cellular_quality, qualityCellular), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text(stringResource(R.string.playback_stats), style = MaterialTheme.typography.titleSmall)
                    if (sampleRate > 0) {
                        Text(stringResource(R.string.sample_rate, sampleRate), style = MaterialTheme.typography.bodyMedium)
                    } else if (sampleRate == 0) {
                        Text(stringResource(R.string.sample_rate_na), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (bitrate > 0) {
                        Text(stringResource(R.string.bitrate, bitrate / 1000), style = MaterialTheme.typography.bodyMedium)
                    } else if (bitrate == 0) {
                        Text(stringResource(R.string.bitrate_na), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val engineType = cp.player.util.UserPreferences.getAudioEngine(context)
                    if (engineType == 1) {
                        HorizontalDivider()
                        Text("Hi-Fi & USB DAC", style = MaterialTheme.typography.titleSmall)
                        val isUsbActive = remember<Boolean> { cp.player.engine.RustEngine.isRustDirectUsbSessionActive() }
                        Text("USB Exclusive: ${if (isUsbActive) "Active" else "Inactive"}", style = MaterialTheme.typography.bodyMedium, color = if (isUsbActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        val hasHwVolume = remember<Boolean> { cp.player.engine.RustEngine.hasRustDirectUsbHardwareVolume() }
                        if (hasHwVolume) {
                            var hwVolume by remember { mutableStateOf(cp.player.engine.RustEngine.getRustDirectUsbHardwareVolume().toFloat()) }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Hardware Volume: ${(hwVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = hwVolume,
                                onValueChange = {
                                    hwVolume = it
                                    cp.player.engine.RustEngine.setRustDirectUsbHardwareVolume(it.toDouble())
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(allPlaylists) { index, p ->
                        cp.player.ui.component.UnifiedListItem(
    onClick = { onAddToPlaylist(song.id, p.id)
                                    showAddToPlaylistDialog = false },
                            headlineContent = { Text(p.name) },
                            modifier = Modifier

                                ,
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showQueueBottomSheet) {
        QueueBottomSheet(
            queue = queue,
            currentSongId = song.id,
            onMove = onMoveQueueItem,
            onRemove = onRemoveQueueItem,
            onClear = onClearQueue,
            onClose = { showQueueBottomSheet = false }
        )
    }

    if (activeParentComment != null) {
        FloorCommentBottomSheet(
            parentComment = activeParentComment,
            replies = floorComments,
            totalCount = floorCommentTotal,
            isLoading = isCommentsLoading,
            hasMore = floorHasMore,
            onLoadMore = { onLoadMoreFloor(activeParentComment) },
            onLikeClick = onLikeComment,
            onReplyClick = onReplyComment,
            onPostComment = onPostComment,
            onAvatarClick = onAvatarClick,
            useCoverColor = useCoverColor,
            coverColor = coverColor,
            onDismiss = onDismissFloor
        )
    }

    if (showSleepTimerBottomSheet) {
        SleepTimerBottomSheet(
            remainingTime = sleepTimerRemaining,
            onSetTimer = onSetSleepTimer,
            onDismiss = { showSleepTimerBottomSheet = false }
        )
    }

    val playerColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme())
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = playerColorScheme) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.surface
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface
                )
            )
        }

        val view = LocalView.current
        if (!view.isInEditMode) {
            val isDarkTheme = isSystemInDarkTheme()
            val luminance = coverColor?.let { ColorUtils.calculateLuminance(it) } ?: 0.0
            val isAppearanceLightStatusBars = if (useFluidBackground) {
                !isDarkTheme
            } else {
                !isDarkTheme && (luminance > 0.5)
            }

            SideEffect {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
                }
            }
        }

        val coroutineScope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        val maxDrag = with(androidx.compose.ui.platform.LocalDensity.current) { 400.dp.toPx() }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    val delta = available.y
                    if (delta < 0 && offsetY.value > 0) {
                        val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                        val consumed = offsetY.value - newOffset
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                        return androidx.compose.ui.geometry.Offset(0f, -consumed)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    val delta = available.y
                    if (delta > 0) {
                        val newOffset = (offsetY.value + delta).coerceAtMost(maxDrag * 2)
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                        return androidx.compose.ui.geometry.Offset(0f, delta)
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offsetY.value > 0) {
                        val targetOffset = if (offsetY.value > maxDrag / 2 || available.y > 1000f) {
                            maxDrag * 2
                        } else {
                            0f
                        }
                        if (targetOffset > 0f) {
                            onBackPressed()
                            coroutineScope.launch {
                                offsetY.animateTo(targetOffset, tween(300))
                            }
                        } else {
                            coroutineScope.launch {
                                offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY.value > 0) {
                                val targetOffset = if (offsetY.value > maxDrag / 2) maxDrag * 2 else 0f
                                if (targetOffset > 0f) {
                                    onBackPressed()
                                    coroutineScope.launch {
                                        offsetY.animateTo(targetOffset, tween(300))
                                    }
                                } else {
                                    coroutineScope.launch {
                                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            if (offsetY.value > 0) {
                                coroutineScope.launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0 || offsetY.value > 0) {
                                val newOffset = (offsetY.value + dragAmount).coerceIn(0f, maxDrag * 2)
                                coroutineScope.launch { offsetY.snapTo(newOffset) }
                                change.consume()
                            }
                        }
                    )
                }
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .then(
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "player_container"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                ),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (useCoverColor && coverColor != null && useFluidBackground) {
                    FluidBackground(
                        color = Color(coverColor),
                        isDark = isSystemInDarkTheme()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(bgBrush))
                }
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { },
                            navigationIcon = {
                                IconButton(onClick = { backDispatcher?.onBackPressed() ?: onBackPressed() }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(id = R.string.back),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showQueueBottomSheet = true },
                                    modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = "Queue",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                                PlayerWideLayout(
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    song = song,
                                lyrics = lyrics,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = currentPosition,
                                duration = duration,
                                isFavorite = isFavorite,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                bitrate = bitrate,
                                sampleRate = sampleRate,
                                isDownloaded = isDownloaded,
                                useWavyProgress = useWavyProgress,
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onSeek = onSeek,
                                onLikeClick = onLikeClick,
                                onArtistClick = onArtistClick,
                                onShuffleClick = onShuffleClick,
                                onRepeatClick = onRepeatClick,
                                onCommentClick = {
                                    onCommentClick()
                                },
                                onQueueClick = { showQueueBottomSheet = true },
                                onMoreClick = { showMoreMenu = true },
                                showMoreMenu = showMoreMenu,
                                onDismissMore = { showMoreMenu = false },
                                onAddToPlaylist = { showAddToPlaylistDialog = true },
                                onDownloadClick = onDownloadClick,
                                onSleepTimerClick = { showSleepTimerBottomSheet = true },
                                onInfoClick = { showSongInfoDialog = true },
                                onQualityClick = { showQualityInfoDialog = true },
                                onDislikeClick = onDislikeClick
                            )
                        } else {
                                PlayerMobileLayout(
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    song = song,
                                lyrics = lyrics,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = currentPosition,
                                duration = duration,
                                isFavorite = isFavorite,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                bitrate = bitrate,
                                sampleRate = sampleRate,
                                isDownloaded = isDownloaded,
                                useWavyProgress = useWavyProgress,
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onSeek = onSeek,
                                onLikeClick = onLikeClick,
                                onArtistClick = onArtistClick,
                                onShuffleClick = onShuffleClick,
                                onRepeatClick = onRepeatClick,
                                onCommentClick = {
                                    onCommentClick()
                                },
                                onQueueClick = { showQueueBottomSheet = true },
                                onMoreClick = { showMoreMenu = true },
                                showMoreMenu = showMoreMenu,
                                onDismissMore = { showMoreMenu = false },
                                onAddToPlaylist = { showAddToPlaylistDialog = true },
                                onDownloadClick = onDownloadClick,
                                onSleepTimerClick = { showSleepTimerBottomSheet = true },
                                onInfoClick = { showSongInfoDialog = true },
                                onQualityClick = { showQualityInfoDialog = true },
                                onDislikeClick = onDislikeClick,
                                onLyricClick = onLyricClick,
                                hotComments = hotComments,
                                newestComments = newestComments,
                                commentTotal = commentTotal,
                                isCommentsLoading = isCommentsLoading,
                                hasMoreComments = hasMoreComments,
                                commentSortType = commentSortType,
                                onLoadMoreComments = onLoadMoreComments,
                                onLikeComment = onLikeComment,
                                onReplyComment = onReplyComment,
                                onPostComment = onPostComment,
                                onAvatarClick = onAvatarClick,
                                onCommentSortChange = onCommentSortChange,
                                onViewFloorClick = onViewFloorClick
                            )
                        }
                    }
                }
            }
        }
    }
}

    @Composable
    fun AudioQualityBadge(sampleRate: Int, bitrate: Int) {
        if (sampleRate > 0 || bitrate > 0) {
            val isDsd = sampleRate >= 2822400
            val text = if (isDsd) {
                "DSD \${sampleRate / 1000}kHz"
            } else if (sampleRate > 0) {
                "\${sampleRate / 1000}kHz" + if (bitrate > 0) " | \${bitrate / 1000}kbps" else ""
            } else {
                "\${bitrate / 1000}kbps"
            }
            
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerWideLayout(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    song: Song,
    lyrics: List<LyricLine>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    isFavorite: Boolean,
    shuffleMode: Boolean,
    repeatMode: Int,
    bitrate: Int,
    sampleRate: Int,
    isDownloaded: Boolean,
    useWavyProgress: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCommentClick: () -> Unit,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreMenu: Boolean,
    onDismissMore: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onInfoClick: () -> Unit,
    onQualityClick: () -> Unit,
    onDislikeClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Album Art & Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cover Art
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val size = if (maxWidth < maxHeight) maxWidth else maxHeight
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.size(size),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 16.dp
                ) {
                    if (song.albumArtUrl != null) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 800),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "player_cover"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
                                            )
                                        }
                                    } else Modifier
                                )
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(128.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls section
            Column(
                modifier = Modifier.fillMaxWidth(0.95f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.clickable { song.artistId?.let { onArtistClick(it) } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onCommentClick, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = onLikeClick, modifier = Modifier.size(48.dp)) {
                            AnimatedContent(
                                targetState = isFavorite,
                                label = "LikeAnimation",
                                transitionSpec = {
                                    scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) togetherWith
                                            scaleOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                                }
                            ) { targetFavorite ->
                                Icon(
                                    if (targetFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    modifier = Modifier.size(32.dp),
                                    tint = if (targetFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { onSeek((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Main Playback Controls Wide
                cp.player.ui.component.PlaybackControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    sideButtonModifier = Modifier.weight(1f).height(80.dp),
                    centerButtonModifier = Modifier.weight(1.2f).height(100.dp),
                    sideIconSize = 36.dp,
                    centerIconSize = 56.dp
                )

                // Secondary Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onShuffleClick,
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.size(24.dp),
                                tint = if (shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(
                            onClick = onRepeatClick,
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            val icon = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            }
                            Icon(
                                icon,
                                contentDescription = "Repeat",
                                modifier = Modifier.size(24.dp),
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            onClick = onQueueClick,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Queue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                        Box {
                            IconButton(
                                onClick = onMoreClick,
                                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp))
                            }
                            if (showMoreMenu) {
                                cp.player.ui.component.PlayerSongOptionsBottomSheet(
                                    song = song,
                                    isDownloaded = isDownloaded,
                                    qualityWifi = "Unknown",
                                    qualityCellular = "Unknown",
                                    sampleRate = sampleRate,
                                    bitrate = bitrate,
                                    onPlaylistClick = {
                                        onAddToPlaylist()
                                        onDismissMore()
                                    },
                                    onDownloadClick = {
                                        onDownloadClick()
                                        onDismissMore()
                                    },
                                    onSleepTimerClick = {
                                        onSleepTimerClick()
                                        onDismissMore()
                                    },
                                    onDislikeClick = {
                                        onDislikeClick()
                                        onDismissMore()
                                    },
                                    onDismissRequest = onDismissMore
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Side: Lyrics
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            LyricContent(
                lyrics = lyrics,
                currentPosition = currentPosition,
                contentPadding = PaddingValues(vertical = 120.dp, horizontal = 24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlayerMobileLayout(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    song: Song,
    lyrics: List<LyricLine>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    isFavorite: Boolean,
    shuffleMode: Boolean,
    repeatMode: Int,
    bitrate: Int,
    sampleRate: Int,
    isDownloaded: Boolean,
    useWavyProgress: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onCommentClick: () -> Unit,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreMenu: Boolean,
    onDismissMore: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onInfoClick: () -> Unit,
    onQualityClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onLyricClick: () -> Unit,
    hotComments: List<Comment>,
    newestComments: List<Comment>,
    commentTotal: Int,
    isCommentsLoading: Boolean,
    hasMoreComments: Boolean,
    commentSortType: Int,
    onLoadMoreComments: () -> Unit,
    onLikeComment: (Comment) -> Unit,
    onReplyComment: (Comment) -> Unit,
    onPostComment: (String) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onCommentSortChange: (Int) -> Unit,
    onViewFloorClick: (Comment) -> Unit
) {
    var showLyrics by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })

    androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Content Area: Album Art or Lyrics
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!showLyrics) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth(0.95f)
                        .clickable { showLyrics = true },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shadowElevation = 16.dp
                ) {
                    if (song.albumArtUrl != null) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 800),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .then(
                                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "player_cover"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
                                            )
                                        }
                                    } else Modifier
                                )
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(128.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showLyrics = false }
                ) {
                    LyricContent(
                        lyrics = lyrics,
                        currentPosition = currentPosition,
                        contentPadding = PaddingValues(vertical = 48.dp)
                    )
                }
            }
        }

        // Song Information Row (Title/Artist left, Lyric/Action right)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.clickable { song.artistId?.let { onArtistClick(it) } }
                )
            }
            
            IconButton(
                onClick = onLyricClick,
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", modifier = Modifier.size(24.dp))
            }
        }

        // Progress Bar
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (useWavyProgress) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(currentPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    formatTime(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Audio Quality Badge (Pill)
        if (sampleRate > 0 || bitrate > 0) {
            val isDsd = sampleRate >= 2822400
            val qualityText = if (isDsd) {
                "DSD \${sampleRate / 1000} kHz"
            } else if (sampleRate > 0) {
                "\${sampleRate / 1000.0} kHz • \${bitrate / 1000} kbps • FLAC"
            } else {
                "\${bitrate / 1000} kbps"
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = qualityText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Main Playback Controls
        cp.player.ui.component.PlaybackControls(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            sideButtonModifier = Modifier.weight(1f).height(72.dp),
            centerButtonModifier = Modifier.weight(1.2f).height(72.dp),
            sideIconSize = 36.dp,
            centerIconSize = 40.dp
        )

        // Bottom Action Area (Dark Pill Container)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback mode toggle (Combine shuffle/repeat into one logic if we had a dedicated combined method, but here we just rotate onRepeatClick or onShuffleClick. Let's just cycle repeatMode.)
                IconButton(
                    onClick = onRepeatClick
                ) {
                    val icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Shuffle
                    }
                    Icon(icon, contentDescription = "Mode", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = onLikeClick) {
                    AnimatedContent(targetState = isFavorite, label = "LikeAnimation") { targetFavorite ->
                        Icon(
                            if (targetFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            modifier = Modifier.size(28.dp),
                            tint = if (targetFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showMoreMenu) {
                        cp.player.ui.component.PlayerSongOptionsBottomSheet(
                            song = song,
                            isDownloaded = isDownloaded,
                            qualityWifi = "Unknown",
                            qualityCellular = "Unknown",
                            sampleRate = sampleRate,
                            bitrate = bitrate,
                            onPlaylistClick = {
                                onAddToPlaylist()
                                onDismissMore()
                            },
                            onDownloadClick = {
                                onDownloadClick()
                                onDismissMore()
                            },
                            onSleepTimerClick = {
                                onSleepTimerClick()
                                onDismissMore()
                            },
                            onDislikeClick = {
                                onDislikeClick()
                                onDismissMore()
                            },
                            onDismissRequest = onDismissMore
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
        } else if (page == 1) {
            LaunchedEffect(Unit) {
                onCommentClick()
            }
            CommentPage(
                hotComments = hotComments,
                newestComments = newestComments,
                totalCount = commentTotal,
                isLoading = isCommentsLoading,
                hasMore = hasMoreComments,
                currentSort = commentSortType,
                onLoadMore = {
                    onLoadMoreComments()
                },
                onLikeClick = onLikeComment,
                onReplyClick = { comment -> onReplyComment(comment) },
                onPostComment = onPostComment,
                onAvatarClick = onAvatarClick,
                onSortChange = onCommentSortChange,
                onViewFloorClick = onViewFloorClick
            )
        }
    }
}



@Composable
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%d:%02d", minutes, seconds)
}
