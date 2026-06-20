package cp.player.model

data class Artist(
    val id: Long,
    val name: String,
    val picUrl: String? = null,
    val alias: List<String> = emptyList(),
    val albumSize: Int = 0,
    val briefDesc: String? = null
)
