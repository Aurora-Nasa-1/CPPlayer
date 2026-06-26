package cp.player.util

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
    fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (seconds < 10) "$minutes:0$seconds" else "$minutes:$seconds"
    }
}
