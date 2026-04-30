package cp.player.model

data class Message(
    val id: Long,
    val fromUserId: Long,
    val fromNickname: String,
    val fromAvatarUrl: String,
    val text: String,
    val time: Long,
    val isMe: Boolean = false
)
