package cp.player.ui.screen

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cp.player.R
import coil3.compose.AsyncImage
import cp.player.util.FormatUtils
import cp.player.util.ImageUtils
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.ui.component.SongItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import cp.player.ui.component.AppScaffold

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    hasMoreSongs: Boolean = false,
    isFetchingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
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
    onFetchSourcePlaylistSongs: (suspend (Long) -> List<Song>)? = null,
    playbackQueue: List<Song> = emptyList(),
    completedSongs: Set<String> = emptySet(),
    currentSongId: String? = null,
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
    var showImportSourcePicker by remember { mutableStateOf(false) }
    var showImportSourceOptions by remember { mutableStateOf(false) }
    var showMultiSelectAddToPlaylist by remember { mutableStateOf(false) }
    var sourcePlaylistForImport by remember { mutableStateOf<Playlist?>(null) }
    var showQueueSongSelection by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var pendingDownloadSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // 排序类型: "default"(默认顺序), "name"(按名称), "artist"(按艺术家)
    var currentSortType by remember { mutableStateOf("default") }

    // 根据排序类型对歌曲进行排序
    val sortedSongs = remember(songs, currentSortType) {
        when (currentSortType) {
            "name" -> songs.sortedBy { it.name.lowercase() }
            "artist" -> songs.sortedBy { it.artist.lowercase() }
            else -> songs  // default: 保持原序
        }
    }

    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
    val durationStr = FormatUtils.formatTime(totalDurationMs)
    val favoriteSongsSet = remember(favoriteSongs) { favoriteSongs.toSet() }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSongs = emptySet()
    }

    if (isSelectionMode) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AppScaffold(
            title = stringResource(R.string.selected_count, selectedSongs.size),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIcon = {
                IconButton(onClick = {
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
            actions = {
                IconButton(onClick = {
                    val selectedList = sortedSongs.filter { selectedSongs.contains(it.id) }
                    onBatchDownload(selectedList)
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download_action))
                }
                IconButton(onClick = {
                    val selectedList = sortedSongs.filter { selectedSongs.contains(it.id) }
                    onQueueAllClick(selectedList)
                    android.widget.Toast.makeText(context, context.getString(R.string.added_to_queue, selectedList.size), android.widget.Toast.LENGTH_SHORT).show()
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.add_to_queue))
                }
                IconButton(onClick = { showMultiSelectAddToPlaylist = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_playlist))
                }
                IconButton(onClick = {
                    onRemoveFromPlaylist(selectedSongs.toList())
                    isSelectionMode = false
                    selectedSongs = emptySet()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_from_playlist))
                }
            }
        ) { innerPadding ->
            PlaylistDetailContent(
                innerPadding = innerPadding,
                bottomContentPadding = bottomContentPadding,
                isLoading = isLoading,
                songs = sortedSongs,
                hasMoreSongs = hasMoreSongs,
                isFetchingMore = isFetchingMore,
                onLoadMore = onLoadMore,
                playlist = playlist,
                isSelectionMode = isSelectionMode,
                selectedSongs = selectedSongs,
                favoriteSongs = favoriteSongsSet,
                completedSongs = completedSongs,
                currentSongId = currentSongId,
                durationStr = durationStr,
                onPlayAllClick = onPlayAllClick,
                onLikeClick = onLikeClick,
                onSongClick = onSongClick,
                onSelectionChange = { id, selected ->
                    selectedSongs = if (selected) selectedSongs + id else selectedSongs - id
                },
                onToggleSelectionMode = {
                    isSelectionMode = it
                    if (!it) selectedSongs = emptySet()
                },
                onAddToPlaylistClick = { showImportSourceOptions = true },
                onBatchDownload = onBatchDownload
            )
        }
    } else {
        AppScaffold(
            title = {
                val collapsedFraction = scrollBehavior.state.collapsedFraction
                val isCollapsed = collapsedFraction > 0.5f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isCollapsed) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp
                        ) {
                            if (playlist.coverImgUrl != null) {
                                AsyncImage(
                                    model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl, 200),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column {
                        Text(
                            text = playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val displayCount = if (playlist.trackCount > 0) playlist.trackCount else songs.size
                        Text(
                            text = "${stringResource(R.string.song_count_simple, displayCount)} • $durationStr",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            onBackPressed = onBackPressed,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            actions = {
                FilledIconButton(
                    onClick = { showSortMenu = true },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                if (showSortMenu) {
                    cp.player.ui.component.PlaylistOptionsBottomSheet(
                        playlist = playlist,
                        onDismissRequest = { showSortMenu = false },
                        onPlayClick = { onPlayAllClick(sortedSongs) },
                        onAddToQueueClick = { onQueueAllClick(sortedSongs) },
                        onShareClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.share_playlist_text, playlist.name, "https://music.163.com/playlist?id=${playlist.id}"))
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_playlist)))
                        },
                        currentSortType = currentSortType,
                        onSortDefaultClick = {
                            currentSortType = "default"
                            onSortChange("default")
                        },
                        onSortByNameClick = {
                            currentSortType = "name"
                            onSortChange("name")
                        },
                        onSortByArtistClick = {
                            currentSortType = "artist"
                            onSortChange("artist")
                        }
                    )
                }
            },
            scrollBehavior = scrollBehavior
        ) { innerPadding ->
            PlaylistDetailContent(
                innerPadding = innerPadding,
                bottomContentPadding = bottomContentPadding,
                isLoading = isLoading,
                songs = sortedSongs,
                hasMoreSongs = hasMoreSongs,
                isFetchingMore = isFetchingMore,
                onLoadMore = onLoadMore,
                playlist = playlist,
                isSelectionMode = isSelectionMode,
                selectedSongs = selectedSongs,
                favoriteSongs = favoriteSongsSet,
                completedSongs = completedSongs,
                currentSongId = currentSongId,
                durationStr = durationStr,
                onPlayAllClick = onPlayAllClick,
                onLikeClick = onLikeClick,
                onSongClick = onSongClick,
                onSelectionChange = { id, selected ->
                    selectedSongs = if (selected) selectedSongs + id else selectedSongs - id
                },
                onToggleSelectionMode = {
                    isSelectionMode = it
                    if (!it) selectedSongs = emptySet()
                },
                onAddToPlaylistClick = { showImportSourceOptions = true },
                onBatchDownload = onBatchDownload
            )
        }
    }

    // Header "Add" - Import source options
    if (showImportSourceOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImportSourceOptions = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_songs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // From other playlists
                Surface(
                    onClick = {
                        showImportSourceOptions = false
                        showImportSourcePicker = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_from_playlist), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                // From current queue
                Surface(
                    onClick = {
                        showImportSourceOptions = false
                        showQueueSongSelection = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_from_queue), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Multi-select: Add to other playlist
    if (showMultiSelectAddToPlaylist) {
        cp.player.ui.component.AddToPlaylistBottomSheet(
            playlists = allPlaylists,
            onDismissRequest = { showMultiSelectAddToPlaylist = false },
            onPlaylistSelected = { p ->
                onAddToPlaylist(selectedSongs.toList(), p.id)
                showMultiSelectAddToPlaylist = false
                isSelectionMode = false
                selectedSongs = emptySet()
            }
        )
    }

    // Import from queue: song selection
    if (showQueueSongSelection) {
        cp.player.ui.component.SourceSongsSelectionBottomSheet(
            sourceName = stringResource(R.string.now_playing),
            initialSongs = playbackQueue,
            onDismissRequest = { showQueueSongSelection = false },
            onAddSelected = { selectedIds ->
                onAddToPlaylist(selectedIds, playlist.id)
                showQueueSongSelection = false
            }
        )
    }

    // Import from playlist: source picker
    if (showImportSourcePicker) {
        cp.player.ui.component.AddToPlaylistBottomSheet(
            playlists = allPlaylists,
            onDismissRequest = { showImportSourcePicker = false },
            excludePlaylistId = playlist.id,
            title = stringResource(R.string.import_from_playlist),
            onPlaylistSelected = { p ->
                showImportSourcePicker = false
                sourcePlaylistForImport = p
            }
        )
    }

    // Import from playlist: song selection
    val currentSourcePlaylist = sourcePlaylistForImport
    if (currentSourcePlaylist != null && onFetchSourcePlaylistSongs != null) {
        cp.player.ui.component.SourceSongsSelectionBottomSheet(
            sourceName = currentSourcePlaylist.name,
            fetchSongs = { onFetchSourcePlaylistSongs(currentSourcePlaylist.id) },
            onDismissRequest = { sourcePlaylistForImport = null },
            onAddSelected = { selectedIds ->
                onAddToPlaylist(selectedIds, playlist.id)
                sourcePlaylistForImport = null
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailContent(
    innerPadding: PaddingValues,
    bottomContentPadding: PaddingValues,
    isLoading: Boolean,
    songs: List<Song>,
    hasMoreSongs: Boolean = false,
    isFetchingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    playlist: Playlist,
    isSelectionMode: Boolean,
    selectedSongs: Set<String>,
    favoriteSongs: Set<String>,
    completedSongs: Set<String>,
    currentSongId: String? = null,
    durationStr: String,
    onPlayAllClick: (List<Song>) -> Unit,
    onLikeClick: (Song) -> Unit,
    onSongClick: (Song) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onAddToPlaylistClick: () -> Unit = {},
    onBatchDownload: (List<Song>) -> Unit = {}
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
                item { 
                    PlaylistHeader(
                        playlist = playlist,
                        songs = songs,
                        durationStr = durationStr,
                        onPlayAllClick = { onPlayAllClick(songs) },
                        onShuffleClick = { onPlayAllClick(songs.shuffled()) },
                        onAddClick = onAddToPlaylistClick,
                        onReorderClick = { onToggleSelectionMode(true) } // For now, just selection mode for reorder too
                    ) 
                }
            }

            itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                if (index >= songs.size - 5 && hasMoreSongs && !isFetchingMore) {
                    LaunchedEffect(index) {
                        onLoadMore()
                    }
                }
                
                val isSelected = selectedSongs.contains(song.id)
                SongItem(
                    song = song,
                    index = index,
                    total = songs.size,
                    isFavorite = favoriteSongs.contains(song.id),
                    isDownloaded = completedSongs.contains(song.id),
                    isCurrentlyPlaying = song.id == currentSongId,
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
            
            if (isFetchingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }

    selectedSongForOptions?.let { song ->
        cp.player.ui.component.SongOptionsBottomSheet(
            song = song,
            isFavorite = favoriteSongs.contains(song.id),
            isDownloaded = completedSongs.contains(song.id),
            onDismissRequest = { selectedSongForOptions = null },
            onPlayClick = {
                onSongClick(song)
                selectedSongForOptions = null
            },
            onFavoriteClick = {
                onLikeClick(song)
                selectedSongForOptions = null
            },
            onDownloadClick = {
                onBatchDownload(listOf(song))
                selectedSongForOptions = null
            }
        )
    }
}

@Composable
fun PlaylistHeader(
    playlist: Playlist,
    songs: List<Song>,
    durationStr: String,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onReorderClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Buttons Row 1: Play and Shuffle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Play Button
            Surface(
                onClick = onPlayAllClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.play_it),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Shuffle Button
            Surface(
                onClick = onShuffleClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.shuffle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Buttons Row 2: Add, Reorder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1.2f).height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
            }
            
            Surface(
                onClick = onReorderClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1.2f).height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.reorder), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.reorder), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}