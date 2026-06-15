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
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.util.ImageUtils
import cp.player.viewmodel.PlaybackViewModel
import cp.player.viewmodel.UserViewModel
import cp.player.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSongOptionsBottomSheet(
    song: Song,
    isDownloaded: Boolean = false,
    qualityWifi: String = "Unknown",
    qualityCellular: String = "Unknown",
    sampleRate: Int = 0,
    bitrate: Int = 0,
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

    var infoText by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(song.id) {
        try {
            val result = ProviderManager.callApi("song/detail", mapOf("ids" to song.id))
            val json = org.json.JSONObject(result)
            if (json.has("songs")) {
                infoText = json.getJSONArray("songs").optJSONObject(0)?.toString(4)
            } else {
                infoText = json.toString(4)
            }
        } catch (e: Exception) {
            infoText = "No additional info."
        }
    }

    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = sheetContainerColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant,
                width = 48.dp,
                height = 4.dp
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Header: Cover, Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (song.albumArtUrl != null) {
                            AsyncImage(
                                model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 200),
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.padding(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Grid of Options
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerPillButton(
                            modifier = Modifier.weight(1f),
                            text = "Add to playlist",
                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            bgColor = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = onPlaylistClick ?: { showPlaylistDialog = true }
                        )
                        PlayerPillButton(
                            modifier = Modifier.weight(1f),
                            text = if (isDownloaded) "Downloaded" else "Download",
                            icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            bgColor = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                onDownloadClick?.invoke()
                                onDismissRequest()
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerPillButton(
                            modifier = Modifier.weight(1f),
                            text = "Sleep Timer",
                            icon = Icons.Rounded.Timer,
                            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = {
                                onSleepTimerClick?.invoke()
                                onDismissRequest()
                            }
                        )
                        PlayerPillButton(
                            modifier = Modifier.weight(1f),
                            text = "Not interested",
                            icon = Icons.Rounded.Block,
                            bgColor = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
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
                            text = "Song Information",
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
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Audio Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "WiFi Quality: $qualityWifi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cellular Quality: $qualityCellular",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sampleRate > 0) {
                            Text(
                                text = "Sample Rate: ${sampleRate}Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (bitrate > 0) {
                            Text(
                                text = "Bitrate: ${bitrate / 1000}kbps",
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
                            Text("USB Exclusive: ${if (isUsbActive) "Active" else "Inactive"}", style = MaterialTheme.typography.bodyMedium, color = if (isUsbActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)

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

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Additional Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (infoText != null) {
                            Text(
                                text = infoText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                val playlists = userViewModel.userPlaylists
                if (playlists.isEmpty()) {
                    Text("No playlists found.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable {
                                    userViewModel.addSongsToPlaylist(playlist.id, listOf(song.id), null)
                                    showToast("Added to ${playlist.name}")
                                    showPlaylistDialog = false
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlayerPillButton(
    modifier: Modifier,
    text: String,
    icon: ImageVector,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = bgColor,
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text, tint = textColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}