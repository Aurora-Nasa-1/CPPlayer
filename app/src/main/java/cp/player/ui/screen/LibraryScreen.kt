package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.util.ImageUtils
import cp.player.model.Playlist
import cp.player.ui.component.PlaylistItem
import cp.player.ui.component.ExpressiveShapes
import cp.player.ui.component.AppScaffold
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LibraryScreen(
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToLiveSort: () -> Unit,
    onNavigateToSettings: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    AppScaffold(
        title = {
            Text(
                stringResource(R.string.your_library),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal
            )
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
    ) { innerPadding ->
        val columns = if (isWideScreen) 2 else 1
        val topItemsCount = 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.downloads), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.offline_music), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.padding(16.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(ExpressiveShapes.calculateShape(0, topItemsCount))
                        .clickable { onNavigateToDownloads() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cloud_music), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.cloud_music), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.padding(16.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(ExpressiveShapes.calculateShape(1, topItemsCount))
                        .clickable { onNavigateToCloud() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.live_sort), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.smart_reordering), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.padding(16.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(ExpressiveShapes.calculateShape(2, topItemsCount))
                        .clickable { onNavigateToLiveSort() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            itemsIndexed(
                items = userPlaylists,
                key = { _, it -> it.id },
                contentType = { _, _ -> "playlist" }
            ) { index, playlist ->
                PlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = ExpressiveShapes.calculateShape(index, userPlaylists.size),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}
