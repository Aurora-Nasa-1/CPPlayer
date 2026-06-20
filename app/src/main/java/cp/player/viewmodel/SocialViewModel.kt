package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewModelScope
import cp.player.model.*
import cp.player.provider.ProviderManager
import cp.player.util.JsonUtils
import kotlinx.coroutines.*

class SocialViewModel(application: Application) : BaseViewModel(application) {
    var hotComments by mutableStateOf<List<Comment>>(emptyList())
    var newestComments by mutableStateOf<List<Comment>>(emptyList())
    var commentTotal by mutableIntStateOf(0)
    var hasMoreComments by mutableStateOf(true)
    var currentCommentPage by mutableIntStateOf(0)
    var commentSortType by mutableIntStateOf(1) // 0: Recommend, 1: Hot, 2: Time
    var commentCursor by mutableStateOf("")

    var floorComments by mutableStateOf<List<Comment>>(emptyList())
    var floorCommentTotal by mutableIntStateOf(0)
    var floorHasMore by mutableStateOf(false)
    var floorCursor by mutableStateOf(0L)
    var activeParentComment by mutableStateOf<Comment?>(null)

    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var chatMessages by mutableStateOf<List<Message>>(emptyList())
    var unreadCount by mutableIntStateOf(0)
    var isCommentsLoading by mutableStateOf(false)
    var isFloorCommentsLoading by mutableStateOf(false)
    var isContactsLoading by mutableStateOf(false)
    var isMessagesLoading by mutableStateOf(false)

    init {
        // 监听提供商变更：切换时清空社交数据
        viewModelScope.launch {
            var isFirst = true
            ProviderManager.currentProviderFlow.collect { provider ->
                if (isFirst) { isFirst = false; return@collect }
                if (provider != null) {
                    android.util.Log.i("SocialViewModel", "Provider changed to ${provider.id}, resetting social data")
                    contacts = emptyList()
                    chatMessages = emptyList()
                    unreadCount = 0
                    hotComments = emptyList()
                    newestComments = emptyList()
                    commentTotal = 0
                }
            }
        }
    }

    fun fetchComments(id: String, type: String = "music", sortType: Int = 1, page: Int = 1) {
        viewModelScope.launch {
            isCommentsLoading = true
            try {
                val body = withContext(Dispatchers.IO) { api.getComments(id, type, 20, (page - 1) * 20, sortType) }
                android.util.Log.d("SocialViewModel", "Comments result: $body")

                val commentsArr = JsonUtils.findJsonArray(body, "comments")
                    ?: JsonUtils.findJsonArray(body, "list")
                    ?: JsonUtils.findJsonArray(body, "data")
                val newComments = commentsArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()

                if (page == 1) {
                    newestComments = newComments
                    val hotArr = body.get("hotComments")?.asJsonArray
                    hotComments = hotArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()
                } else {
                    if (sortType == 1) {
                        hotComments = hotComments + newComments
                    } else {
                        newestComments = newestComments + newComments
                    }
                }

                commentTotal = (body.get("totalCount") ?: body.get("total"))?.asInt ?: 0
                hasMoreComments = (body.get("hasMore") ?: body.get("more"))?.asBoolean ?: true
                currentCommentPage = page
                commentSortType = sortType
            } finally { isCommentsLoading = false }
        }
    }

    fun fetchFloorComments(id: String, parentCommentId: Long, type: String = "music", time: Long = 0L) {
        viewModelScope.launch {
            isFloorCommentsLoading = true
            try {
                val body = withContext(Dispatchers.IO) { api.getFloorComments(id, parentCommentId, type, 20, time) }
                val data = body.get("data")?.asJsonObject
                val commentsArr = data?.get("comments")?.asJsonArray
                val newComments = commentsArr?.mapNotNull { JsonUtils.parseComment(it) } ?: emptyList()

                floorComments = if (time == 0L) newComments else floorComments + newComments
                floorCommentTotal = data?.get("totalCount")?.asInt ?: 0
                floorHasMore = data?.get("hasMore")?.asBoolean ?: false
                floorCursor = data?.get("time")?.asLong ?: 0L
            } finally { isFloorCommentsLoading = false }
        }
    }

    fun fetchUnreadCount() {
        if (cookie == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val body = api.getUnreadCount()
                val count = body.get("msg")?.asInt ?: body.get("data")?.asJsonObject?.get("msg")?.asInt ?: 0
                withContext(Dispatchers.Main) { unreadCount = count }
            } catch (e: Exception) { }
        }
    }

    fun fetchContacts() {
        viewModelScope.launch {
            isContactsLoading = true
            try {
                // Try both msg/recentcontact and msg/private for compatibility
                val body = withContext(Dispatchers.IO) { api.getRecentContacts() }
                val recent = body.get("recentcontacts")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) }
                    ?: body.get("data")?.asJsonObject?.get("recentcontacts")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) }

                if (recent != null && recent.isNotEmpty()) {
                    contacts = recent
                } else {
                    // Fallback to msg/private (notifications/private messages)
                    val privateBody = withContext(Dispatchers.IO) { api.getPrivateMessages() }
                    val privateMsgs = privateBody.get("msgs")?.asJsonArray?.mapNotNull { JsonUtils.parseContact(it) }
                    contacts = privateMsgs ?: emptyList()
                }
            } finally { isContactsLoading = false }
        }
    }

    fun fetchMessages(uid: Long) {
        if (cookie == null) return
        viewModelScope.launch {
            isMessagesLoading = true
            try {
                val body = withContext(Dispatchers.IO) { api.getMessageHistory(uid) }
                chatMessages = body.get("msgs")?.asJsonArray?.mapNotNull { JsonUtils.parseMessage(it, 0L) }?.reversed() ?: emptyList()
            } finally { isMessagesLoading = false }
        }
    }

    fun toggleCommentLike(id: String, cid: Long, type: String, liked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            api.likeComment(id, cid, type, liked)
            withContext(Dispatchers.Main) {
                val update: (Comment) -> Comment = { if (it.id == cid) it.copy(liked = liked, likedCount = it.likedCount + if (liked) 1 else -1) else it }
                hotComments = hotComments.map(update)
                newestComments = newestComments.map(update)
            }
        }
    }

    fun postComment(id: String, type: String, content: String, replyId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.postComment(id, type, content, replyId)
            if (body.get("code")?.asInt == 200) fetchComments(id, type, commentSortType)
        }
    }

    fun markMessageAsRead(uid: Long) {
        if (cookie == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.markMessageAsRead(uid)
            } catch (e: Exception) { }
        }
    }

    fun sendMessage(uid: Long, text: String) {
        if (cookie == null || text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.sendMessage(uid, text)
            if (body.get("code")?.asInt == 200) {
                fetchMessages(uid)
            }
        }
    }
}
