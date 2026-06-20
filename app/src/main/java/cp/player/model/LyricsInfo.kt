package cp.player.model

import androidx.compose.runtime.Immutable

/**
 * 歌词元信息，用于在播放器界面展示歌词来源、格式和能力。
 */
@Immutable
data class LyricsInfo(
    /** 来源：Provider API / AMLL TTML */
    val source: String = "Unknown",
    /** 格式：TTML / YRC / LRC */
    val format: String = "Unknown",
    /** 原始数据是否支持逐字歌词 */
    val hasWordLevel: Boolean = false,
    /** 是否包含翻译 */
    val hasTranslation: Boolean = false,
    /** 是否包含音译/拼音 */
    val hasPhonetic: Boolean = false
)
