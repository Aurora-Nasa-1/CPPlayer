package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一样式的底部弹出面板。
 *
 * @param bottomPadding 底部内边距，用于在手势导航栏区域留出安全距离，
 *   默认 32dp。调用方若已自行设置底部内边距（如 SongOptionsBottomSheet），
 *   可传 0.dp 以避免重复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledModalBottomSheet(
    onDismissRequest: () -> Unit,
    bottomPadding: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant,
                width = 48.dp,
                height = 4.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = bottomPadding)
        ) {
            content()
        }
    }
}
