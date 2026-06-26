package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Song
import cp.player.util.ImageUtils
import cp.player.viewmodel.PlaybackViewModel
import cp.player.viewmodel.UserViewModel
import cp.player.api.MusicApiServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismissRequest: () -> Unit,
    onPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    onAddToQueueClick: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
    onPlaylistClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onSetAsSoundClick: (() -> Unit)? = null,
    onOptionsClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    onBindCloudClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    /** 是否显示收藏按钮（云盘/本地歌曲设为 false） */
    showFavorite: Boolean = true,
    /** 是否显示分享按钮（云盘/本地歌曲设为 false） */
    showShare: Boolean = true,
    /** 是否显示添加到歌单按钮（云盘/本地歌曲设为 false） */
    showPlaylist: Boolean = true,
    /** 是否显示 INFO 按钮（云盘/本地歌曲设为 false） */
    showInfo: Boolean = true,
    /** 是否显示关联云端歌曲按钮 */
    showBindCloud: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val owner = activity ?: LocalViewModelStoreOwner.current!!

    val playbackViewModel: PlaybackViewModel = viewModel(viewModelStoreOwner = owner)
    val userViewModel: UserViewModel = viewModel(viewModelStoreOwner = owner)
    val scope = rememberCoroutineScope()

    var showInfoDialog by remember { mutableStateOf(false) }
    var songDetailMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val handleShare = onShareClick ?: {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.name} by ${song.artist}\nhttps://music.163.com/song?id=${song.id}")
            type = "text/plain"
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share)))
        onDismissRequest()
    }

    val showToast = { msg: String ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val handleAddToQueue = onAddToQueueClick ?: {
        playbackViewModel.addToQueue(song)
        showToast(context.getString(R.string.add_to_queue))
        onDismissRequest()
    }

    val handlePlayNext = onNextClick ?: {
        playbackViewModel.insertNext(song)
        showToast(context.getString(R.string.play_next))
        onDismissRequest()
    }

    // Monet dynamic colors using MaterialTheme.colorScheme
    val playColor = MaterialTheme.colorScheme.primaryContainer
    val playOnColor = MaterialTheme.colorScheme.onPrimaryContainer

    val circleBtnColor = MaterialTheme.colorScheme.secondaryContainer
    val circleBtnOnColor = MaterialTheme.colorScheme.onSecondaryContainer

    val queueColor = MaterialTheme.colorScheme.tertiaryContainer
    val queueOnColor = MaterialTheme.colorScheme.onTertiaryContainer

    val nextColor = MaterialTheme.colorScheme.primary
    val nextOnColor = MaterialTheme.colorScheme.onPrimary

    val playlistColor = MaterialTheme.colorScheme.surfaceVariant
    val playlistOnColor = MaterialTheme.colorScheme.onSurfaceVariant

    val infoBtnColor = MaterialTheme.colorScheme.secondaryContainer
    val infoBtnOnColor = MaterialTheme.colorScheme.onSecondaryContainer

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest,
        bottomPadding = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SongHeader(song = song)

            // Row 1: Play, Favorite (optional), Share (optional)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = playColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .clickable(onClick = onPlayClick)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = playOnColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.play), color = playOnColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Favorite (only when showFavorite is true)
                if (showFavorite) {
                    Surface(
                        shape = CircleShape,
                        color = circleBtnColor,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable(onClick = onFavoriteClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = circleBtnOnColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Share (only when showShare is true)
                if (showShare) {
                    Surface(
                        shape = CircleShape,
                        color = circleBtnColor,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable(onClick = handleShare)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = circleBtnOnColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Row 2: Add to queue, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    modifier = Modifier.weight(1.2f),
                    text = stringResource(R.string.add_to_queue),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    bgColor = queueColor,
                    textColor = queueOnColor,
                    onClick = handleAddToQueue
                )
                PillButton(
                    modifier = Modifier.weight(0.8f),
                    text = stringResource(R.string.play_next),
                    icon = Icons.Rounded.SkipNext,
                    bgColor = nextColor,
                    textColor = nextOnColor,
                    onClick = handlePlayNext
                )
            }

            // Row 3: Download, Playlist (optional), INFO (optional), Delete (optional)
            val downloadBtnColor = if (isDownloaded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
            val downloadBtnOnColor = if (isDownloaded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
            val hasRow3 = onDownloadClick != null || showPlaylist || showInfo || onDeleteClick != null || showBindCloud
            if (hasRow3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onDownloadClick != null) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = if (isDownloaded) stringResource(R.string.downloaded_action) else stringResource(R.string.download_action),
                            icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            bgColor = downloadBtnColor,
                            textColor = downloadBtnOnColor,
                            onClick = {
                                if (!isDownloaded) {
                                    onDownloadClick()
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                    if (showPlaylist) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.playlist),
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            bgColor = playlistColor,
                            textColor = playlistOnColor,
                            onClick = onPlaylistClick ?: { showPlaylistDialog = true }
                        )
                    }
                    if (showInfo) {
                        // 圆形图标按钮，与其他按钮高度对齐
                        Surface(
                            shape = CircleShape,
                            color = infoBtnColor,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable(onClick = onInfoClick ?: {
                                    showInfoDialog = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val body = MusicApiServiceFactory.instance.getSongDetail(listOf(song.id))
                                            val songs = body.get("songs")?.asJsonArray
                                            val obj = songs?.firstOrNull()?.asJsonObject
                                            val map = linkedMapOf<String, String>()
                                            if (obj != null) {
                                                map[context.getString(R.string.detail_song_name)] = obj.get("name")?.asString ?: ""
                                                // 歌手列表
                                                val artists = obj.get("ar")?.asJsonArray ?: obj.get("artists")?.asJsonArray
                                                map[context.getString(R.string.detail_artist)] = artists?.joinToString(", ") {
                                                    it.asJsonObject.get("name")?.asString ?: ""
                                                } ?: ""
                                                // 专辑
                                                val album = obj.get("al")?.asJsonObject ?: obj.get("album")?.asJsonObject
                                                map[context.getString(R.string.detail_album)] = album?.get("name")?.asString ?: ""
                                                // 时长
                                                val dt = obj.get("dt")?.asLong ?: obj.get("duration")?.asLong ?: 0L
                                                val min = dt / 1000 / 60
                                                val sec = (dt / 1000) % 60
                                                map[context.getString(R.string.detail_duration)] = "%d:%02d".format(min, sec)
                                                // 发行时间
                                                val publishTime = obj.get("publishTime")?.asLong ?: 0L
                                                if (publishTime > 0) {
                                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                                    map[context.getString(R.string.detail_publish_time)] = sdf.format(java.util.Date(publishTime))
                                                }
                                                // 评论数
                                                val commentCount = obj.get("commentCount")?.asLong ?: 0L
                                                if (commentCount > 0) map[context.getString(R.string.detail_comment_count)] = commentCount.toString()
                                                // MV
                                                val mvId = obj.get("mv")?.asInt ?: 0
                                                if (mvId > 0) map["MV ID"] = mvId.toString()
                                                // 音质信息
                                                val privilege = body.get("privileges")?.asJsonArray?.firstOrNull()?.asJsonObject
                                                if (privilege != null) {
                                                    val maxBr = privilege.get("maxbr")?.asInt ?: 0
                                                    if (maxBr > 0) map[context.getString(R.string.detail_max_bitrate)] = "${maxBr / 1000}kbps"
                                                    val fee = privilege.get("fee")?.asInt ?: -1
                                                    map[context.getString(R.string.detail_pay_type)] = when (fee) {
                                                        0 -> context.getString(R.string.pay_type_free)
                                                        1 -> context.getString(R.string.pay_type_vip)
                                                        4 -> context.getString(R.string.pay_type_paid_album)
                                                        8 -> context.getString(R.string.pay_type_free_vip)
                                                        else -> context.getString(R.string.pay_type_unknown)
                                                    }
                                                }
                                                map[context.getString(R.string.detail_song_id)] = song.id
                                            }
                                            withContext(Dispatchers.Main) {
                                                songDetailMap = map
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                songDetailMap = mapOf(context.getString(R.string.detail_error) to (e.message ?: "Unknown error"))
                                            }
                                        }
                                    }
                                    Unit
                                })
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.info),
                                    tint = infoBtnOnColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    // Delete button (for downloaded songs etc.)
                    if (onDeleteClick != null) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.delete),
                            icon = Icons.Rounded.Delete,
                            bgColor = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = {
                                onDeleteClick()
                                onDismissRequest()
                            }
                        )
                    }
                    // 关联云端歌曲按钮
                    if (showBindCloud) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.bind_cloud_song),
                            icon = Icons.Rounded.Cloud,
                            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = {
                                onBindCloudClick?.invoke()
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        cp.player.ui.component.AddToPlaylistBottomSheet(
            playlists = userViewModel.userPlaylists,
            onDismissRequest = { showPlaylistDialog = false },
            onPlaylistSelected = { playlist ->
                userViewModel.addSongsToPlaylist(playlist.id, listOf(song.id), null)
                showToast("Added to ${playlist.name}")
                showPlaylistDialog = false
                onDismissRequest()
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.song_details)) },
            text = {
                if (songDetailMap.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        songDetailMap.forEach { (label, value) ->
                            item {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}
