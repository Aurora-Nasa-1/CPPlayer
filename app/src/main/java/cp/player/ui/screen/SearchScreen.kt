package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.api.MusicApiMethod
import cp.player.model.Artist
import cp.player.model.Song
import cp.player.ui.component.SongItem
import cp.player.ui.component.PlaylistItem
import cp.player.ui.component.ArtistItem

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun SearchScreen(
    searchResults: List<Song>,
    searchPlaylists: List<cp.player.model.Playlist>,
    searchArtists: List<Artist> = emptyList(),
    favoriteSongs: List<String>,
    hotSearches: List<Pair<String, String>>,
    searchHistory: List<String>,
    suggestions: List<String>,
    searchQuery: String = "",
    searchType: Int,
    isLoading: Boolean,
    onSearch: (String, Int) -> Unit,
    onSuggestionFetch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (cp.player.model.Playlist) -> Unit,
    onAlbumClick: (cp.player.model.Playlist) -> Unit = onPlaylistClick,
    onArtistClick: (Artist) -> Unit = {},
    onLikeClick: (Song) -> Unit,
    onDownloadClick: ((Song) -> Unit)? = null,
    onPlaylistPlayAllClick: (cp.player.model.Playlist) -> Unit = {},
    onPlaylistAddToQueueClick: (cp.player.model.Playlist) -> Unit = {},
    currentSongId: String? = null,
    completedSongs: Set<String> = emptySet(),
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    var query by remember(searchQuery) { mutableStateOf(searchQuery) }
    var active by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedPlaylistForOptions by remember { mutableStateOf<cp.player.model.Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = {
                        query = it
                        if (it.isNotBlank()) {
                            onSuggestionFetch(it)
                        }
                    },
                    onSearch = {
                        onSearch(it, searchType)
                        active = false
                    },
                    expanded = active,
                    onExpandedChange = { active = it },
                    placeholder = { Text("Search songs, artists...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )
            },
            expanded = active,
            onExpandedChange = { active = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (active) 0.dp else 16.dp),
            windowInsets = WindowInsets.statusBars
        ) {
            if (query.isEmpty()) {
                // Search History
                if (searchHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Searches", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onClearHistory) {
                            Text("Clear")
                        }
                    }
                    searchHistory.forEachIndexed { index, historyItem ->
                        cp.player.ui.component.UnifiedListItem(
    onClick = { query = historyItem
                                    onSearch(historyItem, searchType)
                                    active = false },
                            shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, searchHistory.size),
                            headlineContent = { Text(historyItem) },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 1.dp)

                                ,
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            } else {
                // Suggestions
                suggestions.forEachIndexed { index, suggestion ->
                    cp.player.ui.component.UnifiedListItem(
    onClick = { query = suggestion
                                onSearch(suggestion, searchType)
                                active = false },
                        shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, suggestions.size),
                        headlineContent = { Text(suggestion) },
                        leadingContent = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 1.dp)

                            ,
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        if (!active) {
            if (isLoading) {

                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Filter Chips
            if (searchResults.isNotEmpty() || searchPlaylists.isNotEmpty() || searchArtists.isNotEmpty() || searchQuery.isNotEmpty()) {
                val types = listOf(
                    cp.player.api.MusicApiMethod.SEARCH_TYPE_SONG to "Songs",
                    cp.player.api.MusicApiMethod.SEARCH_TYPE_ALBUM to "Albums",
                    cp.player.api.MusicApiMethod.SEARCH_TYPE_ARTIST to "Artists",
                    cp.player.api.MusicApiMethod.SEARCH_TYPE_PLAYLIST to "Playlists"
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(types.size) { index ->
                        val (type, label) = types[index]
                        FilterChip(
                            selected = searchType == type,
                            onClick = {
                                val kw = query.ifBlank { searchQuery }
                                if (kw.isNotBlank()) {
                                    onSearch(kw, type)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            val columns = if (isWideScreen) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchResults.isEmpty() && searchPlaylists.isEmpty() && searchArtists.isEmpty() && searchQuery.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                        Text(
                            "Hot Searches",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    itemsIndexed(hotSearches, span = { _, _ -> androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) { index, hot ->
                        cp.player.ui.component.UnifiedListItem(
    onClick = { query = hot.first
                                    onSearch(hot.first, searchType) },
                            shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, hotSearches.size),
                            headlineContent = { Text(hot.first) },
                            supportingContent = { if (hot.second.isNotBlank()) Text(hot.second, maxLines = 1) },
                            leadingContent = {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp)
                                )
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
                            modifier = Modifier

                                ,
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                } else {
                    when (searchType) {
                        MusicApiMethod.SEARCH_TYPE_SONG -> {
                            itemsIndexed(searchResults, key = { index, song -> "${song.id}_$index" }) { index, song ->
                                SongItem(
                                    song = song,
                                    index = index,
                                    total = searchResults.size,
                                    isFavorite = favoriteSongs.contains(song.id),
                                    isCurrentlyPlaying = song.id == currentSongId,
                                    onOptionsClick = { selectedSongForOptions = song },
                                    onClick = { onSongClick(song) },
                                    showDivider = false,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                        MusicApiMethod.SEARCH_TYPE_ALBUM -> {
                            itemsIndexed(searchPlaylists, key = { index, playlist -> "${playlist.id}_$index" }) { index, playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    index = index,
                                    total = searchPlaylists.size,
                                    onClick = { onAlbumClick(playlist) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    trailingContent = {
                                        IconButton(onClick = { selectedPlaylistForOptions = playlist }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                                        }
                                    }
                                )
                            }
                        }
                        MusicApiMethod.SEARCH_TYPE_ARTIST -> {
                            itemsIndexed(searchArtists, key = { index, artist -> "${artist.id}_$index" }) { index, artist ->
                                ArtistItem(
                                    artist = artist,
                                    index = index,
                                    total = searchArtists.size,
                                    onClick = { onArtistClick(artist) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                        MusicApiMethod.SEARCH_TYPE_PLAYLIST -> {
                            itemsIndexed(searchPlaylists, key = { index, playlist -> "${playlist.id}_$index" }) { index, playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    index = index,
                                    total = searchPlaylists.size,
                                    onClick = { onPlaylistClick(playlist) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    trailingContent = {
                                        IconButton(onClick = { selectedPlaylistForOptions = playlist }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                                        }
                                    }
                                )
                            }
                        }
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
            onDownloadClick = onDownloadClick?.let { dl -> { dl(song) } }
        )
    }

    selectedPlaylistForOptions?.let { playlist ->
        cp.player.ui.component.PlaylistOptionsBottomSheet(
            playlist = playlist,
            onDismissRequest = { selectedPlaylistForOptions = null },
            onPlayClick = {
                onPlaylistPlayAllClick(playlist)
                selectedPlaylistForOptions = null
            },
            onAddToQueueClick = {
                onPlaylistAddToQueueClick(playlist)
                selectedPlaylistForOptions = null
            },
            onShareClick = {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "https://music.163.com/#/playlist?id=${playlist.id}")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                selectedPlaylistForOptions = null
            }
        )
    }
}
