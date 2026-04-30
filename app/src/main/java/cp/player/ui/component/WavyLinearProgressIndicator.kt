package cp.player.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}
