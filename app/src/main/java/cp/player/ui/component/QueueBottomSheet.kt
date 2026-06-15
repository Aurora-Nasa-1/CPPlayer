package cp.player.ui.component

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Song
import cp.player.util.ImageUtils
import cp.player.viewmodel.UserViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSongId: String?,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    var items by remember(queue) { mutableStateOf(queue) }
    
    val coroutineScope = rememberCoroutineScope()
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    
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

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    
    // Selected song for options
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }

    // Update items when queue changes
    LaunchedEffect(queue) {
        items = queue
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxHeight(0.9f),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Next up", 
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${items.size} tracks lined up.", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // All Songs Button (placeholder)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with music note icon
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Songs", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp) // Extra Padding for bottom bar + nav bars
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, song ->
                        val isDragged = draggedIndex == index
                        val isDropTarget = dropIndex == index && draggedIndex != index
                        
                        QueueItem(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isDragged = isDragged,
                            onMoreClick = { selectedSongForOptions = song },
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggedIndex = index },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val itemHeight = 72.dp.toPx() // Approximate item height including padding
                                            val totalOffset = dragAmount.y
                                            
                                            val newDropIndex = (index + (totalOffset / itemHeight).roundToInt())
                                                .coerceIn(0, items.lastIndex)
                                                
                                            if (newDropIndex != dropIndex) {
                                                dropIndex = newDropIndex
                                            }
                                        },
                                        onDragEnd = {
                                            val from = draggedIndex
                                            val to = dropIndex
                                            if (from != null && to != null && from != to) {
                                                onMove(from, to)
                                            }
                                            draggedIndex = null
                                            dropIndex = null
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dropIndex = null
                                        }
                                    )
                                }
                        )
                    }
                }
            }
            
            // Bottom Action Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface) // Solid background to hide list behind
                    .navigationBarsPadding() // Add padding for navigation bar
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Locate current song
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val currentIndex = items.indexOfFirst { it.id == currentSongId }
                                if (currentIndex != -1) {
                                    listState.animateScrollToItem(currentIndex)
                                }
                            }
                        }) {
                            Icon(Icons.Default.LocationSearching, contentDescription = "Locate Current")
                        }
                        
                        // Save to Playlist
                        IconButton(onClick = { showSavePlaylistDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Save as Playlist")
                        }
                        
                        // Clear Queue
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Queue")
                        }
                    }
                }
            }
        }
    }
    
    // Song Options Bottom Sheet
    selectedSongForOptions?.let { song ->
        cp.player.ui.component.SongOptionsBottomSheet(
            song = song,
            isFavorite = false, // Add placeholder for isFavorite if not available
            onDismissRequest = { selectedSongForOptions = null }
        )
    }

    if (showSavePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlaylistDialog = false },
            title = { Text("Add queue to Playlist") },
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
                                    userViewModel.addSongsToPlaylist(playlist.id, items.map { it.id }, null)
                                    android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    showSavePlaylistDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    isDragged: Boolean,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        isDragged -> MaterialTheme.colorScheme.surfaceVariant
        isCurrent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = "Drag",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Album art
        AsyncImage(
            model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 200),
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            error = painterResource(id = R.drawable.ic_launcher_foreground)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        // More options
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrent) 1f else 0.5f))
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        
        // Current indicator line
        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}
