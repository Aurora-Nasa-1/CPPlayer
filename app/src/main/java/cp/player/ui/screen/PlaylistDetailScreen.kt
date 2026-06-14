package cp.player.ui.screen

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import coil3.compose.AsyncImage
import cp.player.util.ImageUtils
import cp.player.model.Playlist
import cp.player.model.Song
import androidx.compose.foundation.shape.CircleShape
import cp.player.ui.component.SongItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import cp.player.ui.component.AppScaffold

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    favoriteSongs: List<String>,
    allPlaylists: List<Playlist>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: (List<Song>) -> Unit,
    onQueueAllClick: (List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onAddToPlaylist: (List<String>, Long) -> Unit,
    onRemoveFromPlaylist: (List<String>) -> Unit,
    onBatchDownload: (List<Song>) -> Unit,
    completedSongs: Set<String> = emptySet(),
    isFirstDownload: Boolean = false,
    onDownloadQualityChange: (String) -> Unit = {},
    onSortChange: (String) -> Unit = {},
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var pendingDownloadSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongs = emptySet()
    }

    if (isSelectionMode) {
        AppScaffold(
            title = stringResource(R.string.selected_count, selectedSongs.size),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIcon = {
                IconButton(onClick = {
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            },
            actions = {
                IconButton(onClick = {
                    val selectedList = songs.filter { selectedSongs.contains(it.id) }
                    onQueueAllClick(selectedList)
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Queue")
                }
                IconButton(onClick = { showAddToPlaylistDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add to Playlist")
                }
                IconButton(onClick = {
                    onRemoveFromPlaylist(selectedSongs.toList())
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove from Playlist")
                }
            }
        ) { innerPadding ->
            PlaylistDetailContent(
                innerPadding = innerPadding,
                bottomContentPadding = bottomContentPadding,
                isLoading = isLoading,
                songs = songs,
                playlist = playlist,
                isSelectionMode = isSelectionMode,
                selectedSongs = selectedSongs,
                favoriteSongs = favoriteSongs,
                completedSongs = completedSongs,
                onPlayAllClick = onPlayAllClick,
                onLikeClick = onLikeClick,
                onSongClick = onSongClick,
                onSelectionChange = { id, selected ->
                    selectedSongs = if (selected) selectedSongs + id else selectedSongs - id
                },
                onToggleSelectionMode = {
                    isSelectionMode = it
                    if (!it) selectedSongs = emptySet()
                }
            )
        }
    } else {
        AppScaffold(
            title = stringResource(R.string.playlist),
            onBackPressed = onBackPressed,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            actions = {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                if (showSortMenu) {
                    cp.player.ui.component.PlaylistOptionsBottomSheet(
                        playlist = playlist,
                        onDismissRequest = { showSortMenu = false },
                        onPlayClick = { onPlayAllClick(songs) },
                        onAddToQueueClick = { onQueueAllClick(songs) },
                        onShareClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out this playlist: ${playlist.name}\nhttps://music.163.com/playlist?id=${playlist.id}")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Playlist"))
                        },
                        onSortByNameClick = { onSortChange("name") },
                        onSortByArtistClick = { onSortChange("artist") }
                    )
                }
            },
            scrollBehavior = scrollBehavior
        ) { innerPadding ->
            PlaylistDetailContent(
                innerPadding = innerPadding,
                bottomContentPadding = bottomContentPadding,
                isLoading = isLoading,
                songs = songs,
                playlist = playlist,
                isSelectionMode = isSelectionMode,
                selectedSongs = selectedSongs,
                favoriteSongs = favoriteSongs,
                completedSongs = completedSongs,
                onPlayAllClick = onPlayAllClick,
                onLikeClick = onLikeClick,
                onSongClick = onSongClick,
                onSelectionChange = { id, selected ->
                    selectedSongs = if (selected) selectedSongs + id else selectedSongs - id
                },
                onToggleSelectionMode = {
                    isSelectionMode = it
                    if (!it) selectedSongs = emptySet()
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailContent(
    innerPadding: PaddingValues,
    bottomContentPadding: PaddingValues,
    isLoading: Boolean,
    songs: List<Song>,
    playlist: Playlist,
    isSelectionMode: Boolean,
    selectedSongs: Set<String>,
    favoriteSongs: List<String>,
    completedSongs: Set<String>,
    onPlayAllClick: (List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit
) {
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }

    if (isLoading && songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             ContainedLoadingIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!isSelectionMode) {
                item { PlaylistHeader(playlist, onPlayAllClick = { onPlayAllClick(songs) }) }
            }

            itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                val isSelected = selectedSongs.contains(song.id)
                                SongItem(
                    song = song,
                    isFavorite = favoriteSongs.contains(song.id),
                    isDownloaded = completedSongs.contains(song.id),
                    onOptionsClick = if (!isSelectionMode) { { selectedSongForOptions = song } } else null,
                    onClick = {
                        if (isSelectionMode) {
                            onSelectionChange(song.id, !isSelected)
                        } else {
                            onSongClick(song)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            onToggleSelectionMode(true)
                            onSelectionChange(song.id, true)
                        }
                    },
                    leadingContent = if (isSelectionMode) {
                        { Checkbox(checked = isSelected, onCheckedChange = {
                            onSelectionChange(song.id, it)
                        }) }
                    } else null,
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
                )
            }
        }
    }

    selectedSongForOptions?.let { song ->
        cp.player.ui.component.SongOptionsBottomSheet(
            song = song,
            isFavorite = favoriteSongs.contains(song.id),
            onDismissRequest = { selectedSongForOptions = null },
            onPlayClick = {
                onSongClick(song)
                selectedSongForOptions = null
            },
            onFavoriteClick = {
                onLikeClick(song)
                selectedSongForOptions = null
            }
        )
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, onPlayAllClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.size(200.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 8.dp) {
            if (playlist.coverImgUrl != null) {
                AsyncImage(model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl, 400), contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
        Spacer(modifier = Modifier.height(16.dp))

        // MD3E Containment for main actions
        Surface(shape = MaterialTheme.shapes.extraLarge, // Rounded Container
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.songs_count, playlist.trackCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                    }
                    Button(
                        onClick = onPlayAllClick, shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play All") // Sentence case
                    }
                }
            }
        }
    }
}