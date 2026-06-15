package cp.player.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
    onSetLyricsExpanded: (Boolean) -> Unit,
    useSideNav: Boolean,
    hasBottomBar: Boolean,
    bottomBarOffsetHeightPx: MutableState<Float>,
    sharedTransitionScope: SharedTransitionScope
) {
    AnimatedContent(
        targetState = isPlayerExpanded,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        modifier = Modifier.align(if (useSideNav) Alignment.BottomCenter else Alignment.BottomEnd),
        label = "PlayerTransition"
    ) { expanded ->
        if (expanded) {
            Box(modifier = Modifier.fillMaxSize()) {
                val s = playbackViewModel.currentSong
                val completedSongs by downloadViewModel.completedSongs.collectAsState()
                val isFav = s?.let { userViewModel.favoriteSongs.contains(it.id) } ?: false
                
                PlayerScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    song = s,
                    lyrics = playbackViewModel.currentLyrics,
                    isPlaying = playbackViewModel.isPlaying,
                    currentPosition = playbackViewModel.currentPosition,
                    duration = playbackViewModel.duration,
                    onPlayPause = { playbackViewModel.togglePlayPause() },
                    onSkipNext = { playbackViewModel.skipNext() },
                    onSkipPrevious = { playbackViewModel.skipPrevious() },
                    onSeek = { playbackViewModel.seekTo(it) },
                    onRepeatClick = { playbackViewModel.toggleRepeatMode() },
                    onShuffleClick = { playbackViewModel.toggleShuffleMode() },
                    repeatMode = playbackViewModel.repeatMode,
                    shuffleMode = playbackViewModel.shuffleMode,
                    isFavorite = isFav,
                    onLikeClick = { s?.let { userViewModel.toggleLike(it.id, !isFav) } },
                    onArtistClick = { id ->
                        onSetPlayerExpanded(false)
                        userViewModel.fetchOtherUserProfile(id.toLong())
                        navController.navigate("user/$id")
                    },
                    onDownloadClick = { s?.let { downloadViewModel.downloadSong(it) } },
                    isDownloaded = s?.let { completedSongs.contains(it.id) } ?: false,
                    isBuffering = playbackViewModel.isBuffering,
                    hotComments = socialViewModel.hotComments,
                    newestComments = socialViewModel.newestComments,
                    commentTotal = socialViewModel.commentTotal,
                    isCommentsLoading = socialViewModel.isLoading,
                    hasMoreComments = socialViewModel.hasMoreComments,
                    commentSortType = socialViewModel.commentSortType,
                    onLoadMoreComments = {
                        s?.let {
                            socialViewModel.fetchComments(
                                it.id, "music", socialViewModel.commentSortType, socialViewModel.currentCommentPage + 1
                            )
                        }
                    },
                    onLikeComment = { c ->
                        s?.let { socialViewModel.toggleCommentLike(it.id, c.id, "music", !c.liked) }
                    },
                    onPostComment = { t ->
                        s?.let { socialViewModel.postComment(it.id, "music", t) }
                    },
                    onCommentClick = { s?.let { socialViewModel.fetchComments(it.id) } },
                    onCommentSortChange = { sort ->
                        s?.let { socialViewModel.fetchComments(it.id, "music", sort, 1) }
                    },
                    onAvatarClick = { u ->
                        onSetPlayerExpanded(false)
                        userViewModel.fetchOtherUserProfile(u)
                        navController.navigate("user/$u")
                    },
                    onDislikeClick = { s?.let { userViewModel.dislikeSong(it.id); playbackViewModel.skipNext() } },
                    sleepTimerRemaining = playbackViewModel.sleepTimerRemaining,
                    onSetSleepTimer = { playbackViewModel.startSleepTimer(it) },
                    onLyricClick = {
                        s?.let {
                            playbackViewModel.fetchLyrics(it.id)
                            onSetLyricsExpanded(true)
                        }
                    },
                    allPlaylists = userViewModel.userPlaylists,
                    onAddToPlaylist = { id, pid ->
                        userViewModel.addSongsToPlaylist(pid, listOf(id), null)
                    },
                    queue = playbackViewModel.currentQueue,
                    onMoveQueueItem = { f, t -> playbackViewModel.moveQueueItem(f, t) },
                    onRemoveQueueItem = { i -> playbackViewModel.removeQueueItem(i) },
                    onClearQueue = { playbackViewModel.clearQueue() },
                    qualityWifi = settingsViewModel.qualityWifi,
                    qualityCellular = settingsViewModel.qualityCellular,
                    sampleRate = playbackViewModel.currentSampleRate,
                    bitrate = playbackViewModel.currentBitrate,
                    onViewFloorClick = { c ->
                        s?.let { socialViewModel.fetchFloorComments(it.id, c.id) }
                        socialViewModel.activeParentComment = c
                    },
                    floorComments = socialViewModel.floorComments,
                    floorCommentTotal = socialViewModel.floorCommentTotal,
                    floorHasMore = socialViewModel.floorHasMore,
                    onLoadMoreFloor = { c ->
                        s?.let { socialViewModel.fetchFloorComments(it.id, c.id, time = socialViewModel.floorCursor) }
                    },
                    activeParentComment = socialViewModel.activeParentComment,
                    onDismissFloor = { socialViewModel.activeParentComment = null },
                    useCoverColor = settingsViewModel.themeMode == 1 && settingsViewModel.followCoverPlayer,
                    useFluidBackground = settingsViewModel.useFluidBackground,
                    useWavyProgress = settingsViewModel.useWavyProgress,
                    coverColor = playbackViewModel.extractedColor,
                    onBackPressed = { onSetPlayerExpanded(false) }
                )
            }
        } else {
            val playerBottomPadding = if (useSideNav) 16.dp else if (hasBottomBar) 80.dp else 0.dp
            Box(
                modifier = Modifier
                    .padding(
                        bottom = playerBottomPadding,
                        end = if (useSideNav) 0.dp else 16.dp
                    )
                    .offset {
                        if (useSideNav || !hasBottomBar) IntOffset.Zero else IntOffset(0, -bottomBarOffsetHeightPx.value.toInt())
                    }
            ) {
                BottomPlaybackBar(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    song = playbackViewModel.currentSong,
                    isPlaying = playbackViewModel.isPlaying,
                    isBuffering = playbackViewModel.isBuffering,
                    onPlayPause = { playbackViewModel.togglePlayPause() },
                    onSkipNext = { playbackViewModel.skipNext() },
                    onSkipPrevious = { playbackViewModel.skipPrevious() },
                    onClick = { onSetPlayerExpanded(true) },
                    useCoverColor = settingsViewModel.themeMode == 1 && settingsViewModel.followCoverMini,
                    coverColor = playbackViewModel.extractedColor,
                    modifier = if (useSideNav) Modifier.widthIn(max = 500.dp) else Modifier
                )
            }
        }
    }
}
