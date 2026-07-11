package cp.player.model

data class LocalSongMetadata(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val album: String,
    /** 音频 content:// URI，用于播放（不是封面 URL） */
    val albumArtUrl: String? = null,
    /** 音频文件的实际路径，用于提取封面 */
    val filePath: String? = null,
    /** 关联的云端歌曲 ID */
    val cloudSongId: String? = null,
    /** 歌曲时长（毫秒） */
    val durationMs: Long = 0L,
    /** 提取的封面文件 file:// URI（扫描时填充，供播放器显示封面） */
    val coverArtUrl: String? = null
)
