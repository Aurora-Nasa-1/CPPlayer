package cp.player.util

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 格式化工具集合。
 */


    private var cachedLocale: Locale? = null
    private var cachedZeroDigit: Char = '0'

    /**
     * 将毫秒格式化为 "m:ss" 的时间字符串。
     * 针对使用 '0' 的常见 Locale 进行了快速路径优化。
     *
     * @param locale 用于格式化的 Locale，默认为系统默认
     * @return 格式化的时间字符串
     */
    fun Long.formatAsTime(locale: Locale = Locale.getDefault()): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        val zeroDigit = if (locale == cachedLocale) {
            cachedZeroDigit
        } else {
            val digit = DecimalFormatSymbols.getInstance(locale).zeroDigit
            cachedLocale = locale
            cachedZeroDigit = digit
            digit
        }

        if (zeroDigit == '0') {
            val secondsStr = if (seconds < 10) "0$seconds" else seconds.toString()
            return "$minutes:$secondsStr"
        } else {
            return String.format(locale, "%d:%02d", minutes, seconds)
        }
    }
