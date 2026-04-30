package cp.player.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.WavyLinearProgressIndicator
import cp.player.ui.component.ExpressiveShapes
import cp.player.model.Song
import cp.player.model.DownloadTask
import cp.player.model.DownloadStatus
import cp.player.manager.DownloadedSongMetadata
import cp.player.model.LocalSongMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackPressed: () -> Unit,
    onPlayLocalSong: (Song, android.net.Uri) -> Unit,
    downloadedSongs: List<DownloadedSongMetadata>,
    localSongs: List<LocalSongMetadata> = emptyList(),
    tasks: Map<String, DownloadTask> = emptyMap(),
    onCancelDownload: (String) -> Unit = {},
    onDeleteLocalSong: (android.net.Uri) -> Unit = {},
    onRefreshLocalMusic: () -> Unit = {},
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(R.string.downloaded), stringResource(R.string.system_music_folder))

    val context = LocalContext.current
    
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onRefreshLocalMusic()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionToRequest)
    }

    AppScaffold(
        title = stringResource(R.string.offline_music),
        onBackPressed = onBackPressed,
        actions = {
            if (selectedTabIndex == 1) {
                IconButton(onClick = { permissionLauncher.launch(permissionToRequest) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedTabIndex == 0) {
                    val downloadingTasks = tasks.values.filter { it.status != DownloadStatus.COMPLETED }.toList()
                    if (downloadingTasks.isNotEmpty()) {
                        item { Text(stringResource(R.string.downloading), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp)) }
                        itemsIndexed(downloadingTasks) { index, task ->
                            ListItem(
                                headlineContent = { Text(task.song.name) },
                                supportingContent = {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(task.song.artist, style = MaterialTheme.typography.bodySmall)
                                            Text(if (task.progress >= 0f) "${(task.progress * 100).toInt()}%" else stringResource(R.string.connecting), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        WavyLinearProgressIndicator(
                                            progress = { if (task.progress >= 0f) task.progress else 0f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onCancelDownload(task.song.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(ExpressiveShapes.calculateShape(index, downloadingTasks.size)),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    }

                    val validDownloadedSongs = downloadedSongs.filter { true }
                    if (validDownloadedSongs.isNotEmpty()) {
                        item { Text(stringResource(R.string.downloaded), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)) }
                        itemsIndexed(validDownloadedSongs) { index, metadata ->
                            val uri = if (metadata.filePath?.startsWith("content://") == true) {
                                android.net.Uri.parse(metadata.filePath)
                            } else {
                                android.net.Uri.fromFile(java.io.File(metadata.filePath ?: ""))
                            }
                            ListItem(
                                headlineContent = { Text(metadata.song.name) },
                                supportingContent = { Text(metadata.song.artist) },
                                leadingContent = {
                                    Surface(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(12.dp))
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteLocalSong(uri) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.calculateShape(index, validDownloadedSongs.size))
                                    .clickable { onPlayLocalSong(metadata.song, uri) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    }

                    if (downloadedSongs.isEmpty() && downloadingTasks.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_downloads), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    if (localSongs.isNotEmpty()) {
                        itemsIndexed(localSongs) { index, localSong ->
                            val uri = android.net.Uri.parse(localSong.albumArtUrl)
                            val convertedSong = Song(
                                id = localSong.songId,
                                name = localSong.songName,
                                artist = localSong.artist,
                                album = localSong.album,
                                albumArtUrl = localSong.albumArtUrl
                            )
                            ListItem(
                                headlineContent = { Text(localSong.songName, maxLines = 1) },
                                supportingContent = { Text(localSong.artist, maxLines = 1) },
                                leadingContent = {
                                    Surface(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(ExpressiveShapes.calculateShape(index, localSongs.size))
                                    .clickable { onPlayLocalSong(convertedSong, uri) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No local music found or missing permission.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
