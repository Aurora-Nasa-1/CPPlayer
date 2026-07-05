package cp.player.util

import java.util.Locale

/**
 * 格式化工具集合。
 */


    /**
     * 将毫秒格式化为 "m:ss" 的时间字符串。
     *
     * @return 格式化的时间字符串
     */
    fun Long.formatAsTime(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val secondsStr = if (seconds < 10) "0$seconds" else seconds.toString()
        return "$minutes:$secondsStr"
    }
