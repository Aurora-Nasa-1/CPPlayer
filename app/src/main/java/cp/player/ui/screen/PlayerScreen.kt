package cp.player.ui.screen

import cp.player.util.formatAsTime
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.*
import cp.player.ui.component.*
import cp.player.util.resized
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
    uiState: PlayerUiState,
    callbacks: PlayerCallbacks,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    // 从聚合状态中解构常用字段
    val song = uiState.song
    val isPlaying = uiState.isPlaying
    val isBuffering = uiState.isBuffering
    val currentPosition = uiState.currentPosition
    val duration = uiState.duration
    val repeatMode = uiState.repeatMode
    val shuffleMode = uiState.shuffleMode
    val isFavorite = uiState.isFavorite
    val isDownloaded = uiState.isDownloaded
    val syncedLyrics = uiState.syncedLyrics
    val lyricsInfo = uiState.lyricsInfo
    val queue = uiState.queue
    val sampleRate = uiState.sampleRate
    val bitrate = uiState.bitrate
    val bitDepth = uiState.bitDepth
    val channels = uiState.channels
    val codecName = uiState.codecName
    val useCoverColor = uiState.useCoverColor
    val useFluidBackground = uiState.useFluidBackground
    val coverColor = uiState.coverColor
    val pureBlack = uiState.pureBlack
    val sleepTimerRemaining = uiState.sleepTimerRemaining
    val allPlaylists = uiState.allPlaylists
    val hotComments = uiState.hotComments
    val newestComments = uiState.newestComments
    val commentTotal = uiState.commentTotal
    val isCommentsLoading = uiState.isCommentsLoading
    val hasMoreComments = uiState.hasMoreComments
    val commentSortType = uiState.commentSortType
    val floorComments = uiState.floorComments
    val floorCommentTotal = uiState.floorCommentTotal
    val floorHasMore = uiState.floorHasMore
    val activeParentComment = uiState.activeParentComment

    val onPlayPause = callbacks.onPlayPause
    val onSkipNext = callbacks.onSkipNext
    val onSkipPrevious = callbacks.onSkipPrevious
    val onSeek = callbacks.onSeek
    val onRepeatClick = callbacks.onRepeatClick
    val onShuffleClick = callbacks.onShuffleClick
    val onLikeClick = callbacks.onLikeClick
    val onArtistClick = callbacks.onArtistClick
    val onDownloadClick = callbacks.onDownloadClick
    val onCommentClick = callbacks.onCommentClick
    val onDislikeClick = callbacks.onDislikeClick
    val onAddToPlaylist = callbacks.onAddToPlaylist
    val onPlayAtQueueIndex = callbacks.onPlayAtQueueIndex
    val onMoveQueueItem = callbacks.onMoveQueueItem
    val onRemoveQueueItem = callbacks.onRemoveQueueItem
    val onClearQueue = callbacks.onClearQueue
    val onLoadMoreComments = callbacks.onLoadMoreComments
    val onLikeComment = callbacks.onLikeComment
    val onReplyComment = callbacks.onReplyComment
    val onPostComment = callbacks.onPostComment
    val onAvatarClick = callbacks.onAvatarClick
    val onCommentSortChange = callbacks.onCommentSortChange
    val onViewFloorClick = callbacks.onViewFloorClick
    val onLoadMoreFloor = callbacks.onLoadMoreFloor
    val onDismissFloor = callbacks.onDismissFloor
    val onSetSleepTimer = callbacks.onSetSleepTimer
    val onBackPressed = callbacks.onBackPressed
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
    // 是否处于播放器页面（非歌词/评论页），用于控制下拉关闭手势
    var isOnPlayerPage by remember { mutableStateOf(true) }
    // HorizontalPager 状态（仅移动端使用，但需要在 TopAppBar 中读取）
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 3 }
    // 翻译开关
    var showTranslation by remember { mutableStateOf(true) }
    if (showSongInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSongInfoDialog = false },
            title = { Text(stringResource(R.string.song_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.title_label, song.name))
                    Text(stringResource(R.string.artist_label, song.artist))
                    Text(stringResource(R.string.album_label, song.album))
                    Text(stringResource(R.string.song_id_label, song.id))

                    if (lyricsInfo != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("歌词信息", style = MaterialTheme.typography.titleSmall)
                        Text("来源: ${lyricsInfo.source}", style = MaterialTheme.typography.bodyMedium)
                        Text("格式: ${lyricsInfo.format}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "逐字歌词: ${if (lyricsInfo.hasWordLevel) "支持 ✓" else "不支持 ✗"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lyricsInfo.hasWordLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lyricsInfo.hasTranslation) {
                            Text("翻译: 有", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (lyricsInfo.hasPhonetic) {
                            Text("音译: 有", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        cp.player.ui.component.AddToPlaylistBottomSheet(
            playlists = allPlaylists,
            onDismissRequest = { showAddToPlaylistDialog = false },
            onPlaylistSelected = { p ->
                onAddToPlaylist(song.id, p.id)
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showQueueBottomSheet) {
        QueueBottomSheet(
            queue = queue,
            currentSongId = song.id,
            onPlayAt = onPlayAtQueueIndex,
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

    CoverThemeWrapper(useCoverColor = useCoverColor, coverColor = coverColor, pureBlack = pureBlack) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 1.0f),
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

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isOnPlayerPage) Modifier.pointerInput(Unit) {
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
                } else Modifier)
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
                            title = {
                                if (!isWideScreen) {
                                    AnimatedContent(
                                        targetState = pagerState.currentPage,
                                        transitionSpec = {
                                            (slideInVertically { h -> h } + fadeIn()) togetherWith
                                                    (slideOutVertically { h -> -h } + fadeOut())
                                        },
                                        label = "TopBarTitle"
                                    ) { page ->
                                        when (page) {
                                            1 -> { /* 播放器页无标题 */ }
                                            else -> {
                                                // 歌词页/评论页标题栏：歌曲名 + 歌手
                                                Column {
                                                    Text(
                                                        song.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        song.artist,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
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
                                if (!isWideScreen) {
                                    AnimatedContent(
                                        targetState = pagerState.currentPage,
                                        label = "TopBarActions"
                                    ) { page ->
                                        when (page) {
                                            0 -> {
                                                // 歌词页：翻译开关
                                                IconButton(
                                                    onClick = { showTranslation = !showTranslation },
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Translate,
                                                        contentDescription = "翻译",
                                                        tint = if (showTranslation) MaterialTheme.colorScheme.onSurface
                                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    )
                                                }
                                            }
                                            else -> {
                                                // 播放器页/评论页：队列按钮
                                                IconButton(
                                                    onClick = { showQueueBottomSheet = true },
                                                    modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                        contentDescription = "Queue",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = { showQueueBottomSheet = true },
                                        modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = "Queue",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
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
                                syncedLyrics = syncedLyrics,
                                lyricsInfo = lyricsInfo,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = currentPosition,
                                duration = duration,
                                isFavorite = isFavorite,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                bitrate = bitrate,
                                sampleRate = sampleRate,
                                bitDepth = bitDepth,
                                channels = channels,
                                codecName = codecName,
                                isDownloaded = isDownloaded,
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
                                onDislikeClick = onDislikeClick
                            )
                        } else {
                                // 歌词自动预加载：切换歌曲时获取歌词（传入标题/歌手用于本地歌曲自动搜索云端绑定）
                                LaunchedEffect(song.id) {
                                    cp.player.lyrics.LyricsManager.fetch(song.id, context, songTitle = song.name, songArtist = song.artist)
                                }

                                // 页面切换时重置下拉偏移量，防止卡在中间
                                LaunchedEffect(pagerState.settledPage) {
                                    isOnPlayerPage = pagerState.settledPage == 1
                                    if (pagerState.settledPage != 1 && offsetY.value > 0f) {
                                        offsetY.snapTo(0f)
                                    }
                                }

                                PlayerMobileLayout(
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    song = song,
                                syncedLyrics = syncedLyrics,
                                lyricsInfo = lyricsInfo,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = currentPosition,
                                duration = duration,
                                isFavorite = isFavorite,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                bitrate = bitrate,
                                sampleRate = sampleRate,
                                bitDepth = bitDepth,
                                channels = channels,
                                codecName = codecName,
                                isDownloaded = isDownloaded,
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
                                onDislikeClick = onDislikeClick,
                                pagerState = pagerState,
                                showTranslation = showTranslation,
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerWideLayout(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    song: Song,
    syncedLyrics: com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics?,
    lyricsInfo: cp.player.model.LyricsInfo?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    isFavorite: Boolean,
    shuffleMode: Boolean,
    repeatMode: Int,
    bitrate: Int,
    sampleRate: Int,
    bitDepth: Int,
    channels: Int,
    codecName: String,
    isDownloaded: Boolean,
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
                            model = song.albumArtUrl.resized(800),
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
                        Text(currentPosition.formatAsTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(duration.formatAsTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    sampleRate = sampleRate,
                                    bitrate = bitrate,
                                    bitDepth = bitDepth,
                                    channels = channels,
                                    codecName = codecName,
                                    lyricsInfo = lyricsInfo,
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
                syncedLyrics = syncedLyrics,
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
    syncedLyrics: com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics?,
    lyricsInfo: cp.player.model.LyricsInfo?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    isFavorite: Boolean,
    shuffleMode: Boolean,
    repeatMode: Int,
    bitrate: Int,
    sampleRate: Int,
    bitDepth: Int,
    channels: Int,
    codecName: String,
    isDownloaded: Boolean,
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
    onDislikeClick: () -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    showTranslation: Boolean = true,
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
    val context = LocalContext.current

    androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            // ==================== 歌词页（向右滑动进入）====================
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 歌词内容（标题栏已显示歌曲名和歌手）
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LyricContent(
                            syncedLyrics = syncedLyrics,
                            currentPosition = currentPosition,
                            showTranslation = showTranslation,
                            contentPadding = PaddingValues(vertical = 60.dp, horizontal = 8.dp)
                        )
                    }

                    // 底部操作栏
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
                            IconButton(onClick = onRepeatClick) {
                                val icon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                }
                                Icon(icon, contentDescription = "Mode", modifier = Modifier.size(24.dp),
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ==================== 播放器页（默认页）====================
            1 -> {
                var showExpandedCover by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 封面区域
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!showExpandedCover) {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth(0.95f)
                                    .clickable { showExpandedCover = true },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shadowElevation = 16.dp
                            ) {
                                if (song.albumArtUrl != null) {
                                    AsyncImage(
                                        model = song.albumArtUrl.resized(800),
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
                            // 扩展封面：封面作为大背景，底部渐隐，下方一行歌词
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth(0.95f)
                                    .clickable { showExpandedCover = false },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shadowElevation = 16.dp
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (song.albumArtUrl != null) {
                                        AsyncImage(
                                            model = song.albumArtUrl.resized(800),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(128.dp)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                    )
                                    val currentLineText = remember(currentPosition, syncedLyrics) {
                                        syncedLyrics.getCurrentLineText(currentPosition)
                                    }
                                    Text(
                                        text = currentLineText ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 歌曲信息行
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
                    }

                    // 进度条
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { onSeek((it * duration).toLong()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                currentPosition.formatAsTime(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (sampleRate > 0 || bitrate > 0) {
                                val isDsd = sampleRate >= 2822400
                                val qualityText = if (isDsd) {
                                    "DSD ${sampleRate / 1000}"
                                } else if (sampleRate > 0) {
                                    "${sampleRate / 1000}kHz"
                                } else {
                                    "${bitrate / 1000}kbps"
                                }
                                Text(
                                    text = qualityText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                duration.formatAsTime(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // 播放控制
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

                    // 底部操作栏
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
                            IconButton(onClick = onRepeatClick) {
                                val icon = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                }
                                Icon(icon, contentDescription = "Mode", modifier = Modifier.size(24.dp),
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        sampleRate = sampleRate,
                                        bitrate = bitrate,
                                        lyricsInfo = lyricsInfo,
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
            }

            // ==================== 评论页 ====================
            2 -> {
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
                    onLoadMore = { onLoadMoreComments() },
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
}

