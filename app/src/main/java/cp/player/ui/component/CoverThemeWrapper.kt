package cp.player.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cp.player.ui.theme.createCustomColorScheme

/**
 * 封面颜色主题包装器。
 *
 * 当 [useCoverColor] 为 true 且 [coverColor] 不为 null 时，
 * 根据封面颜色创建自定义 ColorScheme 应用于子组件。
 * 否则使用默认的 MaterialTheme ColorScheme。
 *
 * 消除了 PlayerScreen 和 BottomPlaybackBar 中重复的封面颜色主题逻辑。
 */
@Composable
fun CoverThemeWrapper(
    useCoverColor: Boolean,
    coverColor: Int?,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme(), pureBlack)
    } else {
        MaterialTheme.colorScheme
    }
    MaterialTheme(colorScheme = colorScheme) {
        content()
    }
}
