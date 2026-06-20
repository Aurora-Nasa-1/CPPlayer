package cp.player.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import cp.player.model.DownloadStatus
import cp.player.model.DownloadTask

@Composable
fun DownloadIndicator(
    tasks: Map<String, DownloadTask>
) {
    val activeTasks = tasks.values.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }

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
        Icon(
            Icons.Default.Downloading,
            contentDescription = "Downloading",
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.primary
        )

        Badge(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Text(activeTasks.size.toString())
        }
    }
}
