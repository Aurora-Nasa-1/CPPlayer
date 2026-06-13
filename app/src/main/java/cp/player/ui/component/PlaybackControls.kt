package cp.player.ui.component
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    sideButtonModifier: Modifier = Modifier,
    centerButtonModifier: Modifier = Modifier,
    sideIconSize: Dp = 28.dp,
    centerIconSize: Dp = 40.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onSkipPrevious,
            shape = CircleShape,
            modifier = sideButtonModifier,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(sideIconSize))
            }
        }

        Surface(
            onClick = onPlayPause,
            shape = CircleShape,
            modifier = centerButtonModifier,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(centerIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Surface(
            onClick = onSkipNext,
            shape = CircleShape,
            modifier = sideButtonModifier,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(sideIconSize))
            }
        }
    }
}
