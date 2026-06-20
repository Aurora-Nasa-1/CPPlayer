package cp.player.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.model.DownloadTask
import cp.player.manager.DownloadedSongMetadata
import cp.player.model.LocalSongMetadata

import cp.player.viewmodel.LiveSortViewModel
import cp.player.viewmodel.PlaybackViewModel

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import cp.player.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onNavigateToSettings: () -> Unit,
    // Downloads Data
    onPlayLocalSong: (Song, android.net.Uri) -> Unit,
    downloadedSongs: List<DownloadedSongMetadata>,
    localSongs: List<LocalSongMetadata> = emptyList(),
    downloadTasks: Map<String, DownloadTask> = emptyMap(),
    onCancelDownload: (String) -> Unit = {},
    onDeleteLocalSong: (android.net.Uri) -> Unit = {},
    onRefreshLocalMusic: () -> Unit = {},
    // Cloud Data
    cloudSongs: List<Song> = emptyList(),
    favoriteSongs: List<String> = emptyList(),
    isCloudLoading: Boolean = false,
    onCloudSongClick: (Song) -> Unit = {},
    onCloudLikeClick: (Song) -> Unit = {},
    onFetchCloudSongs: () -> Unit = {},
    // Live Sort Data
    liveSortViewModel: LiveSortViewModel,
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val filters = listOf(
        "Playlists" to Icons.Rounded.QueueMusic,
        "Downloads" to Icons.Rounded.DownloadDone,
        "Cloud" to Icons.Rounded.CloudQueue,
        "Live Sort" to Icons.Rounded.AutoGraph
    )
    val pagerState = rememberPagerState(pageCount = { filters.size })
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var showCreateDialog by remember { mutableStateOf(false) }

    // 当 Cloud 标签页被选中时，自动获取云盘歌曲
    LaunchedEffect(pagerState.currentPage) {
        if (filters[pagerState.currentPage].first == "Cloud") {
            onFetchCloudSongs()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = bottomContentPadding.calculateBottomPadding())
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Top Filters with Animated Indicator
                LibraryTopFilters(
                    filters = filters,
                    selectedIndex = pagerState.currentPage,
                    onFilterSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                index,
                                animationSpec = tween(durationMillis = 300)
                            )
                        }
                    },
                    onNavigateToSettings = onNavigateToSettings
                )

                // 2. Large Rounded Container for the list with Pager
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (filters[page].first) {
                            "Playlists" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
                                ) {
                                    item {
                                        PlaylistsControlBar(
                                            onCreatePlaylistClick = {
                                                showCreateDialog = true
                                            }
                                        )
                                    }

                                    items(userPlaylists.size) { index ->
                                        PlaylistItem(
                                            playlist = userPlaylists[index],
                                            isOwner = !userPlaylists[index].subscribed,
                                            onClick = { onPlaylistClick(userPlaylists[index]) },
                                            onDelete = { userViewModel.deletePlaylist(userPlaylists[index].id) },
                                            onUnsubscribe = if (userPlaylists[index].subscribed) {
                                                { userViewModel.unsubscribePlaylist(userPlaylists[index].id) }
                                            } else null,
                                            onAddToQueue = {
                                                coroutineScope.launch {
                                                    val songs = userViewModel.getPlaylistSongs(userPlaylists[index].id)
                                                    if (songs.isNotEmpty()) {
                                                        playbackViewModel.addSongsToQueue(songs)
                                                        android.widget.Toast.makeText(context, "Added ${songs.size} songs to queue", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onShare = {
                                                val shareIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out this playlist: ${userPlaylists[index].name}\nhttps://music.163.com/playlist?id=${userPlaylists[index].id}")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Playlist"))
                                            }
                                        )
                                    }
                                }
                            }
                            "Downloads" -> {
                                cp.player.ui.screen.DownloadsContent(
                                    onPlayLocalSong = onPlayLocalSong,
                                    downloadedSongs = downloadedSongs,
                                    localSongs = localSongs,
                                    tasks = downloadTasks,
                                    onCancelDownload = onCancelDownload,
                                    onDeleteLocalSong = onDeleteLocalSong,
                                    onRefreshLocalMusic = onRefreshLocalMusic,
                                    favoriteSongs = favoriteSongs,
                                    onLikeClick = { song ->
                                        userViewModel.toggleLike(song.id, !favoriteSongs.contains(song.id))
                                    },
                                    playbackViewModel = playbackViewModel,
                                    bottomContentPadding = PaddingValues(bottom = 100.dp)
                                )
                            }
                            "Cloud" -> {
                                cp.player.ui.screen.CloudMusicContent(
                                    songs = cloudSongs,
                                    favoriteSongs = favoriteSongs,
                                    isLoading = isCloudLoading,
                                    onSongClick = onCloudSongClick,
                                    onLikeClick = onCloudLikeClick,
                                    playbackViewModel = playbackViewModel,
                                    bottomContentPadding = PaddingValues(bottom = 100.dp)
                                )
                            }
                            "Live Sort" -> {
                                cp.player.ui.screen.LiveSortContent(
                                    liveSortViewModel = liveSortViewModel,
                                    playbackViewModel = playbackViewModel,
                                    bottomContentPadding = PaddingValues(bottom = 100.dp)
                                )
                            }
                        }
                    }
                }
            }
            // MiniPlayer is handled by AppPlayerOverlay in MainActivity
        }
    }
    
    if (showCreateDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            userViewModel.createPlaylist(newPlaylistName)
                        }
                        showCreateDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    isOwner: Boolean = true,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onUnsubscribe: (() -> Unit)? = null,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.coverImgUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val trackCountStr = if (playlist.trackCount > 0) "${playlist.trackCount} 首" else ""
                val ownerStr = if (isOwner) "创建的歌单" else "收藏 · ${playlist.creatorName ?: "未知"}"
                Text(
                    text = listOf(ownerStr, trackCountStr).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showMenu = true }) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showMenu) {
        cp.player.ui.component.PlaylistOptionsBottomSheet(
            playlist = playlist,
            onDismissRequest = { showMenu = false },
            isOwner = isOwner,
            onPlayClick = { onClick() },
            onAddToQueueClick = { onAddToQueue() },
            onShareClick = { onShare() },
            onDeleteClick = if (onDelete != null) { { showConfirmDialog = true } } else null,
            onUnsubscribeClick = if (onUnsubscribe != null) { { showConfirmDialog = true } } else null
        )
    }

    if (showConfirmDialog) {
        val actionText = if (isOwner) "Delete Playlist" else "Unsubscribe"
        val descText = if (isOwner) "Are you sure you want to delete '${playlist.name}'?"
                       else "Are you sure you want to unsubscribe from '${playlist.name}'?"
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(actionText) },
            text = { Text(descText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isOwner) onDelete?.invoke() else onUnsubscribe?.invoke()
                        showConfirmDialog = false
                    }
                ) {
                    Text(
                        if (isOwner) "Delete" else "Unsubscribe",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LibraryTopFilters(
    filters: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    selectedIndex: Int,
    onFilterSelected: (Int) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(filters.size) { index ->
                val filter = filters[index]
                val isSelected = selectedIndex == index

                Surface(
                    onClick = { onFilterSelected(index) },
                    shape = RoundedCornerShape(percent = 50),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    tonalElevation = if (isSelected) 0.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isSelected) 20.dp else 14.dp,
                            vertical = 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            filter.second,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut(
                                animationSpec = tween(durationMillis = 150)
                            ) + shrinkHorizontally(
                                animationSpec = tween(durationMillis = 150)
                            )
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = filter.first,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp),
            onClick = onNavigateToSettings
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlaylistsControlBar(
    onCreatePlaylistClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Extended FAB-like Create Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onCreatePlaylistClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Create Playlist",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Create Playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
