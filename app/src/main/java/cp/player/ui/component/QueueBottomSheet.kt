package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import cp.player.model.Song
import cp.player.ui.component.ExpressiveShapes

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

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Next In Queue", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClear) {
                    Text("Clear Queue")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(items) { index, song ->
                    QueueItem(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onRemove = { onRemove(index) },
                        onMoveUp = { if (index > 0) onMove(index, index - 1) },
                        onMoveDown = { if (index < items.size - 1) onMove(index, index + 1) },
                        shape = ExpressiveShapes.calculateShape(index, items.size)
                    )
                }
            }
        }
    }
}

@Composable
fun QueueItem(
    song: Song,
    isCurrent: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
) {
    ListItem(
        modifier = modifier.clip(shape),
        headlineContent = {
            Text(
                song.name,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        },
        supportingContent = { Text(song.artist) },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove")
                }
            }
        },
        leadingContent = {
            Icon(Icons.Default.DragHandle, contentDescription = "Drag")
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
            else 
                MaterialTheme.colorScheme.surface
        )
    )
}
