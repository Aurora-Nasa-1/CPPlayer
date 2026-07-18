package cp.player.service

import com.google.gson.JsonObject
import cp.player.model.LyricLine
import cp.player.util.LyricUtils
import cp.player.util.obj
import cp.player.util.str

/**
 * 统一的歌词获取和解析服务。
 *
 * 消除了 MusicService.updateLyriconSong() 和 PlaybackRepository.fetchLyrics()
 * 之间 95% 的重复歌词解析+翻译合并逻辑。
 */
object LyricService {

    /**
     * 从已获取的 JSON 响应中解析歌词。
     * 适用于 MusicService 中已有 JSON 响应的场景。
     *
     * @param body 已获取的 lyric/new API 响应
     * @param duration 时长(ms)，用于 LRC 回退解析
     * @return 已合并翻译的歌词行列表
     */
    fun parseFromJson(body: JsonObject, duration: Long): List<LyricLine> {
        val lrc = body.get("lrc")?.obj?.get("lyric")?.str ?: ""
        val yrc = body.get("yrc")?.obj?.get("lyric")?.str ?: ""
        val tlyric = body.get("tlyric")?.obj?.get("lyric")?.str ?: ""
        return parseLyrics(lrc, yrc, tlyric, duration)
    }

    /**
     * 核心解析逻辑：解析 LRC/YRC 歌词并合并翻译。
     *
     * @param lrc LRC 格式歌词文本
     * @param yrc YRC 格式歌词文本（逐字歌词，优先使用）
     * @param tlyric 翻译歌词文本
     * @param duration 时长(ms)，用于 LRC 回退解析
     * @return 已合并翻译的歌词行列表
     */
    fun parseLyrics(lrc: String, yrc: String, tlyric: String, duration: Long): List<LyricLine> {
        val yrcLines = if (yrc.isNotEmpty()) LyricUtils.parseYrc(yrc) else emptyList()
        val lines = if (yrcLines.isNotEmpty()) yrcLines else LyricUtils.parseLrc(lrc, duration)
        return if (tlyric.isNotEmpty()) {
            mergeTranslation(lines, tlyric)
        } else {
            lines
        }
    }

    /**
     * 将翻译歌词合并到主歌词行中。
     */
    private fun mergeTranslation(lines: List<LyricLine>, tlyric: String): List<LyricLine> {
        val tlines = LyricUtils.parseLrc(tlyric).associateBy { it.time }
        return lines.map { line ->
            val trans = tlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
            if (trans != null) line.copy(translation = trans.value.text) else line
        }
    }
}
