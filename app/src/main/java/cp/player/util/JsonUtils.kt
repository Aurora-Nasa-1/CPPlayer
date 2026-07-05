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

private val JsonElement?.obj: JsonObject? get() = if (this?.isJsonObject == true) this.asJsonObject else null
private val JsonElement?.arr: JsonArray? get() = if (this?.isJsonArray == true) this.asJsonArray else null
private val JsonElement?.str: String? get() = if (this?.isJsonPrimitive == true) this.asString else null
private val JsonElement?.long: Long? get() = try { if (this?.isJsonPrimitive == true) this.asLong else null } catch (e: NumberFormatException) { null }
private val JsonElement?.int: Int? get() = try { if (this?.isJsonPrimitive == true) this.asInt else null } catch (e: NumberFormatException) { null }
private val JsonElement?.bool: Boolean? get() = if (this?.isJsonPrimitive == true && this.asJsonPrimitive.isBoolean) this.asBoolean else null


    fun parseSong(it: JsonElement): Song? {
        val item = it.obj ?: return null

        if (item.has("songId") && item.has("songName")) {
            return parseCloudSongItem(item)
        }

        val obj = item.get("songInfo").obj ?: item

        val artists = obj.get("ar").arr ?: obj.get("artists").arr
        val artistObj = artists?.get(0).obj
        val artistName = artistObj?.get("name").str ?: "Unknown"
        val artistId = artistObj?.get("id").str
        val album = obj.get("al").obj ?: obj.get("album").obj
        val albumName = album?.get("name").str ?: "Unknown"
        var picUrl = album?.get("picUrl").str
        if (picUrl == null || picUrl.contains("null")) {
            picUrl = findUrl(obj)
        }

        val id = obj.get("id").str ?: obj.get("songId").str ?: return null
        val name = obj.get("name").str ?: return null

        return Song(
            id = id,
            name = name,
            artist = artistName,
            artistId = artistId,
            album = albumName,
            albumArtUrl = picUrl,
            durationMs = obj.get("dt").long ?: obj.get("duration").long ?: 0L
        )
    }

    /**
     * 解析云盘歌曲条目。
     *
     * 云盘 API (`user/cloud`) 返回的歌曲结构与在线歌曲不同，
     * 字段为 `songId`/`songName`/`artist`/`album` 等。
     * 优先从嵌套的 `simpleSong` 中提取封面。
     */
    fun parseCloudSongItem(item: com.google.gson.JsonObject): Song? {
        val songId = item.get("songId").str ?: item.get("id").str ?: return null
        val songName = item.get("songName").str ?: "Unknown"
        val artist = item.get("artist").str ?: "Unknown"
        val album = item.get("album").str ?: "Cloud Storage"

        val simpleSong = item.get("simpleSong").obj
        val picUrl = simpleSong?.let { ss ->
            ss.get("al").obj?.get("picUrl").str ?: ss.get("album").obj?.get("picUrl").str
        }
        val duration = item.get("dt").long ?: item.get("duration").long ?: simpleSong?.get("dt").long ?: 0L

        return Song(
            id = "cloud_$songId",
            name = songName,
            artist = artist,
            album = album,
            albumArtUrl = picUrl,
            durationMs = duration
        )
    }

    fun parseComment(it: JsonElement): Comment? {
        val obj = it.obj ?: return null
        val user = obj.get("user").obj ?: obj.get("author").obj ?: return null
        val beReplied = obj.get("beReplied").arr?.mapNotNull { replyElement ->
            val replyObj = replyElement.obj
            val replyUser = replyObj?.get("user").obj
            if (replyUser != null) {
                Comment.Reply(
                    userId = replyUser.get("userId").long ?: 0L,
                    nickname = replyUser.get("nickname").str ?: "Unknown",
                    content = replyObj?.get("content").str ?: ""
                )
            } else null
        }

        val id = obj.get("commentId").long ?: obj.get("id").long ?: return null
        val userId = user.get("userId").long ?: return null
        val nickname = user.get("nickname").str ?: return null
        val avatarUrl = user.get("avatarUrl").str ?: return null
        val time = obj.get("time").long ?: return null

        return Comment(
            id = id,
            userId = userId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            content = obj.get("content").str ?: "",
            time = time,
            timeStr = obj.get("timeStr").str ?: "",
            likedCount = obj.get("likedCount").int ?: 0,
            liked = obj.get("liked").bool ?: false,
            replyCount = obj.get("replyCount").int ?: 0,
            beReplied = beReplied
        )
    }

    fun parseContact(it: JsonElement): Contact? {
        val obj = it.obj ?: return null

        val fromUser = obj.get("fromUser").obj ?: obj.get("from").obj ?: obj.get("user").obj ?: obj.get("author").obj ?: obj.get("profile").obj
        val lastMsgStr = obj.get("lastMsg").str ?: obj.get("msg").str ?: ""

        val lastMsgObj = if (lastMsgStr.startsWith("{")) {
            try { com.google.gson.JsonParser.parseString(lastMsgStr).obj } catch (e: Exception) { null }
        } else null

        val messageText = lastMsgObj?.get("msg").str ?: lastMsgStr

        return Contact(
            userId = fromUser?.get("userId").long ?: fromUser?.get("id").long ?: 0L,
            nickname = fromUser?.get("nickname").str ?: fromUser?.get("userName").str ?: "Unknown",
            avatarUrl = fromUser?.get("avatarUrl").str ?: "",
            lastMessage = messageText,
            lastMessageTime = obj.get("lastMsgTime").long ?: obj.get("time").long ?: 0L,
            unreadCount = obj.get("newMsgCount").int ?: 0
        )
    }

    fun parsePlaylist(element: JsonElement): Playlist? {
        val obj = element.obj ?: return null
        val creatorObj = obj.get("creator").obj
        val creatorUserId = creatorObj?.get("userId").long ?: 0L
        val subscribed = obj.get("subscribed").bool ?: false
        val id = obj.get("id").long ?: return null

        return Playlist(
            id = id,
            name = getString(obj, "name") ?: "",
            coverImgUrl = getString(obj, "coverImgUrl") ?: getString(obj, "picUrl"),
            trackCount = obj.get("trackCount").int ?: 0,
            creatorName = getString(creatorObj, "nickname"),
            creatorUserId = creatorUserId,
            subscribed = subscribed,
            description = getString(obj, "description")
        )
    }

    fun parseMessage(it: JsonElement, myUserId: Long): Message? {
        val obj = it.obj ?: return null
        val fromUser = obj.get("fromUser").obj ?: return null
        val msgStr = obj.get("msg").str ?: "{}"
        val msgContent = try { com.google.gson.JsonParser.parseString(msgStr).obj } catch (e: Exception) { null }
        val userId = fromUser.get("userId").long ?: return null

        val id = obj.get("id").long ?: return null
        val fromNickname = fromUser.get("nickname").str ?: return null
        val fromAvatarUrl = fromUser.get("avatarUrl").str ?: return null
        val time = obj.get("time").long ?: return null

        return Message(
            id = id,
            fromUserId = userId,
            fromNickname = fromNickname,
            fromAvatarUrl = fromAvatarUrl,
            text = msgContent?.get("msg").str ?: "",
            time = time,
            isMe = userId == myUserId
        )
    }

    fun getString(element: JsonElement?, key: String, default: String? = null): String? {
        val obj = element.obj ?: return default
        return obj.get(key).str ?: default
    }

    fun findUrl(element: JsonElement?): String? {
        element.str?.let { s ->
            if (s.startsWith("http") && s.length > 12 && !s.contains("null")) return s
        }

        element.obj?.let { obj ->
            // Direct check
            val url = getString(obj, "url") ?: getString(obj, "picUrl") ?: getString(obj, "coverImgUrl") ?: getString(obj, "avatarUrl")
            if (url != null && url.startsWith("http") && url.length > 12 && !url.contains("null")) return url

            // Priority keys
            listOf("al", "album", "data", "result", "songs", "urlInfo").firstNotNullOfOrNull { key ->
                obj.get(key)?.let { findUrl(it) }
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
        element.obj?.let { obj ->
            obj.get(key).arr?.let { return it }
            return obj.entrySet().firstNotNullOfOrNull { findJsonArray(it.value, key) }
        }

        element.arr?.let { arr ->
            return arr.firstNotNullOfOrNull { findJsonArray(it, key) }
        }

        return null
    }
}
