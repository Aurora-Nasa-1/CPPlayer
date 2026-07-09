package cp.player.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    isScanning: Boolean = false,
    favoriteSongs: List<String> = emptyList(),
    onLikeClick: (Song) -> Unit = {},
    playbackViewModel: PlaybackViewModel? = null,
    onAddToPlaylist: ((Long, List<String>) -> Unit)? = null,
    allPlaylists: List<cp.player.model.Playlist> = emptyList(),
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(R.string.downloaded), stringResource(R.string.system_music_folder))

    // 系统音乐文件夹多选状态
    var selectedLocalSongs by remember { mutableStateOf(setOf<String>()) }
    var isLocalSelectionMode by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf("") }

    // 本地歌曲排序状态：0=默认, 1=按名称, 2=按歌手
    var localSortMode by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 云端绑定状态（响应式，绑定后自动更新 UI）
    val bindings by LocalMusicManager.bindingsFlow.collectAsState()

    // 退出选择模式的 BackHandler
    BackHandler(enabled = isLocalSelectionMode) {
        isLocalSelectionMode = false
        selectedLocalSongs = emptySet()
    }

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

    // 检查权限是否已授予
    fun hasPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, permissionToRequest
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (hasPermission()) {
            onRefreshLocalMusic()
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    // 选择模式下的顶部栏
    if (isLocalSelectionMode && selectedTabIndex == 1) {
        cp.player.ui.component.AppScaffold(
            title = stringResource(R.string.selected_count, selectedLocalSongs.size),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIcon = {
                IconButton(onClick = {
                    isLocalSelectionMode = false
                    selectedLocalSongs = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            },
            actions = {
                // 全选/取消全选
                IconButton(onClick = {
                    if (selectedLocalSongs.size == localSongs.size) {
                        selectedLocalSongs = emptySet()
                    } else {
                        selectedLocalSongs = localSongs.map { it.songId }.toSet()
                    }
                }) {
                    Icon(
                        Icons.Rounded.Checklist,
                        contentDescription = if (selectedLocalSongs.size == localSongs.size) stringResource(R.string.deselect_all) else stringResource(R.string.select_all)
                    )
                }
                // 添加到队列
                IconButton(onClick = {
                    val selectedList = localSongs.filter { selectedLocalSongs.contains(it.songId) }
                    selectedList.forEach { localSong ->
                        val uri = android.net.Uri.parse(localSong.albumArtUrl)
                        val song = Song(
                            id = localSong.songId,
                            name = localSong.songName,
                            artist = localSong.artist,
                            album = localSong.album
                        )
                        playbackViewModel?.addToQueue(song)
                    }
                    android.widget.Toast.makeText(context, context.getString(R.string.added_to_queue, selectedList.size), android.widget.Toast.LENGTH_SHORT).show()
                    isLocalSelectionMode = false
                    selectedLocalSongs = emptySet()
                }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add to Queue")
                }
                // 自动同步云端
                IconButton(onClick = {
                    val selectedList = localSongs.filter { selectedLocalSongs.contains(it.songId) }
                    if (selectedList.isEmpty()) return@IconButton
                    isSyncing = true
                    scope.launch {
                        val (success, skipped, failed) = LocalMusicManager.autoBindBatch(selectedList, context)
                        isSyncing = false
                        isLocalSelectionMode = false
                        selectedLocalSongs = emptySet()
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.sync_cloud_done, success, skipped, failed),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        onRefreshLocalMusic()
                    }
                }) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = "Auto Sync Cloud")
                }
            }
        ) { innerPadding ->
            DownloadsMainContent(
                innerPadding = innerPadding,
                selectedTabIndex = selectedTabIndex,
                onTabChange = { selectedTabIndex = it },
                tabs = tabs,
                onRefreshClick = {
                    if (hasPermission()) onRefreshLocalMusic() else permissionLauncher.launch(permissionToRequest)
                },
                isScanning = isScanning,
                // 下载相关
                tasks = tasks,
                onCancelDownload = onCancelDownload,
                downloadedSongs = downloadedSongs,
                favoriteSongs = favoriteSongs,
                onLikeClick = onLikeClick,
                onPlayLocalSong = onPlayLocalSong,
                onDeleteLocalSong = onDeleteLocalSong,
                playbackViewModel = playbackViewModel,
                // 本地音乐相关
                localSongs = localSongs,
                isLocalSelectionMode = isLocalSelectionMode,
                selectedLocalSongs = selectedLocalSongs,
                onLocalSelectionChange = { id, selected ->
                    selectedLocalSongs = if (selected) selectedLocalSongs + id else selectedLocalSongs - id
                },
                onToggleLocalSelectionMode = {
                    isLocalSelectionMode = it
                    if (!it) selectedLocalSongs = emptySet()
                },
                localSortMode = localSortMode,
                onLocalSortModeChange = { localSortMode = it },
                bottomContentPadding = bottomContentPadding,
                bindings = bindings
            )
        }
    } else {
        DownloadsMainContent(
            selectedTabIndex = selectedTabIndex,
            onTabChange = { selectedTabIndex = it },
            tabs = tabs,
            onRefreshClick = {
                if (hasPermission()) onRefreshLocalMusic() else permissionLauncher.launch(permissionToRequest)
            },
            isScanning = isScanning,
            // 下载相关
            tasks = tasks,
            onCancelDownload = onCancelDownload,
            downloadedSongs = downloadedSongs,
            favoriteSongs = favoriteSongs,
            onLikeClick = onLikeClick,
            onPlayLocalSong = onPlayLocalSong,
            onDeleteLocalSong = onDeleteLocalSong,
            playbackViewModel = playbackViewModel,
            // 本地音乐相关
            localSongs = localSongs,
            isLocalSelectionMode = isLocalSelectionMode,
            selectedLocalSongs = selectedLocalSongs,
            onLocalSelectionChange = { id, selected ->
                selectedLocalSongs = if (selected) selectedLocalSongs + id else selectedLocalSongs - id
            },
            onToggleLocalSelectionMode = {
                isLocalSelectionMode = it
                if (!it) selectedLocalSongs = emptySet()
            },
            localSortMode = localSortMode,
            onLocalSortModeChange = { localSortMode = it },
            bottomContentPadding = bottomContentPadding,
            bindings = bindings
        )
    }

    // 同步进度对话框
    if (isSyncing) {
        AlertDialog(
            onDismissRequest = { /* 不允许关闭 */ },
            title = { Text(stringResource(R.string.sync_cloud_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text(stringResource(R.string.sync_cloud_progress))
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsMainContent(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    selectedTabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabs: List<String>,
    onRefreshClick: () -> Unit,
    isScanning: Boolean = false,
    // 下载相关
    tasks: Map<String, DownloadTask>,
    onCancelDownload: (String) -> Unit,
    downloadedSongs: List<DownloadedSongMetadata>,
    favoriteSongs: List<String>,
    onLikeClick: (Song) -> Unit,
    onPlayLocalSong: (Song, android.net.Uri) -> Unit,
    onDeleteLocalSong: (android.net.Uri) -> Unit,
    playbackViewModel: PlaybackViewModel?,
    // 本地音乐相关
    localSongs: List<LocalSongMetadata>,
    isLocalSelectionMode: Boolean,
    selectedLocalSongs: Set<String>,
    onLocalSelectionChange: (String, Boolean) -> Unit,
    onToggleLocalSelectionMode: (Boolean) -> Unit,
    localSortMode: Int = 0,
    onLocalSortModeChange: (Int) -> Unit = {},
    bottomContentPadding: PaddingValues,
    bindings: Map<String, cp.player.manager.LocalMusicManager.CloudBinding> = emptyMap()
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                        onClick = { onTabChange(index) },
                        text = { Text(title) }
                    )
                }
            }
            if (selectedTabIndex == 1 && !isLocalSelectionMode) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefreshClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        val sortedLocalSongs = remember(localSongs, localSortMode) {
            when (localSortMode) {
                1 -> localSongs.sortedBy { it.songName.lowercase() }
                2 -> localSongs.sortedBy { it.artist.lowercase() }
                else -> localSongs
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 16.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selectedTabIndex == 0) {
                val downloadingTasks = tasks.values.filter { it.status != DownloadStatus.COMPLETED }.toList()
                if (downloadingTasks.isNotEmpty()) {
                    item { Text(stringResource(R.string.downloading), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp)) }
                    itemsIndexed(downloadingTasks, key = { _, task -> task.song.id }) { index, task ->
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
                    itemsIndexed(validDownloadedSongs, key = { _, metadata -> metadata.song.id }) { index, metadata ->
                        val uri = if (metadata.filePath?.startsWith("content://") == true) {
                            android.net.Uri.parse(metadata.filePath)
                        } else {
                            android.net.Uri.fromFile(java.io.File(metadata.filePath ?: ""))
                        }

                        // 封面回退链：持久化本地封面 > 缓存提取封面 > 云端 HTTP URL
                        // 异步加载，避免 MediaMetadataRetriever 阻塞 composition
                        val resolvedCoverUrl by produceState<String?>(initialValue = metadata.localCoverPath ?: metadata.song.albumArtUrl) {
                            if (metadata.localCoverPath != null) return@produceState
                            value = withContext(Dispatchers.IO) {
                                cp.player.util.CoverArtExtractor.getOrExtract(context, metadata.song.id, metadata.filePath)
                            } ?: metadata.song.albumArtUrl
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
                    // 本地歌曲控制栏：全部播放 + 排序
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 全部播放按钮
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = {
                                    val sortedSongs = when (localSortMode) {
                                        1 -> localSongs.sortedBy { it.songName.lowercase() }
                                        2 -> localSongs.sortedBy { it.artist.lowercase() }
                                        else -> localSongs
                                    }
                                    val songs = sortedSongs.map {
                                        Song(id = it.songId, name = it.songName, artist = it.artist, album = it.album)
                                    }
                                    if (songs.isNotEmpty()) {
                                        playbackViewModel?.playSong(songs.first(), songs)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.play_it),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // 排序按钮
                            var showSortMenu by remember { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                onClick = { showSortMenu = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        when (localSortMode) {
                                            1 -> stringResource(R.string.sort_by_name_cn)
                                            2 -> stringResource(R.string.sort_by_artist_cn)
                                            else -> stringResource(R.string.sort_by_name)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_name)) },
                                    onClick = { onLocalSortModeChange(1); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.SortByAlpha, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_artist)) },
                                    onClick = { onLocalSortModeChange(2); showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                                )
                            }
                        }
                    }

                    itemsIndexed(sortedLocalSongs, key = { _, it -> it.songId }) { index, localSong ->
                        val uri = android.net.Uri.parse(localSong.albumArtUrl)

                        // 获取封面：云端绑定封面 > 内嵌封面 > null
                        val binding = bindings[localSong.songId]
                        val coverArtUrl by produceState<String?>(initialValue = binding?.cloudCoverUrl) {
                            if (binding?.cloudCoverUrl != null) return@produceState
                            value = withContext(Dispatchers.IO) {
                                cp.player.util.CoverArtExtractor.getOrExtract(
                                    context, localSong.songId, localSong.filePath,
                                    // content:// URI 作为回退（Scoped Storage 下 DATA 列可能无效）
                                    contentUri = if (localSong.albumArtUrl?.startsWith("content://") == true) localSong.albumArtUrl else null
                                )
                            }
                        }

                        val convertedSong = Song(
                            id = localSong.songId,
                            name = localSong.songName,
                            artist = localSong.artist,
                            album = localSong.album,
                            albumArtUrl = coverArtUrl
                        )

                        val isSelected = selectedLocalSongs.contains(localSong.songId)

                        var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
                        var showBindSheet by remember { mutableStateOf(false) }

                        SongItem(
                            song = convertedSong,
                            isFavorite = false,
                            isCurrentlyPlaying = convertedSong.id == playbackViewModel?.currentSong?.id,
                            onClick = {
                                if (isLocalSelectionMode) {
                                    onLocalSelectionChange(localSong.songId, !isSelected)
                                } else {
                                    onPlayLocalSong(convertedSong, uri)
                                }
                            },
                            onLongClick = {
                                if (!isLocalSelectionMode) {
                                    onToggleLocalSelectionMode(true)
                                    onLocalSelectionChange(localSong.songId, true)
                                }
                            },
                            onOptionsClick = if (!isLocalSelectionMode) { { selectedSongForOptions = convertedSong } } else null,
                            index = index,
                            total = sortedLocalSongs.size,
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            leadingContent = if (isLocalSelectionMode) {
                                {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onLocalSelectionChange(localSong.songId, it) }
                                    )
                                }
                            } else null,
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
