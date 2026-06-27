package cp.player.ui.component

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
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
import cp.player.util.resized
import cp.player.viewmodel.UserViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSongId: String?,
    onPlayAt: (Int) -> Unit,
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
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, song ->
                    val isDragged = draggedIndex == index
                    val isDropTarget = dropIndex == index && draggedIndex != index

                    Column {
                        // Drop target indicator line
                        if (isDropTarget) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .padding(horizontal = 24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        QueueItem(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isDragged = isDragged,
                            isDropTarget = isDropTarget,
                            index = index,
                            lastIndex = items.lastIndex,
                            onPlay = { onPlayAt(index) },
                            onMoreClick = { selectedSongForOptions = song },
                            onDragStart = { draggedIndex = index },
                            onDragTo = { newDropIndex -> dropIndex = newDropIndex },
                            onDragEnd = {
                                val from = draggedIndex
                                val to = dropIndex
                                if (from != null && to != null && from != to) {
                                    onMove(from, to)
                                }
                                draggedIndex = null
                                dropIndex = null
                            },
                            modifier = Modifier.zIndex(if (isDragged) 1f else 0f)
                        )
                    }
                }
            }

            // Bottom Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        
                        IconButton(onClick = { showSavePlaylistDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Save as Playlist")
                        }
                        
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
        cp.player.ui.component.AddToPlaylistBottomSheet(
            playlists = userViewModel.userPlaylists,
            onDismissRequest = { showSavePlaylistDialog = false },
            onPlaylistSelected = { playlist ->
                userViewModel.addSongsToPlaylist(playlist.id, items.map { it.id }, null)
                android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                showSavePlaylistDialog = false
            }
        )
    }
}

@Composable
fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    isDragged: Boolean,
    isDropTarget: Boolean,
    index: Int,
    lastIndex: Int,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragTo: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drag animation: elevation + slight scale
    val elevation by animateDpAsState(
        targetValue = if (isDragged) 8.dp else 0.dp,
        animationSpec = spring()
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.03f else 1f,
        animationSpec = spring()
    )

    val backgroundColor = when {
        isDragged -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        isCurrent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
            },
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        tonalElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle — 拖拽手势仅在此区域生效
            var totalDragY by remember { mutableStateOf(0f) }
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .pointerInput(index, lastIndex) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                totalDragY = 0f
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragY += dragAmount.y
                                val itemHeight = 72.dp.toPx()
                                val newDropIndex = (index + (totalDragY / itemHeight).roundToInt())
                                    .coerceIn(0, lastIndex)
                                onDragTo(newDropIndex)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 主内容区域 — 点击播放
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlay() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                AsyncImage(
                    model = song.albumArtUrl.resized(200),
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
}
