package cp.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PillButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = 64.dp
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = if (enabled) bgColor else bgColor.copy(alpha = 0.5f),
        modifier = modifier
            .height(height)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text.ifEmpty { null }, tint = if (enabled) textColor else textColor.copy(alpha = 0.5f))
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = if (enabled) textColor else textColor.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
