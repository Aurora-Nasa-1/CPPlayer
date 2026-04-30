package cp.player.util

import cp.player.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import java.util.regex.Pattern

object LyricUtils {
    fun parseLrc(lrc: String, duration: Long = 0L): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrc.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLong() ?: 0L
                val sec = matcher.group(2)?.toLong() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val time = min * 60000 + sec * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(time = time, text = text))
                }
            }
        }
        val sortedLines = lines.sortedBy { it.time }.toMutableList()
        for (i in 0 until sortedLines.size - 1) {
            sortedLines[i] = sortedLines[i].copy(endTime = sortedLines[i+1].time)
        }
        if (sortedLines.isNotEmpty()) {
            val lastEnd = if (duration > sortedLines.last().time) duration else sortedLines.last().time + 5000
            sortedLines[sortedLines.size - 1] = sortedLines[sortedLines.size - 1].copy(endTime = lastEnd)
        }
        return sortedLines
    }

    fun parseYrc(yrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // YRC format: [time,duration]text(time,duration,type)word...
        val linePattern = Pattern.compile("\\[(\\d+),(\\d+)](.*)")
        val wordPattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)([^\\(]*)")

        yrc.lines().forEach { line ->
            val lineMatcher = linePattern.matcher(line)
            if (lineMatcher.find()) {
                val lineBegin = lineMatcher.group(1)?.toLong() ?: 0L
                val lineDur = lineMatcher.group(2)?.toLong() ?: 0L
                val content = lineMatcher.group(3) ?: ""

                val words = mutableListOf<LyricLine.Word>()
                val wordMatcher = wordPattern.matcher(content)
                var fullText = ""

                while (wordMatcher.find()) {
                    val wBeginOffset = wordMatcher.group(1)?.toLong() ?: 0L
                    val wDur = wordMatcher.group(2)?.toLong() ?: 0L
                    val wText = wordMatcher.group(4) ?: ""

                    words.add(LyricLine.Word(
                        text = wText,
                        beginTime = lineBegin + wBeginOffset,
                        endTime = lineBegin + wBeginOffset + wDur
                    ))
                    fullText += wText
                }

                if (words.isNotEmpty()) {
                    lines.add(LyricLine(
                        time = lineBegin,
                        endTime = lineBegin + lineDur,
                        text = fullText,
                        words = words
                    ))
                } else if (content.isNotEmpty()) {
                    lines.add(LyricLine(
                        time = lineBegin,
                        endTime = lineBegin + lineDur,
                        text = content
                    ))
                }
            }
        }
        return lines.sortedBy { it.time }
    }

    fun toRichLyricLines(lyrics: List<LyricLine>): List<RichLyricLine> {
        return lyrics.map { line ->
            val begin = line.time
            val end = line.endTime ?: (line.time + 5000)
            RichLyricLine(
                begin = begin,
                end = end,
                duration = end - begin,
                text = line.text,
                translation = line.translation,
                roma = line.romanization,
                secondary = line.secondary,
                words = line.words?.map { word ->
                    LyricWord(
                        text = word.text,
                        begin = word.beginTime,
                        end = word.endTime
                    )
                }
            )
        }
    }
}
