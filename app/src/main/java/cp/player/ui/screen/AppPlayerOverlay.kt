package cp.player.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cp.player.model.PlayerCallbacks
import cp.player.model.PlayerUiState
import cp.player.ui.component.BottomPlaybackBar
import cp.player.viewmodel.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BoxScope.AppPlayerOverlay(
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    socialViewModel: SocialViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavController,
    isPlayerExpanded: Boolean,
    onSetPlayerExpanded: (Boolean) -> Unit,
    useSideNav: Boolean,
    hasBottomBar: Boolean,
    bottomBarOffsetHeightPx: MutableState<Float>,
    sharedTransitionScope: SharedTransitionScope
) {
    val s = playbackViewModel.currentSong
    val completedSongs by downloadViewModel.completedSongs.collectAsState()
    val isFav = s?.let { userViewModel.favoriteSongs.contains(it.id) } ?: false

    // 直接从 LyricsManager 读取歌词状态，避免 ViewModel 中间层的竞态问题
    val lyricsState by cp.player.lyrics.LyricsManager.state.collectAsState()
    val currentSyncedLyrics = (lyricsState as? cp.player.lyrics.LyricsState.Success)?.syncedLyrics
    val currentLyricsInfo = (lyricsState as? cp.player.lyrics.LyricsState.Success)?.lyricsInfo
    // 构造聚合的 UI 状态
    val uiState = PlayerUiState(
        song = s,
        isPlaying = playbackViewModel.isPlaying,
        isBuffering = playbackViewModel.isBuffering,
        currentPosition = playbackViewModel.currentPosition,
        duration = playbackViewModel.duration,
        repeatMode = playbackViewModel.repeatMode,
        shuffleMode = playbackViewModel.shuffleMode,
        sampleRate = playbackViewModel.currentSampleRate,
        bitrate = playbackViewModel.currentBitrate,
        bitDepth = playbackViewModel.currentBitDepth,
        channels = playbackViewModel.currentChannels,
        codecName = playbackViewModel.currentCodecName,
        isFavorite = isFav,
        isDownloaded = s?.let { completedSongs.contains(it.id) } ?: false,
        syncedLyrics = currentSyncedLyrics,
        lyricsInfo = currentLyricsInfo,
        queue = playbackViewModel.currentQueue,
        useCoverColor = settingsViewModel.themeMode == 1 && settingsViewModel.followCoverPlayer,
        useFluidBackground = settingsViewModel.useFluidBackground,
        coverColor = playbackViewModel.extractedColor,
        pureBlack = settingsViewModel.pureBlackMode,
        sleepTimerRemaining = playbackViewModel.sleepTimerRemaining,
        hotComments = socialViewModel.hotComments,
        newestComments = socialViewModel.newestComments,
        commentTotal = socialViewModel.commentTotal,
        isCommentsLoading = socialViewModel.isCommentsLoading,
        hasMoreComments = socialViewModel.hasMoreComments,
        commentSortType = socialViewModel.commentSortType,
        floorComments = socialViewModel.floorComments,
        floorCommentTotal = socialViewModel.floorCommentTotal,
        floorHasMore = socialViewModel.floorHasMore,
        activeParentComment = socialViewModel.activeParentComment,
        allPlaylists = userViewModel.userPlaylists,
        similarSongs = playbackViewModel.similarSongs,
        isSimilarSongsLoading = playbackViewModel.isSimilarSongsLoading
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    // 构造聚合的回调集合
    val callbacks = remember(playbackViewModel, userViewModel, socialViewModel, downloadViewModel, navController) {
        PlayerCallbacks(
            onPlayPause = { playbackViewModel.togglePlayPause() },
            onSkipNext = { playbackViewModel.skipNext() },
            onSkipPrevious = { playbackViewModel.skipPrevious() },
            onSeek = { playbackViewModel.seekTo(it) },
            onRepeatClick = { playbackViewModel.toggleRepeatMode() },
            onShuffleClick = { playbackViewModel.toggleShuffleMode() },
            onLikeClick = {
                playbackViewModel.currentSong?.let { song ->
                    val fav = userViewModel.favoriteSongs.contains(song.id)
                    userViewModel.toggleLike(song.id, !fav)
                }
            },
            onArtistClick = { id ->
                onSetPlayerExpanded(false)
                userViewModel.fetchOtherUserProfile(id.toLong())
                navController.navigate("user/$id")
            },
            onDownloadClick = { playbackViewModel.currentSong?.let { downloadViewModel.downloadSong(it) } },
            onCommentClick = { playbackViewModel.currentSong?.let { socialViewModel.fetchComments(it.id) } },
            onLyricClick = { /* 歌词已集成到播放器 HorizontalPager 中，自动加载 */ },
            onDislikeClick = { playbackViewModel.currentSong?.let { userViewModel.dislikeSong(it.id); playbackViewModel.skipNext() } },
            onAddToPlaylist = { id, pid -> userViewModel.addSongsToPlaylist(pid, listOf(id), null) },
            onPlayAtQueueIndex = { i -> playbackViewModel.playAt(i) },
            onMoveQueueItem = { f, t -> playbackViewModel.moveQueueItem(f, t) },
            onRemoveQueueItem = { i -> playbackViewModel.removeQueueItem(i) },
            onClearQueue = { playbackViewModel.clearQueue() },
            onLoadMoreComments = {
                playbackViewModel.currentSong?.let {
                    socialViewModel.fetchComments(
                        it.id, "music", socialViewModel.commentSortType, socialViewModel.currentCommentPage + 1
                    )
                }
            },
            onLikeComment = { c ->
                playbackViewModel.currentSong?.let { socialViewModel.toggleCommentLike(it.id, c.id, "music", !c.liked) }
            },
            onPostComment = { t ->
                playbackViewModel.currentSong?.let { socialViewModel.postComment(it.id, "music", t) }
            },
            onAvatarClick = { u ->
                onSetPlayerExpanded(false)
                userViewModel.fetchOtherUserProfile(u)
                navController.navigate("user/$u")
            },
            onCommentSortChange = { sort ->
                playbackViewModel.currentSong?.let { socialViewModel.fetchComments(it.id, "music", sort, 1) }
            },
            onViewFloorClick = { c ->
                playbackViewModel.currentSong?.let { socialViewModel.fetchFloorComments(it.id, c.id) }
                socialViewModel.activeParentComment = c
            },
            onLoadMoreFloor = { c ->
                playbackViewModel.currentSong?.let { socialViewModel.fetchFloorComments(it.id, c.id, time = socialViewModel.floorCursor) }
            },
            onDismissFloor = { socialViewModel.activeParentComment = null },
            onSetSleepTimer = { playbackViewModel.startSleepTimer(it) },
            onBackPressed = { onSetPlayerExpanded(false) },
            onFetchSimilarSongs = { playbackViewModel.fetchSimilarSongs() },
            onPlaySimilarSong = { song -> playbackViewModel.playSong(song, playbackViewModel.similarSongs) }
        )
    }

    // 驱动全屏展开/收起的连续进度值，用于 scrim 和下沉效果
    val expandProgress by animateFloatAsState(
        targetValue = if (isPlayerExpanded) 1f else 0f,
        animationSpec = tween(400, easing = EaseOutCubic),
        label = "expandProgress"
    )

    // 背景变暗 scrim — 位于 AnimatedContent 之下，覆盖主内容
    if (expandProgress > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = expandProgress * 0.3f))
        )
    }

    AnimatedContent(
        targetState = isPlayerExpanded,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        modifier = Modifier
            .align(if (useSideNav) Alignment.BottomCenter else Alignment.BottomEnd)
            .graphicsLayer {
                // 微量下沉：展开时内容略微缩小并下移
                val scale = 1f - expandProgress * 0.03f
                scaleX = scale
                scaleY = scale
                translationY = expandProgress * 8f
            },
        label = "PlayerTransition"
    ) { expanded ->
        if (expanded) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerScreen(
                    uiState = uiState,
                    callbacks = callbacks,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }
        } else {
            // 动态计算 mini player 底部间距：底栏可见时 80dp + 小白条高度，隐藏时 16dp
            val density = LocalDensity.current
            val gestureInsetDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
            val navBarVisibleHeight = 80.dp + gestureInsetDp
            val navBarHiddenHeight = 16.dp
            val animatedBottomPadding by animateDpAsState(
                targetValue = if (!hasBottomBar || useSideNav) 16.dp
                else {
                    // 根据底栏偏移量插值：offset=0 → 80dp, offset=maxOffset → 16dp
                    val navBarHeightPx = with(density) { navBarVisibleHeight.toPx() }
                    val progress = if (navBarHeightPx > 0) {
                        (-bottomBarOffsetHeightPx.value / navBarHeightPx).coerceIn(0f, 1f)
                    } else 0f
                    navBarVisibleHeight * (1f - progress) + navBarHiddenHeight * progress
                },
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                label = "miniPlayerBottomPadding"
            )
            Box(
                modifier = Modifier
                    .padding(bottom = animatedBottomPadding)
            ) {
                val progress = if (uiState.duration > 0) uiState.currentPosition.toFloat() / uiState.duration.toFloat() else 0f
                BottomPlaybackBar(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    song = uiState.song,
                    isPlaying = uiState.isPlaying,
                    isBuffering = uiState.isBuffering,
                    progress = progress,
                    useWavyProgress = settingsViewModel.wavyProgress,
                    onPlayPause = callbacks.onPlayPause,
                    onSkipNext = callbacks.onSkipNext,
                    onSkipPrevious = callbacks.onSkipPrevious,
                    onClick = { onSetPlayerExpanded(true) },
                    useCoverColor = settingsViewModel.themeMode == 1 && settingsViewModel.followCoverMini,
                    coverColor = uiState.coverColor,
                    modifier = if (useSideNav) Modifier.widthIn(max = 500.dp) else Modifier
                )
            }
        }
    }
}
