package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.util.resized

/**
 * 共用的歌曲头部组件：封面 + 标题 + 歌手。
 *
 * @param song 歌曲信息
 * @param modifier Modifier
 * @param trailingContent 可选的尾部内容（如分享按钮），在标题列之后渲染
 */
@Composable
fun SongHeader(
    song: Song,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    model = song.albumArtUrl.resized(200),
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

        trailingContent?.invoke(this)
    }
}
