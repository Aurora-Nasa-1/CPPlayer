package cp.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.model.Playlist
import cp.player.util.resized
import cp.player.viewmodel.ToplistEntry

/**
 * 快速访问卡片组件。
 *
 * 用于首页快速访问区域，显示一个完整的卡片，包含：
 * - 顶部：图标 + 标题 + 箭头按钮
 * - 底部：预览内容（可点击）
 *
 * @param title 卡片标题
 * @param icon 图标组件
 * @param previewContent 预览内容组件
 * @param onArrowClick 箭头按钮点击回调
 * @param modifier Modifier
 */
@Composable
fun QuickAccessCard(
    title: String,
    icon: @Composable () -> Unit,
    previewContent: @Composable () -> Unit,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = if (isSystemInDarkTheme())
        MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor
    ) {
        Column {
            // 顶部：图标 + 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 图标（底部指示器选中样式）
                icon()
                // 标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 预览内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                previewContent()
            }
        }
    }
}

/**
 * 歌曲预览列表组件。
 *
 * 显示歌曲列表，每首歌曲可点击。
 *
 * @param songs 歌曲列表
 * @param onSongClick 歌曲点击回调
 * @param modifier Modifier
 */
@Composable
fun SongPreviewList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        songs.take(3).forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 序号
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 歌曲封面
                AsyncImage(
                    model = (song.albumArtUrl ?: "").resized(150),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 歌曲信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 箭头按钮（只在最后一首歌曲右边显示）
                if (index == songs.take(3).size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onArrowClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "查看全部",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 歌单预览组件。
 *
 * 显示歌单封面和基本信息。
 *
 * @param playlist 歌单
 * @param onClick 点击回调
 * @param modifier Modifier
 */
@Composable
fun PlaylistPreview(
    playlist: Playlist,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌单封面
        AsyncImage(
            model = (playlist.coverImgUrl ?: "").resized(300),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        // 歌单信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${playlist.trackCount} 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 箭头按钮
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            onClick = onArrowClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "查看全部",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 发现页预览组件。
 *
 * 显示热门榜单或推荐歌单。
 *
 * @param toplists 热门榜单列表
 * @param onToplistClick 榜单点击回调
 * @param modifier Modifier
 */
@Composable
fun DiscoveryPreview(
    toplists: List<ToplistEntry>,
    onToplistClick: (Long) -> Unit,
    onArrowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        toplists.take(3).forEachIndexed { index, toplist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToplistClick(toplist.id) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 榜单封面
                AsyncImage(
                    model = (toplist.coverImgUrl ?: "").resized(300),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 榜单信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toplist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = toplist.updateFrequency ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 箭头按钮（只在最后一个榜单右边显示）
                if (index == toplists.take(3).size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onArrowClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "查看全部",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
