package cp.player.model

data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String? = null,
    val album: String,
    val albumArtUrl: String? = null,
    val durationMs: Long = 0L
)
