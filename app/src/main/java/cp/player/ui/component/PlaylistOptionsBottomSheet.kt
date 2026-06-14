package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cp.player.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsBottomSheet(
    playlist: Playlist,
    onDismissRequest: () -> Unit,
    onPlayClick: () -> Unit = {},
    onAddToQueueClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    onSortByNameClick: (() -> Unit)? = null,
    onSortByArtistClick: (() -> Unit)? = null
) {
    val sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    
    val playColor = MaterialTheme.colorScheme.primaryContainer
    val playOnColor = MaterialTheme.colorScheme.onPrimaryContainer
    
    val queueColor = MaterialTheme.colorScheme.tertiaryContainer
    val queueOnColor = MaterialTheme.colorScheme.onTertiaryContainer
    
    val circleBtnColor = MaterialTheme.colorScheme.secondaryContainer
    val circleBtnOnColor = MaterialTheme.colorScheme.onSecondaryContainer

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
                    if (playlist.coverImgUrl != null) {
                        AsyncImage(
                            model = playlist.coverImgUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.padding(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Playlist",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 1: Play, Share
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
                        .clickable { 
                            onPlayClick()
                            onDismissRequest()
                        }
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

                // Share
                Surface(
                    shape = CircleShape,
                    color = circleBtnColor,
                    modifier = Modifier
                        .size(88.dp)
                        .clickable {
                            onShareClick()
                            onDismissRequest()
                        }
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
                
                // Delete (optional)
                if (onDeleteClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable {
                                onDeleteClick()
                                onDismissRequest()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Row 2: Add to queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = queueColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            onAddToQueueClick()
                            onDismissRequest()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to queue", tint = queueOnColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to queue", color = queueOnColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            // Optional Sort Row
            if (onSortByNameClick != null || onSortByArtistClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onSortByNameClick != null) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable {
                                    onSortByNameClick()
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.SortByAlpha, contentDescription = "Sort by name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("By Name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (onSortByArtistClick != null) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable {
                                    onSortByArtistClick()
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = "Sort by artist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("By Artist", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
