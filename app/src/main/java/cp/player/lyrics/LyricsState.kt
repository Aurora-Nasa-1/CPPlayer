package cp.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import cp.player.model.LyricsInfo
import io.github.proify.lyricon.lyric.model.RichLyricLine

/**
 * 歌词状态密封类。
 *
 * 作为 [LyricsManager] 的输出，UI 层和 Service 层统一消费此状态。
 */
sealed class LyricsState {
    /** 初始状态，尚未请求歌词。 */
    object Idle : LyricsState()

    /** 正在获取歌词。 */
    data class Loading(val songId: String) : LyricsState()

    /** 歌词获取成功。 */
    data class Success(
        val songId: String,
        /** 统一的 SyncedLyrics 格式，UI 渲染使用。 */
        val syncedLyrics: SyncedLyrics?,
        /** 歌词元信息（来源、格式、能力）。 */
        val lyricsInfo: LyricsInfo,
        /** RichLyricLine 列表，供 Lyricon 外部歌词同步使用。 */
        val richLyricLines: List<RichLyricLine>
    ) : LyricsState()

    /** 歌词获取失败。 */
    data class Error(val songId: String, val message: String) : LyricsState()
}
