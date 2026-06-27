package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Playlist
import cp.player.util.resized

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    index: Int = 0,
    total: Int = 1,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        cp.player.ui.component.UnifiedListItem(
            onClick = onClick,
            shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, total),
            headlineContent = { 
                Text(
                    playlist.name, 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                ) 
            },
            supportingContent = { 
                Text(
                    playlist.description ?: "${playlist.trackCount} songs", 
                    maxLines = if (playlist.description != null) Int.MAX_VALUE else 1,
                    overflow = if (playlist.description != null) TextOverflow.Visible else TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            overlineContent = if (playlist.creatorName != null) {
                { Text(playlist.creatorName, style = MaterialTheme.typography.labelSmall) }
            } else null,
            leadingContent = {
                Surface(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    if (playlist.coverImgUrl != null) {
                        AsyncImage(
                            model = playlist.coverImgUrl.resized(180),
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = containerColor)
        )
        if (bottomContent != null) {
            Box(modifier = Modifier.padding(start = 88.dp, end = 16.dp, bottom = 12.dp)) {
                bottomContent()
            }
        }
    }
}
