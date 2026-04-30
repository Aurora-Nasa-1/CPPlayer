package cp.player.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> VerticalGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.(T) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        for (i in items.indices step columns) {
            Row(horizontalArrangement = horizontalArrangement) {
                for (j in 0 until columns) {
                    val idx = i + j
                    if (idx < items.size) {
                        content(items[idx])
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun <T> IndexedVerticalGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.(Int, T, Int, Int) -> Unit // index, item, rowIndex, totalRows
) {
    val totalRows = (items.size + columns - 1) / columns
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        for (i in items.indices step columns) {
            Row(horizontalArrangement = horizontalArrangement) {
                for (j in 0 until columns) {
                    val idx = i + j
                    if (idx < items.size) {
                        content(idx, items[idx], i / columns, totalRows)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}