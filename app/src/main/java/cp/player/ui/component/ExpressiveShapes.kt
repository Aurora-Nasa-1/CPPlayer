package cp.player.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object ExpressiveShapes {
    val OuterCornerSize = 24.dp
    val InnerCornerSize = 4.dp

    @Composable
    fun calculateShape(index: Int, totalCount: Int): Shape {
        return when {
            totalCount <= 1 -> RoundedCornerShape(OuterCornerSize)
            index == 0 -> RoundedCornerShape(
                topStart = OuterCornerSize,
                topEnd = OuterCornerSize,
                bottomStart = InnerCornerSize,
                bottomEnd = InnerCornerSize
            )
            index == totalCount - 1 -> RoundedCornerShape(
                topStart = InnerCornerSize,
                topEnd = InnerCornerSize,
                bottomStart = OuterCornerSize,
                bottomEnd = OuterCornerSize
            )
            else -> RoundedCornerShape(InnerCornerSize)
        }
    }
}
