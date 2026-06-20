package cp.player.api

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.monitor.HealthMonitor
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.util.DebugLog
import cp.player.util.UserPreferences

/**
 * [MusicApiService] 的默认实现。
 *
 * 通过 [ProviderManager] 将请求委托给当前活跃的 [BackendProvider][cp.player.provider.BackendProvider]。
 * 当 URL 解析场景需要多 Provider 容灾时，使用 [callWithAllProviders]。
 *
 * ### 初始化
 * 在 [cp.player.CPApplication.onCreate] 中初始化 [ProviderManager] 后，
 * 通过 [MusicApiServiceFactory.create] 获取单例。
 *
 * @see MusicApiMethod API 方法名常量
 * @see MusicApiService 接口文档
 */
class MusicApiServiceImpl(
    private val context: Context
) : MusicApiService {

    // ======================== 通用 ========================

    override fun callApi(
        method: String,
        params: Map<String, String>,
        cookie: String?
    ): JsonObject {
        val effectiveCookie = cookie ?: UserPreferences.getCookie(context)
        val finalParams = if (!effectiveCookie.isNullOrEmpty() && !params.containsKey("cookie")) {
            params + ("cookie" to effectiveCookie)
        } else {
            params
        }
        val startTime = System.currentTimeMillis()
        val providerId = ProviderManager.getCurrentProviderId()
        val result = ProviderManager.callApi(method, finalParams)
        val duration = System.currentTimeMillis() - startTime

        return try {
            val json = JsonParser.parseString(result).asJsonObject
            val issues = validateResponse(method, json, duration)
            val code = json.get("code")?.asInt
            val success = code == 200 || code == 0 || code == 201 || code == 301

            HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                timestamp = startTime,
                providerId = providerId,
                method = method,
                durationMs = duration,
                success = success,
                responseCode = code,
                errorCode = if (!success) code else null,
                errorMessage = buildErrorMessage(json, issues, success),
                responseWarnings = issues.map { it.warning },
                expectedField = issues.firstOrNull { it.expected != null }?.expected
            ))
            json
        } catch (e: Exception) {
            HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                timestamp = startTime,
                providerId = providerId,
                method = method,
                durationMs = duration,
                success = false,
                errorCode = 500,
                errorMessage = "JSON parse error: ${e.message}",
                responseWarnings = listOf(HealthMonitor.ResponseWarning.MALFORMED_RESPONSE)
            ))
            JsonObject().apply {
                addProperty("code", 500)
                addProperty("msg", "JSON parse error: ${e.message}")
            }
        }
    }

    // ======================== 认证 Auth ========================

    override fun getQrKey(): JsonObject =
        callApi(MusicApiMethod.AUTH_QR_KEY)

    override fun createQrCode(key: String): JsonObject =
        callApi(MusicApiMethod.AUTH_QR_CREATE, mapOf("key" to key, "qrimg" to "true"))

    override fun checkQrStatus(key: String): JsonObject =
        callApi(MusicApiMethod.AUTH_QR_CHECK, mapOf("key" to key))

    override fun login(email: String, password: String, md5: Boolean): JsonObject {
        val params = mutableMapOf("email" to email)
        if (md5) params["md5_password"] = password else params["password"] = password
        return callApi(MusicApiMethod.AUTH_LOGIN, params)
    }

    override fun loginWithPhone(
        phone: String,
        password: String,
        captcha: Boolean,
        md5: Boolean
    ): JsonObject {
        val params = mutableMapOf("phone" to phone)
        when {
            captcha -> params["captcha"] = password
            md5 -> params["md5_password"] = password
            else -> params["password"] = password
        }
        return callApi(MusicApiMethod.AUTH_LOGIN_PHONE, params)
    }

    override fun sendCaptcha(phone: String): JsonObject =
        callApi(MusicApiMethod.AUTH_CAPTCHA_SENT, mapOf("phone" to phone))

    override fun logout(): JsonObject =
        callApi(MusicApiMethod.AUTH_LOGOUT)

    override fun loginAnonymous(): JsonObject =
        callApi(MusicApiMethod.AUTH_ANONYMOUS)

    override fun getLoginStatus(cookie: String?): JsonObject =
        callApi(MusicApiMethod.AUTH_LOGIN_STATUS, cookie = cookie)

    // ======================== 用户 User ========================

    override fun getUserPlaylists(uid: Long): JsonObject =
        callApi(MusicApiMethod.USER_PLAYLIST, mapOf("uid" to uid.toString()))

    override fun getUserCreatedPlaylists(uid: Long): JsonObject =
        callApi(MusicApiMethod.USER_PLAYLIST_CREATE, mapOf("uid" to uid.toString()))

    override fun getUserCollectedPlaylists(uid: Long): JsonObject =
        callApi(MusicApiMethod.USER_PLAYLIST_COLLECT, mapOf("uid" to uid.toString()))

    override fun getUserDetail(uid: Long): JsonObject =
        callApi(MusicApiMethod.USER_DETAIL, mapOf("uid" to uid.toString()))

    override fun getUserCloud(limit: Int): JsonObject =
        callApi(MusicApiMethod.USER_CLOUD, mapOf("limit" to limit.toString()))

    override fun getLikeList(uid: Long): JsonObject =
        callApi(MusicApiMethod.USER_LIKE_LIST, mapOf("uid" to uid.toString()))

    override fun likeSong(id: String, like: Boolean): JsonObject =
        callApi(MusicApiMethod.USER_LIKE, mapOf("id" to id, "like" to like.toString()))

    override fun getRecommendedSongs(): JsonObject =
        callApi(MusicApiMethod.USER_RECOMMEND_SONGS)

    override fun getRecommendedPlaylists(): JsonObject =
        callApi(MusicApiMethod.USER_RECOMMEND_RESOURCE)

    override fun dislikeSong(id: String): JsonObject =
        callApi(MusicApiMethod.USER_DISLIKE_SONG, mapOf("id" to id))

    // ======================== 歌单 Playlist ========================

    override fun getPlaylistDetail(id: Long): JsonObject =
        callApi(MusicApiMethod.PLAYLIST_DETAIL, mapOf("id" to id.toString()))

    override fun getPlaylistTracks(id: Long, limit: Int, offset: Int): JsonObject =
        callApi(
            MusicApiMethod.PLAYLIST_TRACK_ALL,
            mapOf("id" to id.toString(), "limit" to limit.toString(), "offset" to offset.toString())
        )

    override fun addTracksToPlaylist(pid: Long, trackIds: List<String>): JsonObject =
        callApi(
            MusicApiMethod.PLAYLIST_TRACKS,
            mapOf("op" to "add", "pid" to pid.toString(), "tracks" to trackIds.joinToString(","))
        )

    override fun removeTracksFromPlaylist(pid: Long, trackIds: List<String>): JsonObject =
        callApi(
            MusicApiMethod.PLAYLIST_TRACKS,
            mapOf("op" to "del", "pid" to pid.toString(), "tracks" to trackIds.joinToString(","))
        )

    override fun createPlaylist(name: String, privacy: Int): JsonObject =
        callApi(MusicApiMethod.PLAYLIST_CREATE, mapOf("name" to name, "privacy" to privacy.toString()))

    override fun deletePlaylist(id: Long): JsonObject =
        callApi(MusicApiMethod.PLAYLIST_DELETE, mapOf("id" to id.toString()))

    override fun subscribePlaylist(id: Long, t: Int): JsonObject =
        callApi(MusicApiMethod.PLAYLIST_SUBSCRIBE, mapOf("id" to id.toString(), "t" to t.toString()))

    // ======================== 专辑 Album ========================

    override fun getAlbumDetail(id: Long): JsonObject =
        callApi(MusicApiMethod.ALBUM_DETAIL, mapOf("id" to id.toString()))

    // ======================== 歌手 Artist ========================

    override fun getArtistDetail(id: Long): JsonObject =
        callApi(MusicApiMethod.ARTIST_DETAIL, mapOf("id" to id.toString()))

    override fun getArtistSongs(id: Long, limit: Int): JsonObject =
        callApi(MusicApiMethod.ARTIST_SONGS, mapOf("id" to id.toString(), "limit" to limit.toString()))

    override fun getArtistAlbums(id: Long, limit: Int): JsonObject =
        callApi(MusicApiMethod.ARTIST_ALBUM, mapOf("id" to id.toString(), "limit" to limit.toString()))

    // ======================== 搜索 Search ========================

    override fun search(keywords: String, type: Int): JsonObject =
        callApi(MusicApiMethod.SEARCH_CLOUD, mapOf("keywords" to keywords, "type" to type.toString()))

    override fun getHotSearches(): JsonObject =
        callApi(MusicApiMethod.SEARCH_HOT_DETAIL)

    override fun getSearchSuggestions(keywords: String): JsonObject =
        callApi(MusicApiMethod.SEARCH_SUGGEST, mapOf("keywords" to keywords, "type" to "mobile"))

    // ======================== 播放 Playback ========================

    override fun getSongUrl(songId: String, level: String): JsonObject {
        val params = mutableMapOf("id" to songId, "level" to level)
        // 优先尝试 302 重定向版本
        val result = callApi(MusicApiMethod.SONG_URL_V1_302, params)
        val url = extractUrl(result)
        if (!url.isNullOrEmpty() && url.startsWith("http")) {
            // 确保 result 中包含 redirectUrl 字段以便 BackendDataSource 使用
            if (!result.has("redirectUrl") && !result.has("url")) {
                result.addProperty("redirectUrl", url)
            }
            return result
        }
        // 自动回退到非 302 版本
        DebugLog.d("getSongUrl: 302 版本无有效 URL，回退到 SONG_URL_V1")
        HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
            timestamp = System.currentTimeMillis(),
            providerId = ProviderManager.getCurrentProviderId(),
            method = MusicApiMethod.SONG_URL_V1_302,
            durationMs = 0,
            success = false,
            wasFallback = true,
            fallbackFrom = MusicApiMethod.SONG_URL_V1,
            errorMessage = "302 版本无有效 URL，自动回退"
        ))
        return callApi(MusicApiMethod.SONG_URL_V1, params)
    }

    override fun getSongUrlFallback(songId: String, level: String): JsonObject =
        callApi(MusicApiMethod.SONG_URL_V1, mapOf("id" to songId, "level" to level))

    override fun getSongDownloadUrl(songId: String, level: String): JsonObject =
        callApi(MusicApiMethod.SONG_DOWNLOAD_URL, mapOf("id" to songId, "level" to level))

    override fun getSongDetail(ids: List<String>): JsonObject =
        callApi(MusicApiMethod.SONG_DETAIL, mapOf("ids" to ids.joinToString(",")))

    override fun getPersonalFm(): JsonObject =
        callApi(MusicApiMethod.PERSONAL_FM, mapOf("timestamp" to System.currentTimeMillis().toString()))

    override fun getIntelligenceList(songId: String, playlistId: Long): JsonObject =
        callApi(
            MusicApiMethod.INTELLIGENCE_LIST,
            mapOf(
                "id" to songId,
                "pid" to playlistId.toString(),
                "sid" to songId,
                "count" to "20"
            )
        )

    override fun getLyric(songId: String): JsonObject =
        callApi(MusicApiMethod.LYRIC_NEW, mapOf("id" to songId))

    // ======================== 社交 Social ========================

    override fun getComments(id: String, type: String, limit: Int, offset: Int, sortType: Int): JsonObject =
        callApi(
            getCommentMethod(type),
            mapOf("id" to id, "limit" to limit.toString(), "offset" to offset.toString(), "sortType" to sortType.toString())
        )

    override fun getFloorComments(
        id: String,
        parentCommentId: Long,
        type: String,
        limit: Int,
        time: Long
    ): JsonObject {
        val t = when (type) {
            "music" -> "0"; "mv" -> "1"; "playlist" -> "2"
            "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"
            else -> "0"
        }
        val params = mutableMapOf(
            "id" to id,
            "parentCommentId" to parentCommentId.toString(),
            "type" to t,
            "limit" to limit.toString()
        )
        if (time > 0) params["time"] = time.toString()
        return callApi(MusicApiMethod.COMMENT_FLOOR, params)
    }

    override fun likeComment(id: String, cid: Long, type: String, liked: Boolean): JsonObject {
        val t = when (type) {
            "music" -> "0"; "mv" -> "1"; "playlist" -> "2"
            "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"
            else -> "0"
        }
        return callApi(
            MusicApiMethod.COMMENT_LIKE,
            mapOf("id" to id, "cid" to cid.toString(), "t" to if (liked) "1" else "0", "type" to t)
        )
    }

    override fun postComment(id: String, type: String, content: String, replyId: Long?): JsonObject {
        val t = when (type) {
            "music" -> "0"; "mv" -> "1"; "playlist" -> "2"
            "album" -> "3"; "dj" -> "4"; "video" -> "5"; "event" -> "6"
            else -> "0"
        }
        val params = mutableMapOf(
            "id" to id,
            "type" to t,
            "content" to content,
            "op" to if (replyId != null) "reply" else "add"
        )
        if (replyId != null) params["commentId"] = replyId.toString()
        return callApi(MusicApiMethod.COMMENT_POST, params)
    }

    override fun getUnreadCount(): JsonObject =
        callApi(MusicApiMethod.MESSAGE_UNREAD_COUNT)

    override fun getRecentContacts(): JsonObject =
        callApi(MusicApiMethod.MESSAGE_RECENT_CONTACT)

    override fun getPrivateMessages(): JsonObject =
        callApi(MusicApiMethod.MESSAGE_PRIVATE, mapOf("limit" to "50"))

    override fun getMessageHistory(uid: Long): JsonObject =
        callApi(MusicApiMethod.MESSAGE_PRIVATE_HISTORY, mapOf("uid" to uid.toString()))

    override fun markMessageAsRead(uid: Long): JsonObject =
        callApi(MusicApiMethod.MESSAGE_MARK_READ, mapOf("uid" to uid.toString()))

    override fun sendMessage(uid: Long, text: String): JsonObject =
        callApi(MusicApiMethod.MESSAGE_SEND_TEXT, mapOf("user_ids" to uid.toString(), "msg" to text))

    // ======================== 多 Provider 容灾 ========================

    /**
     * 依次尝试所有已加载的 Provider 调用 API。
     * 用于 URL 解析等需要容灾的场景。
     *
     * 自动通过每个 Provider 的 apiMap 映射方法名。
     *
     * @param method API 方法名（内部标准名）
     * @param params 请求参数
     * @param predicate 判断返回结果是否成功的谓词
     * @return 第一个成功的结果，全部失败则返回最后一个结果
     */
    fun <T> callWithAllProviders(
        method: String,
        params: Map<String, String>,
        predicate: (JsonObject) -> T?
    ): T? {
        val providers = ModuleManager.getAvailableProviders().toMutableList()
        val current = ProviderManager.currentProvider
        if (current != null) {
            providers.remove(current)
            providers.add(0, current) // 当前 Provider 优先
        }

        for (provider in providers) {
            for (attempt in 1..2) {
                val startTime = System.currentTimeMillis()
                try {
                    val actualMethod = if (attempt == 1) MusicApiMethod.SONG_URL_V1_302 else method
                    // 通过 apiMap 映射方法名
                    val mappedMethod = provider.apiMap?.get(actualMethod) ?: actualMethod
                    if (mappedMethod.isEmpty() || mappedMethod.equals("unsupported", ignoreCase = true)) {
                        DebugLog.d("callWithAllProviders: ${provider.id} 不支持 $actualMethod")
                        continue
                    }
                    val result = provider.callApi(mappedMethod, params)
                    val body = JsonParser.parseString(result).asJsonObject
                    val value = predicate(body)
                    if (value != null) {
                        // 记录成功（如果是回退则标记）
                        val isFallback = provider.id != current?.id
                        HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                            timestamp = startTime,
                            providerId = provider.id,
                            method = method,
                            durationMs = System.currentTimeMillis() - startTime,
                            success = true,
                            wasFallback = isFallback,
                            fallbackFrom = if (isFallback) current?.id else null
                        ))
                        return value
                    }
                } catch (e: Exception) {
                    HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                        timestamp = startTime,
                        providerId = provider.id,
                        method = method,
                        durationMs = System.currentTimeMillis() - startTime,
                        success = false,
                        errorMessage = e.message,
                        wasFallback = provider.id != current?.id,
                        fallbackFrom = if (provider.id != current?.id) current?.id else null
                    ))
                }
                if (attempt < 2) Thread.sleep(200L)
            }
        }
        return null
    }

    // ======================== 响应兼容性验证 ========================

    /**
     * 验证结果条目。
     * @param warning 警告类型
     * @param expected 期望的数据字段名（如 "data", "playlist"），null 表示无特定期望
     * @param detail 详细描述
     */
    private data class ValidationIssue(
        val warning: HealthMonitor.ResponseWarning,
        val expected: String? = null,
        val detail: String = ""
    )

    /** 方法 → 期望的主要数据字段名 */
    private val EXPECTED_FIELDS = mapOf(
        MusicApiMethod.SEARCH_CLOUD to "result",
        MusicApiMethod.USER_PLAYLIST to "playlist",
        MusicApiMethod.USER_PLAYLIST_CREATE to "playlist",
        MusicApiMethod.USER_PLAYLIST_COLLECT to "playlist",
        MusicApiMethod.USER_DETAIL to "profile",
        MusicApiMethod.USER_CLOUD to "data",
        MusicApiMethod.USER_LIKE_LIST to "ids",
        MusicApiMethod.USER_RECOMMEND_SONGS to "data",
        MusicApiMethod.USER_RECOMMEND_RESOURCE to "recommend",
        MusicApiMethod.PLAYLIST_DETAIL to "playlist",
        MusicApiMethod.PLAYLIST_TRACK_ALL to "songs",
        MusicApiMethod.ALBUM_DETAIL to "album",
        MusicApiMethod.ARTIST_DETAIL to "data",
        MusicApiMethod.ARTIST_SONGS to "songs",
        MusicApiMethod.ARTIST_ALBUM to "hotAlbums",
        MusicApiMethod.SONG_DETAIL to "songs",
        MusicApiMethod.LYRIC_NEW to "lrc",
        MusicApiMethod.COMMENT_MUSIC to "comments",
        MusicApiMethod.COMMENT_PLAYLIST to "comments",
        MusicApiMethod.COMMENT_ALBUM to "comments",
        MusicApiMethod.COMMENT_FLOOR to "comments",
        MusicApiMethod.MESSAGE_PRIVATE to "msgs",
        MusicApiMethod.MESSAGE_PRIVATE_HISTORY to "msgs",
        MusicApiMethod.MESSAGE_RECENT_CONTACT to "data",
        MusicApiMethod.SONG_URL_V1 to "data",
        MusicApiMethod.SONG_URL_V1_302 to "data",
        MusicApiMethod.SONG_DOWNLOAD_URL to "data"
    )

    /** 回退候选字段（当主字段不存在时依次检查） */
    private val FALLBACK_FIELDS = listOf("data", "result", "playlist", "songs", "albums", "artists", "comments", "msgs", "hotData", "list")

    /**
     * 构建详细的错误/警告消息，包含期望字段信息。
     */
    private fun buildErrorMessage(json: JsonObject, issues: List<ValidationIssue>, success: Boolean): String? {
        if (success && issues.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (!success) {
            val msg = json.get("msg")?.asString ?: json.get("message")?.asString
            parts.add("code=${json.get("code")?.asString ?: "?"}${if (msg != null) ", msg=$msg" else ""}")
        }
        for (issue in issues) {
            when (issue.warning) {
                HealthMonitor.ResponseWarning.MISSING_CODE ->
                    parts.add("缺少code字段, 实际keys=${json.keySet().take(8)}")
                HealthMonitor.ResponseWarning.UNEXPECTED_CODE ->
                    parts.add("异常code=${json.get("code")?.asString}")
                HealthMonitor.ResponseWarning.MISSING_DATA_FIELD ->
                    parts.add("期望字段: ${issue.expected ?: "?"}, 实际字段: ${json.keySet()}")
                HealthMonitor.ResponseWarning.EMPTY_DATA_ARRAY ->
                    parts.add("字段'${issue.expected}'为空数组")
                HealthMonitor.ResponseWarning.EMPTY_DATA_OBJECT ->
                    parts.add("字段'${issue.expected}'为空对象")
                HealthMonitor.ResponseWarning.MALFORMED_RESPONSE ->
                    parts.add("响应格式异常(非JSON)")
                HealthMonitor.ResponseWarning.UNSUPPORTED_BY_PROVIDER ->
                    parts.add("Provider不支持(code=-1)")
                HealthMonitor.ResponseWarning.SLOW_RESPONSE ->
                    parts.add("响应过慢(>${issue.detail}ms)")
            }
        }
        return parts.joinToString("; ").take(500)
    }

    /**
     * 验证 API 响应的兼容性。返回问题列表（空 = 响应正常）。
     */
    private fun validateResponse(method: String, json: JsonObject, durationMs: Long): List<ValidationIssue> {
        // 预分配容量避免动态扩容
        val issues = ArrayList<ValidationIssue>(4)

        // 1. code 字段
        val codeElement = json.get("code")
        if (codeElement == null || codeElement.isJsonNull) {
            issues.add(ValidationIssue(HealthMonitor.ResponseWarning.MISSING_CODE))
        } else {
            val code = codeElement.asInt
            if (code != 200 && code != 0 && code != 201 && code != 301) {
                issues.add(ValidationIssue(
                    if (code == -1) HealthMonitor.ResponseWarning.UNSUPPORTED_BY_PROVIDER
                    else HealthMonitor.ResponseWarning.UNEXPECTED_CODE
                ))
            }
        }

        // 2. 数据字段检查
        val expectedField = EXPECTED_FIELDS[method]
        if (expectedField != null) {
            // 该方法有期望的主字段
            if (json.has(expectedField)) {
                val field = json.get(expectedField)
                if (field.isJsonArray && field.asJsonArray.size() == 0) {
                    issues.add(ValidationIssue(HealthMonitor.ResponseWarning.EMPTY_DATA_ARRAY, expected = expectedField))
                } else if (field.isJsonObject && field.asJsonObject.size() == 0) {
                    issues.add(ValidationIssue(HealthMonitor.ResponseWarning.EMPTY_DATA_OBJECT, expected = expectedField))
                }
            } else {
                // 主字段不存在，检查回退字段
                val foundFallback = FALLBACK_FIELDS.firstOrNull { it != expectedField && json.has(it) }
                if (foundFallback == null) {
                    issues.add(ValidationIssue(HealthMonitor.ResponseWarning.MISSING_DATA_FIELD, expected = expectedField))
                    DebugLog.w("API缺少期望字段: method=$method, 期望=$expectedField, 实际=${json.keySet()}")
                }
            }
        }

        // 3. URL 方法
        if (method in setOf(MusicApiMethod.SONG_URL_V1, MusicApiMethod.SONG_URL_V1_302, MusicApiMethod.SONG_DOWNLOAD_URL)) {
            if (extractUrl(json).isNullOrEmpty()) {
                issues.add(ValidationIssue(HealthMonitor.ResponseWarning.MISSING_DATA_FIELD, expected = "url"))
            }
        }

        // 4. 响应时间
        if (durationMs > 5000) {
            issues.add(ValidationIssue(HealthMonitor.ResponseWarning.SLOW_RESPONSE, detail = "5000"))
        }

        return issues
    }

    // ======================== 工具方法 ========================

    private fun extractUrl(body: JsonObject): String? {
        // 优先从 redirectUrl 字段获取
        body.get("redirectUrl")?.asString?.let { if (it.startsWith("http")) return it }
        // 递归查找 URL
        return findUrlRecursive(body)
    }

    private fun findUrlRecursive(element: com.google.gson.JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val s = element.asString
            if (s.startsWith("http") && s.length > 12 && !s.contains("null")) return s
        }
        if (element.isJsonObject) {
            // 优先检查常见字段名
            listOf("url", "picUrl", "coverImgUrl", "avatarUrl").forEach { key ->
                element.asJsonObject.get(key)?.asString?.let { v ->
                    if (v.startsWith("http") && v.length > 12 && !v.contains("null")) return v
                }
            }
            for (entry in element.asJsonObject.entrySet()) {
                findUrlRecursive(entry.value)?.let { return it }
            }
        }
        if (element.isJsonArray) {
            for (i in 0 until element.asJsonArray.size()) {
                findUrlRecursive(element.asJsonArray.get(i))?.let { return it }
            }
        }
        return null
    }
}
