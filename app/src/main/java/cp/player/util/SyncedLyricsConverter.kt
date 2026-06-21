package cp.player.util

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import cp.player.model.LyricLine

/**
 * 将 CPPlayer 的 [LyricLine] 列表转换为 accompanist-lyrics-core 的 [SyncedLyrics]。
 *
 * 转换规则：
 * - 有 words 的行 → [KaraokeLine.MainKaraokeLine]（逐字模式）
 * - 无 words 的行 → [SyncedLine]（普通同步行）
 * - translation / romanization 保留到对应字段
 */
object SyncedLyricsConverter {

    fun convert(lyrics: List<LyricLine>): SyncedLyrics {
        val lines: List<ISyncedLine> = lyrics.map { line ->
            if (line.words != null && line.words.isNotEmpty()) {
                toKaraokeLine(line)
            } else {
                toSyncedLine(line)
            }
        }
        return SyncedLyrics(lines = lines)
    }

    private fun toKaraokeLine(line: LyricLine): KaraokeLine.MainKaraokeLine {
        val syllables = line.words!!.map { word ->
            KaraokeSyllable(
                content = word.text,
                start = word.beginTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                end = word.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                phonetic = ""
            )
        }
        return KaraokeLine.MainKaraokeLine(
            syllables = syllables,
            translation = line.translation ?: "",
            alignment = KaraokeAlignment.Unspecified,
            start = line.time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            end = (line.endTime ?: (line.time + 5000)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            phonetic = line.romanization ?: "",
            accompanimentLines = emptyList()
        )
    }

    private fun toSyncedLine(line: LyricLine): SyncedLine {
        return SyncedLine(
            content = line.text,
            translation = line.translation ?: "",
            start = line.time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            end = (line.endTime ?: (line.time + 5000)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        )
    }
}
