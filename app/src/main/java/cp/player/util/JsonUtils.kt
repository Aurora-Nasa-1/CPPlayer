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
    fun parseSong(it: JsonElement): Song? {
        return try {
            val item = it.asJsonObject

            // Check for cloud song format first
            if (item.has("songId") && item.has("songName")) {
                return Song(
                    id = (item.get("songId") ?: item.get("id")).asString,
                    name = item.get("songName").asString,
                    artist = item.get("artist")?.asString ?: "Unknown",
                    album = item.get("album")?.asString ?: "Cloud Storage",
                    albumArtUrl = null
                )
            }

            val obj = if (item.has("songInfo")) item.get("songInfo").asJsonObject else item

            val artists = obj.get("ar")?.asJsonArray ?: obj.get("artists")?.asJsonArray
            val artistObj = artists?.get(0)?.asJsonObject
            val artistName = artistObj?.get("name")?.asString ?: "Unknown"
            val artistId = artistObj?.get("id")?.asString
            val album = obj.get("al")?.asJsonObject ?: obj.get("album")?.asJsonObject
            val albumName = album?.get("name")?.asString ?: "Unknown"
            var picUrl = album?.get("picUrl")?.asString
            if (picUrl == null || picUrl.contains("null")) {
                picUrl = findUrl(obj)
            }

            Song(
                id = (obj.get("id") ?: obj.get("songId")).asJsonPrimitive.asString,
                name = obj.get("name").asString,
                artist = artistName,
                artistId = artistId,
                album = albumName,
                albumArtUrl = picUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseComment(it: JsonElement): Comment? {
        return try {
            val obj = it.asJsonObject
            val user = obj.get("user")?.asJsonObject ?: obj.get("author")?.asJsonObject ?: return null
            val beReplied = obj.get("beReplied")?.asJsonArray?.mapNotNull {
                val replyObj = it.asJsonObject
                val replyUser = replyObj.get("user")?.asJsonObject
                if (replyUser != null) {
                    Comment.Reply(
                        userId = replyUser.get("userId")?.asLong ?: 0L,
                        nickname = replyUser.get("nickname")?.asString ?: "Unknown",
                        content = replyObj.get("content")?.asString ?: ""
                    )
                } else null
            }

            Comment(
                id = (obj.get("commentId") ?: obj.get("id")).asLong,
                userId = user.get("userId").asLong,
                nickname = user.get("nickname").asString,
                avatarUrl = user.get("avatarUrl").asString,
                content = obj.get("content")?.asString ?: "",
                time = obj.get("time").asLong,
                timeStr = obj.get("timeStr")?.asString ?: "",
                likedCount = obj.get("likedCount")?.asInt ?: 0,
                liked = obj.get("liked")?.asBoolean ?: false,
                replyCount = obj.get("replyCount")?.asInt ?: 0,
                beReplied = beReplied
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseContact(it: JsonElement): Contact? {
        return try {
            val obj = it.asJsonObject

            // Handle different variations of where user info might be stored
            val fromUser = when {
                obj.has("fromUser") && obj.get("fromUser").isJsonObject -> obj.get("fromUser").asJsonObject
                obj.has("from") && obj.get("from").isJsonObject -> obj.get("from").asJsonObject
                obj.has("user") && obj.get("user").isJsonObject -> obj.get("user").asJsonObject
                obj.has("author") && obj.get("author").isJsonObject -> obj.get("author").asJsonObject
                obj.has("profile") && obj.get("profile").isJsonObject -> obj.get("profile").asJsonObject
                else -> null
            }

            val lastMsgStr = when {
                obj.has("lastMsg") && obj.get("lastMsg").isJsonPrimitive -> obj.get("lastMsg").asString
                obj.has("msg") && obj.get("msg").isJsonPrimitive -> obj.get("msg").asString
                else -> ""
            }

            val lastMsgObj = try {
                if (lastMsgStr.startsWith("{")) JsonParser.parseString(lastMsgStr).asJsonObject else null
            } catch (e: Exception) { null }

            val messageText = lastMsgObj?.get("msg")?.asString ?: lastMsgStr

            Contact(
                userId = fromUser?.get("userId")?.asLong ?: fromUser?.get("id")?.asLong ?: 0L,
                nickname = fromUser?.get("nickname")?.asString ?: fromUser?.get("userName")?.asString ?: "Unknown",
                avatarUrl = fromUser?.get("avatarUrl")?.asString ?: "",
                lastMessage = messageText,
                lastMessageTime = obj.get("lastMsgTime")?.asLong ?: obj.get("time")?.asLong ?: 0L,
                unreadCount = obj.get("newMsgCount")?.asInt ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseMessage(it: JsonElement, myUserId: Long): Message? {
        return try {
            val obj = it.asJsonObject
            val fromUser = obj.get("fromUser").asJsonObject
            val msgStr = obj.get("msg")?.asString ?: "{}"
            val msgContent = try { JsonParser.parseString(msgStr).asJsonObject } catch (e: Exception) { JsonObject() }
            val userId = fromUser.get("userId").asLong

            Message(
                id = obj.get("id").asLong,
                fromUserId = userId,
                fromNickname = fromUser.get("nickname").asString,
                fromAvatarUrl = fromUser.get("avatarUrl").asString,
                text = msgContent.get("msg")?.asString ?: "",
                time = obj.get("time").asLong,
                isMe = userId == myUserId
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getString(element: JsonElement?, key: String, default: String? = null): String? {
        val obj = if (element != null && element.isJsonObject) element.asJsonObject else return default
        val field = obj.get(key)
        return if (field != null && !field.isJsonNull && field.isJsonPrimitive) field.asString else default
    }

    fun findUrl(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val s = element.asString
            if (s.startsWith("http") && s.length > 12 && !s.contains("null")) return s
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            // Direct check
            val url = getString(obj, "url") ?: getString(obj, "picUrl") ?: getString(obj, "coverImgUrl") ?: getString(obj, "avatarUrl")
            if (url != null && url.startsWith("http") && url.length > 12 && !url.contains("null")) return url

            // Priority keys
            listOf("al", "album", "data", "result", "songs", "urlInfo").forEach { key ->
                if (obj.has(key)) {
                    val found = findUrl(obj.get(key))
                    if (found != null) return found
                }
            }

            // Exhaustive search
            for (entry in obj.entrySet()) {
                val found = findUrl(entry.value)
                if (found != null) return found
            }
        }

        if (element.isJsonArray) {
            val arr = element.asJsonArray
            for (i in 0 until arr.size()) {
                val found = findUrl(arr.get(i))
                if (found != null) return found
            }
        }

        return null
    }

    fun findJsonArray(element: JsonElement?, key: String): JsonArray? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has(key) && obj.get(key).isJsonArray) return obj.getAsJsonArray(key)

            for (entry in obj.entrySet()) {
                val found = findJsonArray(entry.value, key)
                if (found != null) return found
            }
        }

        if (element.isJsonArray) {
            val arr = element.asJsonArray
            for (i in 0 until arr.size()) {
                val found = findJsonArray(arr.get(i), key)
                if (found != null) return found
            }
        }

        return null
    }
}
