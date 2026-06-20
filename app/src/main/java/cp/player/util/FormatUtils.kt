package cp.player.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 格式化工具集合。
 */
object FormatUtils {

    /**
     * 将毫秒格式化为 "m:ss" 的时间字符串。
     *
     * @param millis 毫秒数
     * @return 格式化的时间字符串
     */
    @Composable
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(LocalConfiguration.current.locales[0], "%d:%02d", minutes, seconds)
    }
}
