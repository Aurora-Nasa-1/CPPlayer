package cp.player.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.model.Song
import cp.player.model.Contact
import cp.player.model.Message
import cp.player.model.Comment
import cp.player.model.Playlist

object JsonUtils {

    private val JsonElement?.obj: JsonObject? get() = this?.takeIf { it.isJsonObject }?.asJsonObject
    private val JsonElement?.arr: JsonArray? get() = this?.takeIf { it.isJsonArray }?.asJsonArray
    private val JsonElement?.str: String? get() = this?.takeIf { it.isJsonPrimitive }?.asString
    private val JsonElement?.long: Long? get() = this?.takeIf { it.isJsonPrimitive }?.asLong
    private val JsonElement?.int: Int? get() = this?.takeIf { it.isJsonPrimitive }?.asInt
    private val JsonElement?.bool: Boolean? get() = this?.takeIf { it.isJsonPrimitive }?.asBoolean

    fun parseSong(it: JsonElement): Song? {
        return try {
            val item = it.obj ?: return null

            // Check for cloud song format first
            if (item.has("songId") && item.has("songName")) {
                return parseCloudSongItem(item)
            }

            val obj = item.get("songInfo")?.obj ?: item

            val artists = obj.get("ar")?.arr ?: obj.get("artists")?.arr
            val artistObj = artists?.get(0)?.obj
            val artistName = artistObj?.get("name")?.str ?: "Unknown"
            val artistId = artistObj?.get("id")?.str
            val album = obj.get("al")?.obj ?: obj.get("album")?.obj
            val albumName = album?.get("name")?.str ?: "Unknown"
            var picUrl = album?.get("picUrl")?.str
            if (picUrl == null || picUrl.contains("null")) {
                picUrl = findUrl(obj)
            }

            Song(
                id = (obj.get("id") ?: obj.get("songId"))?.str ?: return null,
                name = obj.get("name")?.str ?: "Unknown",
                artist = artistName,
                artistId = artistId,
                album = albumName,
                albumArtUrl = picUrl,
                durationMs = obj.get("dt")?.long ?: obj.get("duration")?.long ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析云盘歌曲条目。
     *
     * 云盘 API (`user/cloud`) 返回的歌曲结构与在线歌曲不同，
     * 字段为 `songId`/`songName`/`artist`/`album` 等。
     * 优先从嵌套的 `simpleSong` 中提取封面。
     */
    fun parseCloudSongItem(item: JsonObject): Song? {
        return try {
            val songId = (item.get("songId") ?: item.get("id"))?.str ?: return null
            val songName = item.get("songName")?.str ?: "Unknown"
            val artist = item.get("artist")?.str ?: "Unknown"
            val album = item.get("album")?.str ?: "Cloud Storage"
            // 尝试从 simpleSong 中获取封面
            val simpleSong = item.get("simpleSong")?.obj
            val picUrl = simpleSong?.let { ss ->
                ss.get("al")?.obj?.get("picUrl")?.str
                    ?: ss.get("album")?.obj?.get("picUrl")?.str
            }
            val duration = item.get("dt")?.long ?: item.get("duration")?.long
                ?: simpleSong?.get("dt")?.long ?: 0L

            Song(
                id = "cloud_$songId",
                name = songName,
                artist = artist,
                album = album,
                albumArtUrl = picUrl,
                durationMs = duration
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseComment(it: JsonElement): Comment? {
        return try {
            val obj = it.obj ?: return null
            val user = obj.get("user")?.obj ?: obj.get("author")?.obj ?: return null
            val beReplied = obj.get("beReplied")?.arr?.mapNotNull { replyElement ->
                val replyObj = replyElement.obj
                val replyUser = replyObj?.get("user")?.obj
                if (replyUser != null) {
                    Comment.Reply(
                        userId = replyUser.get("userId")?.long ?: 0L,
                        nickname = replyUser.get("nickname")?.str ?: "Unknown",
                        content = replyObj.get("content")?.str ?: ""
                    )
                } else null
            }

            Comment(
                id = (obj.get("commentId") ?: obj.get("id"))?.long ?: return null,
                userId = user.get("userId")?.long ?: 0L,
                nickname = user.get("nickname")?.str ?: "Unknown",
                avatarUrl = user.get("avatarUrl")?.str ?: "",
                content = obj.get("content")?.str ?: "",
                time = obj.get("time")?.long ?: 0L,
                timeStr = obj.get("timeStr")?.str ?: "",
                likedCount = obj.get("likedCount")?.int ?: 0,
                liked = obj.get("liked")?.bool ?: false,
                replyCount = obj.get("replyCount")?.int ?: 0,
                beReplied = beReplied
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseContact(it: JsonElement): Contact? {
        return try {
            val obj = it.obj ?: return null

            val fromUserKeys = listOf("fromUser", "from", "user", "author", "profile")
            val fromUser = fromUserKeys.firstNotNullOfOrNull { key -> obj.get(key)?.obj }

            val lastMsgStr = obj.get("lastMsg")?.str ?: obj.get("msg")?.str ?: ""
            val lastMsgObj = try {
                if (lastMsgStr.startsWith("{")) JsonParser.parseString(lastMsgStr).obj else null
            } catch (e: Exception) { null }

            val messageText = lastMsgObj?.get("msg")?.str ?: lastMsgStr

            Contact(
                userId = fromUser?.get("userId")?.long ?: fromUser?.get("id")?.long ?: 0L,
                nickname = fromUser?.get("nickname")?.str ?: fromUser?.get("userName")?.str ?: "Unknown",
                avatarUrl = fromUser?.get("avatarUrl")?.str ?: "",
                lastMessage = messageText,
                lastMessageTime = obj.get("lastMsgTime")?.long ?: obj.get("time")?.long ?: 0L,
                unreadCount = obj.get("newMsgCount")?.int ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parsePlaylist(element: JsonElement): Playlist? {
        return try {
            val obj = element.obj ?: return null
            val creatorObj = obj.get("creator")?.obj
            val creatorUserId = creatorObj?.get("userId")?.long ?: 0L
            val subscribed = obj.get("subscribed")?.bool ?: false
            Playlist(
                id = obj.get("id")?.long ?: 0L,
                name = getString(obj, "name") ?: "",
                coverImgUrl = getString(obj, "coverImgUrl") ?: getString(obj, "picUrl"),
                trackCount = obj.get("trackCount")?.int ?: 0,
                creatorName = getString(creatorObj, "nickname"),
                creatorUserId = creatorUserId,
                subscribed = subscribed,
                description = getString(obj, "description")
            )
        } catch (e: Exception) { null }
    }

    fun parseMessage(it: JsonElement, myUserId: Long): Message? {
        return try {
            val obj = it.obj ?: return null
            val fromUser = obj.get("fromUser")?.obj ?: return null
            val msgStr = obj.get("msg")?.str ?: "{}"
            val msgContent = try { JsonParser.parseString(msgStr).obj } catch (e: Exception) { null }
            val userId = fromUser.get("userId")?.long ?: return null

            Message(
                id = obj.get("id")?.long ?: return null,
                fromUserId = userId,
                fromNickname = fromUser.get("nickname")?.str ?: "Unknown",
                fromAvatarUrl = fromUser.get("avatarUrl")?.str ?: "",
                text = msgContent?.get("msg")?.str ?: "",
                time = obj.get("time")?.long ?: 0L,
                isMe = userId == myUserId
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getString(element: JsonElement?, key: String, default: String? = null): String? {
        return element?.obj?.get(key)?.str ?: default
    }

    fun findUrl(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        val str = element.str
        if (str != null && str.startsWith("http") && str.length > 12 && !str.contains("null")) {
            return str
        }

        element.obj?.let { obj ->
            // Direct check
            val url = getString(obj, "url") ?: getString(obj, "picUrl") ?: getString(obj, "coverImgUrl") ?: getString(obj, "avatarUrl")
            if (url != null && url.startsWith("http") && url.length > 12 && !url.contains("null")) return url

            // Priority keys
            listOf("al", "album", "data", "result", "songs", "urlInfo").firstNotNullOfOrNull { key ->
                findUrl(obj.get(key))
            }?.let { return it }

            // Exhaustive search
            return obj.entrySet().firstNotNullOfOrNull { findUrl(it.value) }
        }

        element.arr?.let { arr ->
            return arr.firstNotNullOfOrNull { findUrl(it) }
        }

        return null
    }

    fun findJsonArray(element: JsonElement?, key: String): JsonArray? {
        if (element == null || element.isJsonNull) return null

        element.obj?.let { obj ->
            val arr = obj.get(key)?.arr
            if (arr != null) return arr

            return obj.entrySet().firstNotNullOfOrNull { findJsonArray(it.value, key) }
        }

        element.arr?.let { arr ->
            return arr.firstNotNullOfOrNull { findJsonArray(it, key) }
        }

        return null
    }
}
