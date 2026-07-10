package cp.player.util

import cp.player.model.LyricLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import java.util.regex.Pattern

object LyricUtils {
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
    private val YRC_LINE_PATTERN = Pattern.compile("\\[(\\d+),(\\d+)](.*)")
    private val YRC_WORD_REGEX = Regex("\\((\\d+),(\\d+),(\\d+)\\)")

    fun parseLrc(lrc: String, duration: Long = 0L): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        lrc.lines().forEach { line ->
            val matcher = LRC_PATTERN.matcher(line)
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

        var parsedLineCount = 0
        var totalWordCount = 0

        yrc.lines().forEach { line ->
            val lineMatcher = YRC_LINE_PATTERN.matcher(line)
            if (lineMatcher.find()) {
                val lineBegin = lineMatcher.group(1)?.toLong() ?: 0L
                val lineDur = lineMatcher.group(2)?.toLong() ?: 0L
                val content = lineMatcher.group(3) ?: ""

                // 使用 findAll 顺序扫描 word markers
                val markers = YRC_WORD_REGEX.findAll(content).toList()

                if (markers.isNotEmpty()) {
                    val words = mutableListOf<LyricLine.Word>()

                    // 自动检测时间戳类型：
                    // - firstWordTime < lineBegin → 相对时间戳 (或者 lineBegin == 0 也可以正常处理)
                    // - firstWordTime >= lineBegin → 绝对时间戳（word time 就是全局时间）
                    val firstWordTime = markers.first().groupValues[1].toLong()
                    val isAbsolute = if (lineBegin > 0) firstWordTime >= lineBegin else false
                    val baseTime = if (isAbsolute) 0L else lineBegin

                    markers.forEachIndexed { i, match ->
                        val wBegin = match.groupValues[1].toLong()
                        val wDur = match.groupValues[2].toLong()
                        // 提取 word 文本：YRC 格式中文字在 marker 之后，即当前 marker 结束到下一个 marker 开始
                        val textStart = match.range.last + 1
                        val textEnd = if (i < markers.size - 1) markers[i + 1].range.first else content.length
                        val wText = if (textStart < textEnd) content.substring(textStart, textEnd) else ""

                        // 保留空格（(0,0,0) marker 产生的空格），但跳过完全为空的条目
                        if (wText.isNotEmpty()) {
                            words.add(LyricLine.Word(
                                text = wText,
                                beginTime = baseTime + wBegin,
                                endTime = baseTime + wBegin + wDur
                            ))
                        }
                    }

                    if (words.isNotEmpty()) {
                        val fullText = words.joinToString("") { it.text }
                        lines.add(LyricLine(
                            time = lineBegin,
                            endTime = lineBegin + lineDur,
                            text = fullText,
                            words = words
                        ))
                        parsedLineCount++
                        totalWordCount += words.size
                    }
                } else if (content.isNotEmpty()) {
                    // 无 word markers 的行，作为普通歌词行
                    lines.add(LyricLine(
                        time = lineBegin,
                        endTime = lineBegin + lineDur,
                        text = content
                    ))
                    parsedLineCount++
                }
            }
        }

        cp.player.util.DebugLog.i("parseYrc: input=${yrc.lines().size} lines, parsed=$parsedLineCount lines, words=$totalWordCount")
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

    /**
     * 将 accompanist-lyrics-core 的 [SyncedLyrics] 转换为 Lyricon 的 [RichLyricLine] 列表。
     * 用于 MusicService 中 Lyricon 插件的歌词同步。
     */
    fun syncedLyricsToRichLyricLines(syncedLyrics: SyncedLyrics): List<RichLyricLine> {
        return syncedLyrics.lines.map { line ->
            val begin = line.start.toLong()
            val end = line.end.toLong()
            when (line) {
                is KaraokeLine -> {
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        duration = end - begin,
                        text = line.syllables.joinToString("") { it.content },
                        translation = line.translation?.ifEmpty { null },
                        roma = line.phonetic?.ifEmpty { null },
                        words = line.syllables.map { syl ->
                            LyricWord(
                                text = syl.content,
                                begin = syl.start.toLong(),
                                end = syl.end.toLong()
                            )
                        }
                    )
                }
                is SyncedLine -> {
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        duration = end - begin,
                        text = line.content,
                        translation = line.translation?.ifEmpty { null }
                    )
                }
                else -> {
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        duration = end - begin,
                        text = ""
                    )
                }
            }
        }
    }
}
