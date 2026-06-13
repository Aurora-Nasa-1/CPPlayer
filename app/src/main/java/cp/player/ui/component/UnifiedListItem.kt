package cp.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnifiedListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(0, 1),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    if (onClick != null || onLongClick != null) {
        SegmentedListItem(
            onClick = onClick ?: {},
            onLongClick = onLongClick,
            shapes = shapes,
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            colors = ListItemDefaults.segmentedColors(
                containerColor = colors.containerColor
            ),
            content = headlineContent
        )
    } else {
        // Just pass empty lambda if it's purely display
        SegmentedListItem(
            onClick = {},
            shapes = shapes,
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            colors = ListItemDefaults.segmentedColors(
                containerColor = colors.containerColor
            ),
            content = headlineContent
        )
    }
}
