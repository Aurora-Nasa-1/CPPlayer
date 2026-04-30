package cp.player.model

data class Comment(
    val id: Long,
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    val content: String,
    val time: Long,
    val timeStr: String,
    val likedCount: Int,
    val liked: Boolean,
    val replyCount: Int = 0,
    val beReplied: List<Reply>? = null
) {
    data class Reply(
        val userId: Long,
        val nickname: String,
        val content: String
    )
}
