package cp.player.model

data class LocalSongMetadata(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String? = null,
    /** 音频文件的实际路径，用于提取封面 */
    val filePath: String? = null,
    /** 关联的云端歌曲 ID */
    val cloudSongId: String? = null
)
