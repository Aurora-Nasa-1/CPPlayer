package cp.player.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cp.player.R
import androidx.compose.material3.LinearProgressIndicator
import cp.player.model.Song
import cp.player.model.DownloadTask
import cp.player.model.DownloadStatus
import cp.player.manager.DownloadedSongMetadata
import cp.player.manager.LocalMusicManager
import cp.player.model.LocalSongMetadata
import cp.player.ui.component.SongItem
import cp.player.viewmodel.PlaybackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsContent(
    onPlayLocalSong: (Song, android.net.Uri) -> Unit,
    downloadedSongs: List<DownloadedSongMetadata>,
    localSongs: List<LocalSongMetadata> = emptyList(),
    tasks: Map<String, DownloadTask> = emptyMap(),
    onCancelDownload: (String) -> Unit = {},
    onDeleteLocalSong: (android.net.Uri) -> Unit = {},
    onRefreshLocalMusic: () -> Unit = {},
    favoriteSongs: List<String> = emptyList(),
    onLikeClick: (Song) -> Unit = {},
    playbackViewModel: PlaybackViewModel? = null,
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            if (selectedTabIndex == 1) {
                IconButton(onClick = { permissionLauncher.launch(permissionToRequest) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 16.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selectedTabIndex == 0) {
                val downloadingTasks = tasks.values.filter { it.status != DownloadStatus.COMPLETED }.toList()
                if (downloadingTasks.isNotEmpty()) {
                    item { Text(stringResource(R.string.downloading), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp)) }
                    itemsIndexed(downloadingTasks) { index, task ->
                        SongItem(
                            song = task.song,
                            isFavorite = false,
                            onClick = null,
                            onOptionsClick = null,
                            index = index,
                            total = downloadingTasks.size,
                            containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            trailingContent = {
                                IconButton(onClick = { onCancelDownload(task.song.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            },
                            supportingContent = {
                                Column {
                                    Text(task.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { if (task.progress >= 0f) task.progress else 0f },
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            if (task.progress >= 0f) "${(task.progress * 100).toInt()}%" else stringResource(R.string.connecting),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                val validDownloadedSongs = downloadedSongs.distinctBy { it.song.id }
                if (validDownloadedSongs.isNotEmpty()) {
                    item { Text(stringResource(R.string.downloaded), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)) }
                    itemsIndexed(validDownloadedSongs) { index, metadata ->
                        val uri = if (metadata.filePath?.startsWith("content://") == true) {
                            android.net.Uri.parse(metadata.filePath)
                        } else {
                            android.net.Uri.fromFile(java.io.File(metadata.filePath ?: ""))
                        }

                        // 封面回退链：持久化本地封面 > 缓存提取封面 > 云端 HTTP URL
                        val resolvedCoverUrl = remember(metadata.song.id, metadata.localCoverPath) {
                            metadata.localCoverPath
                                ?: cp.player.util.CoverArtExtractor.getOrExtract(context, metadata.song.id, metadata.filePath)
                                ?: metadata.song.albumArtUrl
                        }

                        var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }

                        SongItem(
                            song = metadata.song.copy(albumArtUrl = resolvedCoverUrl),
                            isFavorite = favoriteSongs.contains(metadata.song.id),
                            isCurrentlyPlaying = metadata.song.id == playbackViewModel?.currentSong?.id,
                            onClick = { onPlayLocalSong(metadata.song, uri) },
                            onOptionsClick = { selectedSongForOptions = metadata.song },
                            index = index,
                            total = validDownloadedSongs.size,
                            containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        selectedSongForOptions?.let { song ->
                            cp.player.ui.component.SongOptionsBottomSheet(
                                song = song,
                                isFavorite = favoriteSongs.contains(song.id),
                                onDismissRequest = { selectedSongForOptions = null },
                                onPlayClick = {
                                    onPlayLocalSong(song, uri)
                                    selectedSongForOptions = null
                                },
                                onFavoriteClick = {
                                    onLikeClick(song)
                                    selectedSongForOptions = null
                                },
                                onDeleteClick = {
                                    onDeleteLocalSong(uri)
                                    selectedSongForOptions = null
                                },
                                onAddToQueueClick = {
                                    playbackViewModel?.addToQueue(song)
                                    selectedSongForOptions = null
                                },
                                onNextClick = {
                                    playbackViewModel?.insertNext(song)
                                    selectedSongForOptions = null
                                }
                            )
                        }
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
                        val context = LocalContext.current

                        // 获取封面：云端绑定封面 > 内嵌封面 > null
                        val coverArtUrl = remember(localSong.songId, localSong.cloudSongId) {
                            LocalMusicManager.getCoverArt(context, localSong.songId, localSong.filePath)
                        }

                        val convertedSong = Song(
                            id = localSong.songId,
                            name = localSong.songName,
                            artist = localSong.artist,
                            album = localSong.album,
                            albumArtUrl = coverArtUrl
                        )

                        val binding = remember(localSong.songId) {
                            LocalMusicManager.getBinding(localSong.songId)
                        }

                        var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
                        var showBindSheet by remember { mutableStateOf(false) }

                        SongItem(
                            song = convertedSong,
                            isFavorite = false,
                            isCurrentlyPlaying = convertedSong.id == playbackViewModel?.currentSong?.id,
                            onClick = { onPlayLocalSong(convertedSong, uri) },
                            onOptionsClick = { selectedSongForOptions = convertedSong },
                            index = index,
                            total = localSongs.size,
                            containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 已关联云端歌曲标识
                                    if (binding != null) {
                                        Icon(
                                            Icons.Rounded.Cloud,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp).padding(end = 4.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Text(localSong.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        )

                        selectedSongForOptions?.let { song ->
                            cp.player.ui.component.SongOptionsBottomSheet(
                                song = song,
                                isFavorite = false,
                                onDismissRequest = { selectedSongForOptions = null },
                                onPlayClick = {
                                    onPlayLocalSong(song, uri)
                                    selectedSongForOptions = null
                                },
                                onFavoriteClick = { /* Local songs: no favorite */ },
                                onAddToQueueClick = {
                                    playbackViewModel?.addToQueue(song)
                                    selectedSongForOptions = null
                                },
                                onNextClick = {
                                    playbackViewModel?.insertNext(song)
                                    selectedSongForOptions = null
                                },
                                onBindCloudClick = {
                                    selectedSongForOptions = null
                                    showBindSheet = true
                                },
                                showFavorite = false,
                                showShare = false,
                                showPlaylist = false,
                                showInfo = false,
                                showBindCloud = true
                            )
                        }

                        // 关联云端歌曲搜索面板
                        if (showBindSheet) {
                            cp.player.ui.component.BindCloudSongSheet(
                                songName = localSong.songName,
                                artistName = localSong.artist,
                                onSongSelected = { cloudSong ->
                                    LocalMusicManager.bind(context, localSong.songId, cloudSong)
                                    showBindSheet = false
                                },
                                onDismissRequest = { showBindSheet = false }
                            )
                        }
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
