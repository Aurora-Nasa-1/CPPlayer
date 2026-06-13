package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.util.ImageUtils

@Composable
fun SongItem(
    song: Song,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    index: Int = 0,
    total: Int = 1,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    containerColor: Color = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
) {
    cp.player.ui.component.UnifiedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(index, total),
        headlineContent = {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDownloaded) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        },
        leadingContent = leadingContent ?: {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (song.albumArtUrl != null) {
                    AsyncImage(
                        model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 180),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(16.dp))
                }
            }
        },
        trailingContent = trailingContent ?: if (onLikeClick != null) {
            {
                IconButton(onClick = onLikeClick) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = modifier
            .fillMaxWidth()
            
            .then(if (onClick != null) Modifier else Modifier)
    )
}
