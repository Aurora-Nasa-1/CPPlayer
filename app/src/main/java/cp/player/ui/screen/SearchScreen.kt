package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import cp.player.ui.component.WavyLinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.model.Song
import cp.player.ui.component.SongItem
import cp.player.ui.component.PlaylistItem
import cp.player.ui.component.ExpressiveShapes

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun SearchScreen(
    searchResults: List<Song>,
    searchPlaylists: List<cp.player.model.Playlist>,
    favoriteSongs: List<String>,
    hotSearches: List<Pair<String, String>>,
    searchHistory: List<String>,
    suggestions: List<String>,
    searchType: Int,
    isLoading: Boolean,
    onSearch: (String, Int) -> Unit,
    onSuggestionFetch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (cp.player.model.Playlist) -> Unit,
    onLikeClick: (Song) -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

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
                        val shape = ExpressiveShapes.calculateShape(index, searchHistory.size)
                        ListItem(
                            headlineContent = { Text(historyItem) },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 1.dp)
                                .clip(shape)
                                .clickable {
                                    query = historyItem
                                    onSearch(historyItem, searchType)
                                    active = false
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            } else {
                // Suggestions
                suggestions.forEachIndexed { index, suggestion ->
                    val shape = ExpressiveShapes.calculateShape(index, suggestions.size)
                    ListItem(
                        headlineContent = { Text(suggestion) },
                        leadingContent = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 1.dp)
                            .clip(shape)
                            .clickable {
                                query = suggestion
                                onSearch(suggestion, searchType)
                                active = false
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        if (!active) {
            if (isLoading) {

                WavyLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Filter Chips
            if (searchResults.isNotEmpty() || query.isNotEmpty()) {
                val types = listOf(
                    1 to "Songs",
                    10 to "Albums",
                    100 to "Artists",
                    1000 to "Playlists"
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
                                if (query.isNotBlank()) {
                                    onSearch(query, type)
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
                if (searchResults.isEmpty() && searchPlaylists.isEmpty() && query.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                        Text(
                            "Hot Searches",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    itemsIndexed(hotSearches, span = { _, _ -> androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) { index, hot ->
                        val shape = ExpressiveShapes.calculateShape(index, hotSearches.size)
                        ListItem(
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
                                .clip(shape)
                                .clickable {
                                    query = hot.first
                                    onSearch(hot.first, searchType)
                                },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                } else {
                    if (searchType == 1) {
                        itemsIndexed(searchResults, key = { _, song -> song.id }) { index, song ->
                            val shape = ExpressiveShapes.calculateShape(index, searchResults.size)
                            SongItem(
                                song = song,
                                isFavorite = favoriteSongs.contains(song.id),
                                onLikeClick = { onLikeClick(song) },
                                onClick = { onSongClick(song) },
                                showDivider = false,
                                shape = shape,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    } else if (searchType == 10) {
                        itemsIndexed(searchPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                            val shape = ExpressiveShapes.calculateShape(index, searchPlaylists.size)
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                shape = shape,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    } else if (searchType == 100) {
                        itemsIndexed(searchPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                            val shape = ExpressiveShapes.calculateShape(index, searchPlaylists.size)
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                shape = shape,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    } else if (searchType == 1000) {
                        itemsIndexed(searchPlaylists, key = { _, playlist -> playlist.id }) { index, playlist ->
                            val shape = ExpressiveShapes.calculateShape(index, searchPlaylists.size)
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                shape = shape,
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }
    }
}
