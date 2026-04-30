package cp.player.model

data class OtherUserViewState(
    val uid: Long = 0L,
    val profile: UserProfile? = null,
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isArtist: Boolean = false,
    val isLoading: Boolean = false
)
