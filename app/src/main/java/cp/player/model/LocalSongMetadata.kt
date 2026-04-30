package cp.player.model

data class LocalSongMetadata(
    val songId: String,
    val fileName: String,
    val songName: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String? = null
)
