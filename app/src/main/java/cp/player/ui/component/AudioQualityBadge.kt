package cp.player.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioQualityBadge(sampleRate: Int, bitrate: Int, modifier: Modifier = Modifier) {
    if (sampleRate > 0 || bitrate > 0) {
        val isDsd = sampleRate >= 2822400
        val text = if (isDsd) {
            "DSD ${sampleRate / 1000}kHz"
        } else if (sampleRate > 0) {
            "${sampleRate / 1000}kHz" + if (bitrate > 0) " | ${bitrate / 1000}kbps" else ""
        } else {
            "${bitrate / 1000}kbps"
        }

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(4.dp),
            modifier = modifier.padding(top = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
