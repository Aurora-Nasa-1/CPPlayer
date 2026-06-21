package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cp.player.R
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
    onUnsubscribeClick: (() -> Unit)? = null,
    isOwner: Boolean = true,
    currentSortType: String = "default",
    onSortDefaultClick: (() -> Unit)? = null,
    onSortByNameClick: (() -> Unit)? = null,
    onSortByArtistClick: (() -> Unit)? = null
) {
    val playColor = MaterialTheme.colorScheme.primaryContainer
    val playOnColor = MaterialTheme.colorScheme.onPrimaryContainer
    
    val queueColor = MaterialTheme.colorScheme.tertiaryContainer
    val queueOnColor = MaterialTheme.colorScheme.onTertiaryContainer
    
    val circleBtnColor = MaterialTheme.colorScheme.secondaryContainer
    val circleBtnOnColor = MaterialTheme.colorScheme.onSecondaryContainer

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest
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
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.padding(16.dp))
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
                        text = stringResource(R.string.playlist),
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
                        Text(stringResource(R.string.play), color = playOnColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
                            contentDescription = stringResource(R.string.share),
                            tint = circleBtnOnColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Delete / Unsubscribe (optional)
                if (!isOwner && onUnsubscribeClick != null) {
                    // 收藏的歌单 → 取消收藏
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .size(88.dp)
                            .clickable {
                                onUnsubscribeClick()
                                onDismissRequest()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkRemove,
                                contentDescription = stringResource(R.string.unsubscribe),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (isOwner && onDeleteClick != null) {
                    // 自己创建的歌单 → 删除
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
                                contentDescription = stringResource(R.string.delete),
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
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.add_to_queue), tint = queueOnColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_to_queue), color = queueOnColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            // Optional Sort Row
            if (onSortDefaultClick != null || onSortByNameClick != null || onSortByArtistClick != null) {
                // 标题
                Text(
                    text = stringResource(R.string.sort_method),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 默认排序
                    if (onSortDefaultClick != null) {
                        val isSelected = currentSortType == "default"
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable {
                                    onSortDefaultClick()
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.List,
                                    contentDescription = stringResource(R.string.default_sort),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.sort_default),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    // 按名称排序
                    if (onSortByNameClick != null) {
                        val isSelected = currentSortType == "name"
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
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
                                Icon(
                                    Icons.Rounded.SortByAlpha,
                                    contentDescription = stringResource(R.string.sort_by_name_cn),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.sort_by_name_cn),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    // 按艺术家排序
                    if (onSortByArtistClick != null) {
                        val isSelected = currentSortType == "artist"
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
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
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = stringResource(R.string.sort_by_artist_cn),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.sort_by_artist_cn),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
