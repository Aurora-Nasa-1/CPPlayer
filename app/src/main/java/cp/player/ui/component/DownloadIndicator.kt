package cp.player.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import cp.player.model.DownloadStatus
import cp.player.model.DownloadTask

@Composable
fun DownloadIndicator(
    tasks: Map<String, DownloadTask>,
    onClick: () -> Unit
) {
    val activeTasks = tasks.values.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
    val isDownloading = activeTasks.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "download")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            if (isDownloading) {
                Icon(
                    Icons.Default.Downloading,
                    contentDescription = "Downloading",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.Default.DownloadDone, contentDescription = "Downloads")
            }
        }

        if (isDownloading) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Text(activeTasks.size.toString())
            }
        }
    }
}
