package cp.player.util

import cp.player.model.LyricLine

/**
 * YRC 逐字歌词 → TTML 格式转换器。
 *
 * 将 CPPlayer 的 [LyricLine]（含 words）转换为 TTML XML 字符串，
 * 再由 accompanist-lyrics-core 的 AutoParser 解析为 [SyncedLyrics]。
 *
 * 这样可以确保 YRC 歌词和 AMLL TTML 歌词使用相同的解析路径，
 * 避免手动构造 KaraokeLine/Syllable 时的兼容性问题。
 */
object YrcToTtmlConverter {

    /**
     * 将 YRC 格式的 [LyricLine] 列表转换为 TTML XML 字符串。
     *
     * @param lines YRC 歌词行（必须包含 words 字段）
     * @return TTML XML 字符串，如果输入为空则返回空字符串
     */
    fun convert(lines: List<LyricLine>): String {
        if (lines.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word">""")
        sb.appendLine("""<head><metadata><ttm:agent type="person" xml:id="v1" /></metadata></head>""")

        // 计算总时长
        val maxEnd = lines.maxOfOrNull { it.endTime ?: (it.time + 5000) } ?: 0L
        sb.appendLine("""<body dur="${formatTtmlTime(maxEnd)}">""")
        sb.appendLine("""<div begin="${formatTtmlTime(lines.first().time)}" end="${formatTtmlTime(maxEnd)}">""")

        lines.forEachIndexed { index, line ->
            val lineBegin = formatTtmlTime(line.time)
            val lineEnd = formatTtmlTime(line.endTime ?: (line.time + 5000))

            if (line.words != null && line.words.isNotEmpty()) {
                // 逐字行：每个 word 是一个 <span>
                sb.append("""<p begin="$lineBegin" end="$lineEnd" ttm:agent="v1" itunes:key="L${index + 1}">""")
                line.words.forEach { word ->
                    val wordBegin = formatTtmlTime(word.beginTime)
                    val wordEnd = formatTtmlTime(word.endTime)
                    val escapedText = escapeXml(word.text)
                    sb.append("""<span begin="$wordBegin" end="$wordEnd">$escapedText</span>""")
                }
                appendTranslationAndRomanization(sb, line, lineBegin, lineEnd)
                sb.appendLine("</p>")
            } else {
                // 普通行
                val escapedText = escapeXml(line.text)
                sb.append("""<p begin="$lineBegin" end="$lineEnd" ttm:agent="v1" itunes:key="L${index + 1}">""")
                sb.append("""<span begin="$lineBegin" end="$lineEnd">$escapedText</span>""")
                appendTranslationAndRomanization(sb, line, lineBegin, lineEnd)
                sb.appendLine("</p>")
            }
        }

        sb.appendLine("</div>")
        sb.appendLine("</body>")
        sb.appendLine("</tt>")

        return sb.toString()
    }

    /**
     * 在 `<p>` 内追加翻译和音译 span（如果存在）。
     * TTMLParser 通过 `ttm:role="x-translation"` 和 `ttm:role="x-roman"` 识别，
     * 并在 extractAllText 时自动跳过这些 span，不会影响主歌词文本。
     */
    private fun appendTranslationAndRomanization(
        sb: StringBuilder,
        line: LyricLine,
        lineBegin: String,
        lineEnd: String
    ) {
        if (!line.translation.isNullOrEmpty()) {
            val escaped = escapeXml(line.translation)
            sb.append("""<span begin="$lineBegin" end="$lineEnd" ttm:role="x-translation">$escaped</span>""")
        }
        if (!line.romanization.isNullOrEmpty()) {
            val escaped = escapeXml(line.romanization)
            sb.append("""<span begin="$lineBegin" end="$lineEnd" ttm:role="x-roman">$escaped</span>""")
        }
    }

    /**
     * 将毫秒转换为 TTML 时间格式 `MM:SS.mmm`。
     */
    private fun formatTtmlTime(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0)
        val minutes = totalMs / 60000
        val seconds = (totalMs % 60000) / 1000
        val millis = totalMs % 1000
        return "%02d:%02d.%03d".format(minutes, seconds, millis)
    }

    /**
     * XML 特殊字符转义。
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
