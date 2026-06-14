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
    onSetAsSoundClick: (() -> Unit)? = null,
    onOptionsClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null
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
    var infoText by remember { mutableStateOf("Loading...") }
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

    val handleAddToQueue = onAddToQueueClick ?: {
        playbackViewModel.addToQueue(song)
        showToast("Added to queue")
        onDismissRequest()
    }
    
    val handlePlayNext = onNextClick ?: {
        playbackViewModel.insertNext(song)
        showToast("Playing next")
        onDismissRequest()
    }

    // Monet dynamic colors using MaterialTheme.colorScheme
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    
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
    
    val infoBtnColor = MaterialTheme.colorScheme.surface
    val infoBtnOnColor = MaterialTheme.colorScheme.onSurface

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Cover, Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover
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

                // Title and Artist
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
            }

            // Row 1: Play, Favorite, Share
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
                        Text("Play", color = playOnColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Favorite
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
                            contentDescription = "Favorite",
                            tint = circleBtnOnColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Share
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
                            contentDescription = "Share",
                            tint = circleBtnOnColor,
                            modifier = Modifier.size(32.dp)
                        )
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
                    text = "Add to queue",
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    bgColor = queueColor,
                    textColor = queueOnColor,
                    onClick = handleAddToQueue
                )
                PillButton(
                    modifier = Modifier.weight(0.8f),
                    text = "Next",
                    icon = Icons.Rounded.SkipNext,
                    bgColor = nextColor,
                    textColor = nextOnColor,
                    onClick = handlePlayNext
                )
            }

            // Row 3: Playlist, INFO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    modifier = Modifier.weight(1f),
                    text = "Playlist",
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    bgColor = playlistColor,
                    textColor = playlistOnColor,
                    onClick = onPlaylistClick ?: { showPlaylistDialog = true }
                )
                PillButton(
                    modifier = Modifier.weight(1f),
                    text = "INFO",
                    icon = Icons.Rounded.Info,
                    bgColor = infoBtnColor,
                    textColor = infoBtnOnColor,
                    onClick = onInfoClick ?: {
                        showInfoDialog = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val result = ProviderManager.callApi("song/detail", mapOf("ids" to song.id))
                                withContext(Dispatchers.Main) {
                                    infoText = org.json.JSONObject(result).toString(4)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    infoText = "Failed to load info: ${e.message}"
                                }
                            }
                        }
                        Unit
                    }
                )
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

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Song Info") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        Text(text = infoText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PillButton(
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
            .height(64.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text, tint = textColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
