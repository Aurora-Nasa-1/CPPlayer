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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import cp.player.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.util.ImageUtils
import cp.player.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSongOptionsBottomSheet(
    song: Song,
    isDownloaded: Boolean = false,
    sampleRate: Int = 0,
    bitrate: Int = 0,
    lyricsInfo: cp.player.model.LyricsInfo? = null,
    onDismissRequest: () -> Unit,
    onPlaylistClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onSleepTimerClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onDislikeClick: (() -> Unit)? = null
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
    val userViewModel: UserViewModel = viewModel(viewModelStoreOwner = owner)
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val handleShare = onShareClick ?: {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, "Check out this song: ${song.name} by ${song.artist}\nhttps://music.163.com/song?id=${song.id}")
            type = "text/plain"
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
        onDismissRequest()
    }

    val showToast = { msg: String ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SongHeader(
                    song = song,
                    trailingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = handleShare)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.share),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                )
            }

            item {
                // Grid of Options
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.add_to_playlist_action),
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            bgColor = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            height = 56.dp,
                            onClick = onPlaylistClick ?: { showPlaylistDialog = true }
                        )
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = if (isDownloaded) stringResource(R.string.downloaded_action) else stringResource(R.string.download_action),
                            icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            bgColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            height = 56.dp,
                            onClick = {
                                onDownloadClick?.invoke()
                                onDismissRequest()
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.sleep_timer),
                            icon = Icons.Rounded.Timer,
                            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            height = 56.dp,
                            onClick = {
                                onSleepTimerClick?.invoke()
                                onDismissRequest()
                            }
                        )
                        PillButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.not_interested_action),
                            icon = Icons.Rounded.Block,
                            bgColor = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                            height = 56.dp,
                            onClick = {
                                onDislikeClick?.invoke()
                                onDismissRequest()
                            }
                        )
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.song_information),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ID: ${song.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Album: ${song.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 歌词信息
                        if (lyricsInfo != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.lyrics_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.lyrics_source_label, lyricsInfo.source),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.lyrics_format_label, lyricsInfo.format),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.lyrics_word_by_word, if (lyricsInfo.hasWordLevel) stringResource(R.string.lyrics_supported) else stringResource(R.string.lyrics_not_supported)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lyricsInfo.hasWordLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lyricsInfo.hasTranslation) {
                                Text(
                                    text = stringResource(R.string.lyrics_translation),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (lyricsInfo.hasPhonetic) {
                                Text(
                                    text = stringResource(R.string.lyrics_transliteration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.audio_quality_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (sampleRate > 0) {
                            Text(
                                text = stringResource(R.string.sample_rate_label, sampleRate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (bitrate > 0) {
                            Text(
                                text = stringResource(R.string.bitrate_label, bitrate / 1000),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val engineType = cp.player.util.UserPreferences.getAudioEngine(context)
                        if (engineType == 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Hi-Fi & USB DAC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            val isUsbActive = remember<Boolean> { cp.player.engine.RustEngine.isRustDirectUsbSessionActive() }
                            Text(stringResource(R.string.usb_exclusive_status, if (isUsbActive) stringResource(R.string.usb_active) else stringResource(R.string.usb_inactive)), style = MaterialTheme.typography.bodyMedium, color = if (isUsbActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)

                            val hasHwVolume = remember<Boolean> { cp.player.engine.RustEngine.hasRustDirectUsbHardwareVolume() }
                            if (hasHwVolume) {
                                var hwVolume by remember { mutableStateOf(cp.player.engine.RustEngine.getRustDirectUsbHardwareVolume().toFloat()) }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Hardware Volume: ${(hwVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = hwVolume,
                                    onValueChange = {
                                        hwVolume = it
                                        cp.player.engine.RustEngine.setRustDirectUsbHardwareVolume(it.toDouble())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

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
}
