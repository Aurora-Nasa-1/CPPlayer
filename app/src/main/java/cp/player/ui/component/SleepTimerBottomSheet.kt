package cp.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    remainingTime: Long,
    onSetTimer: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Off" to 0,
        "10 Minutes" to 10,
        "20 Minutes" to 20,
        "30 Minutes" to 30,
        "45 Minutes" to 45,
        "60 Minutes" to 60,
        "90 Minutes" to 90
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            if (remainingTime > 0) {
                val minutes = (remainingTime / 1000) / 60
                val seconds = (remainingTime / 1000) % 60
                Text(
                    text = "Remaining: ${String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%02d:%02d", minutes, seconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            options.forEach { (label, minutes) ->
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSetTimer(minutes)
                            onDismiss()
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}
