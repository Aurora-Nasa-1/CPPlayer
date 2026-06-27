package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.util.resized

/**
 * 共享的专辑封面渲染组件。
 *
 * 支持两种尺寸模式：
 * - 固定尺寸：传入具体的 [size] 值（如 56.dp）
 * - 自适应尺寸：传入 [Dp.Unspecified]，通过外部 [modifier] 控制尺寸（如 fillMaxWidth + aspectRatio）
 */
@Composable
fun AlbumArt(
    albumArtUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = MaterialTheme.shapes.large,
    placeholderIcon: ImageVector = Icons.Default.MusicNote,
    iconSize: Dp = 24.dp,
    contentScale: ContentScale = ContentScale.Crop,
    resizeUrl: Int? = null
) {
    Surface(
        modifier = modifier
            .then(if (size == Dp.Unspecified) Modifier else Modifier.size(size))
            .clip(shape),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (albumArtUrl != null) {
            // file:// URI 需转为 File 对象，Coil 的 OkHttpFetcher 无法直接加载 file:// 字符串
            val imageModel: Any = if (albumArtUrl.startsWith("file://")) {
                java.io.File(android.net.Uri.parse(albumArtUrl).path!!)
            } else {
                resizeUrl?.let { albumArtUrl.resized(it) } ?: albumArtUrl
            }
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
