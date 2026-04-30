package cp.player.model

data class UserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    val signature: String? = null,
    val gender: Int = 0,
    val province: Int = 0,
    val city: Int = 0,
    val birthday: Long = 0,
    val followed: Boolean = false,
    val follows: Int = 0,
    val followeds: Int = 0,
    val eventCount: Int = 0,
    val playlistCount: Int = 0
)
