package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.api.MusicApiMethod
import cp.player.model.*
import cp.player.provider.ProviderManager
import cp.player.util.CacheManager
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import kotlinx.coroutines.*

/**
 * 用户 ViewModel。
 *
 * 管理用户资料、歌单、推荐、云盘等数据。
 * 所有 API 调用通过 [BaseViewModel.api]（[cp.player.api.MusicApiService]）进行类型安全调用。
 */
class UserViewModel(application: Application) : BaseViewModel(application) {
    var userProfile by mutableStateOf<UserProfile?>(null)
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var recommendedPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var cloudSongs by mutableStateOf<List<Song>>(emptyList())

    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var currentPlaylistMetadata by mutableStateOf<Playlist?>(null)
    var isFetchingMorePlaylistSongs by mutableStateOf(false)
    var hasMorePlaylistSongs by mutableStateOf(true)
    var isPlaylistLoading by mutableStateOf(false)
    var isFetchingUserData by mutableStateOf(false)
    var isAlbumLoading by mutableStateOf(false)
    var isCloudLoading by mutableStateOf(false)
    private var playlistSongsOffset = 0
    private var fetchingPlaylistId: Long? = null

    var likedSongsPlaylistId by mutableLongStateOf(0L)
    var otherUserViewState by mutableStateOf(OtherUserViewState())

    // 专辑详情页数据
    var albumSongs by mutableStateOf<List<Song>>(emptyList())
    var currentAlbumMetadata by mutableStateOf<Playlist?>(null)

    init {
        loadCache()
        // 监听提供商变更：切换时清空旧数据并重新加载
        viewModelScope.launch {
            var isFirst = true
            ProviderManager.currentProviderFlow.collect { provider ->
                if (isFirst) { isFirst = false; return@collect }
                if (provider != null) {
                    android.util.Log.i("UserViewModel", "Provider changed to ${provider.id}, refreshing data")
                    // 清空旧提供商的数据
                    userProfile = null
                    userPlaylists = emptyList()
                    recommendedSongs = emptyList()
                    recommendedPlaylists = emptyList()
                    favoriteSongs = emptyList()
                    cloudSongs = emptyList()
                    likedSongsPlaylistId = 0L
                    // 重新加载新提供商的数据
                    fetchUserData()
                }
            }
        }
    }

    private fun loadCache() {
        CacheManager.load(getApplication(), CacheManager.CacheType.USER_PROFILE, "")?.let {
            userProfile = Gson().fromJson(it, UserProfile::class.java)
        }
        CacheManager.load(getApplication(), CacheManager.CacheType.RECOMMENDED_SONGS, "")?.let {
            recommendedSongs = JsonParser.parseString(it).asJsonArray.mapNotNull { s -> JsonUtils.parseSong(s) }
        }
        CacheManager.load(getApplication(), CacheManager.CacheType.USER_PLAYLISTS, "")?.let {
            userPlaylists = JsonParser.parseString(it).asJsonArray.map { p ->
                val obj = p.asJsonObject
                Playlist(
                    id = obj.get("id").asLong,
                    name = obj.get("name").asString,
                    coverImgUrl = obj.get("coverImgUrl")?.takeIf { !it.isJsonNull }?.asString,
                    trackCount = obj.get("trackCount")?.asInt ?: 0,
                    creatorName = obj.get("creatorName")?.takeIf { !it.isJsonNull }?.asString,
                    creatorUserId = obj.get("creatorUserId")?.asLong ?: 0L,
                    subscribed = obj.get("subscribed")?.asBoolean ?: false
                )
            }
            likedSongsPlaylistId = userPlaylists.find { it.name.contains("喜欢的音乐") }?.id ?: userPlaylists.firstOrNull()?.id ?: 0L
        }
    }

    /**
     * 从 API 响应中提取歌单数组，自动处理多种响应格式：
     * - 直接在根级别: `{"playlist": [...]}`
     * - 嵌套在 data 中: `{"data": {"playlist": [...]}}`
     * - data 本身就是数组: `{"data": [...]}`
     */
    private fun extractPlaylistArray(body: JsonObject): JsonArray? {
        return JsonUtils.findJsonArray(body, "playlist")
            ?: JsonUtils.findJsonArray(body, "list")
            ?: body.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.let { dataObj ->
                dataObj.get("playlist")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: dataObj.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
            }
    }

    /**
     * 获取用户数据（资料、歌单、推荐等）。
     *
     * 使用专用 API 分别获取"创建的歌单"和"收藏的歌单"，
     * 解决旧 `user/playlist` 接口无法正确区分个人创建歌单和收藏歌单的问题。
     */
    fun fetchUserData() {
        if (cookie == null) return
        viewModelScope.launch {
            isFetchingUserData = true
            try {
                coroutineScope {
                    val statusDef = async(Dispatchers.IO) { api.getLoginStatus(cookie) }
                    val recDef = async(Dispatchers.IO) { api.getRecommendedSongs() }
                    val recPlDef = async(Dispatchers.IO) { api.getRecommendedPlaylists() }

                    val statusBody = statusDef.await()
                    val dataElem = statusBody.get("data")
                    val profileElem = if (dataElem != null && dataElem.isJsonObject) dataElem.asJsonObject.get("profile") else statusBody.get("profile")
                    val profileJson = if (profileElem != null && profileElem.isJsonObject) profileElem.asJsonObject else null
                    val uid = profileJson?.get("userId")?.asLong ?: 0L

                    val resolvedUid: Long
                    if (uid != 0L) {
                        resolvedUid = uid
                        userProfile = UserProfile(
                            userId = uid,
                            nickname = profileJson?.get("nickname")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown",
                            avatarUrl = profileJson?.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )
                    } else {
                        val savedAccounts = UserPreferences.getSavedAccounts(getApplication())
                        val matchingAccount = savedAccounts.find { it.cookie == cookie }
                        if (matchingAccount != null) {
                            resolvedUid = matchingAccount.uid
                            userProfile = UserProfile(userId = matchingAccount.uid, nickname = matchingAccount.nickname, avatarUrl = matchingAccount.avatarUrl)
                        } else {
                            resolvedUid = 0L
                            userProfile = UserProfile(userId = System.currentTimeMillis() % 100000, nickname = "Guest", avatarUrl = "")
                        }
                    }

                    if (resolvedUid != 0L) {
                        val plBody = withContext(Dispatchers.IO) { api.getUserPlaylists(resolvedUid) }
                        userPlaylists = extractPlaylistArray(plBody)?.mapNotNull { JsonUtils.parsePlaylist(it) } ?: emptyList()
                        likedSongsPlaylistId = userPlaylists.find { it.name.contains("喜欢的音乐") }?.id ?: userPlaylists.firstOrNull()?.id ?: 0L

                        // 缓存歌单数据
                        CacheManager.save(
                            getApplication(),
                            CacheManager.CacheType.USER_PLAYLISTS,
                            "",
                            Gson().toJson(userPlaylists)
                        )

                        val favBody = withContext(Dispatchers.IO) { api.getLikeList(resolvedUid) }
                        favoriteSongs = favBody.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString } ?: emptyList()
                    }

                    val recBody = recDef.await()
                    val recDataElem = recBody.get("data")
                    val dailySongsElem = if (recDataElem != null && recDataElem.isJsonObject) recDataElem.asJsonObject.get("dailySongs") else recBody.get("dailySongs")
                    recommendedSongs = dailySongsElem?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()

                    val recPlBody = recPlDef.await()
                    val recommendElem = recPlBody.get("recommend") ?: recPlBody.get("data")
                    recommendedPlaylists = recommendElem?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                        JsonUtils.parsePlaylist(it)
                    } ?: emptyList()
                }
            } finally { isFetchingUserData = false }
        }
    }

    /**
     * 设置本地歌单详情（用于每日推荐等虚拟歌单）。
     * 不调用 API，直接使用传入的歌曲列表。
     */
    fun setLocalPlaylistDetail(playlist: Playlist, songs: List<Song>) {
        fetchingPlaylistId = playlist.id
        playlistSongs = emptyList()
        currentPlaylistMetadata = null
        playlistSongsOffset = 0
        hasMorePlaylistSongs = false
        isPlaylistLoading = false

        currentPlaylistMetadata = playlist
        playlistSongs = songs
    }

    /**
     * 将歌单元数据和歌曲列表序列化为 JSON 字符串（用于缓存）。
     */
    private fun playlistCacheToJson(playlist: Playlist?, songs: List<Song>): String {
        val root = JsonObject()
        if (playlist != null) {
            val plObj = JsonObject()
            plObj.addProperty("id", playlist.id)
            plObj.addProperty("name", playlist.name)
            plObj.addProperty("coverImgUrl", playlist.coverImgUrl)
            plObj.addProperty("trackCount", playlist.trackCount)
            plObj.addProperty("creatorName", playlist.creatorName)
            plObj.addProperty("description", playlist.description)
            root.add("playlist", plObj)
        }
        val arr = JsonArray()
        for (s in songs) {
            val obj = JsonObject()
            obj.addProperty("id", s.id)
            obj.addProperty("name", s.name)
            obj.addProperty("artist", s.artist)
            obj.addProperty("artistId", s.artistId)
            obj.addProperty("album", s.album)
            obj.addProperty("albumArtUrl", s.albumArtUrl)
            obj.addProperty("durationMs", s.durationMs)
            arr.add(obj)
        }
        root.add("songs", arr)
        return Gson().toJson(root)
    }

    /**
     * 从缓存 JSON 中解析歌单元数据。
     */
    private fun parseCachedPlaylist(json: String): Playlist? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val plObj = obj.get("playlist")?.asJsonObject ?: return null
            Playlist(
                id = plObj.get("id")?.asLong ?: 0L,
                name = plObj.get("name")?.asString ?: "",
                coverImgUrl = plObj.get("coverImgUrl")?.takeIf { !it.isJsonNull }?.asString,
                trackCount = plObj.get("trackCount")?.asInt ?: 0,
                creatorName = plObj.get("creatorName")?.takeIf { !it.isJsonNull }?.asString,
                description = plObj.get("description")?.takeIf { !it.isJsonNull }?.asString
            )
        } catch (_: Exception) { null }
    }

    /**
     * 从缓存 JSON 中解析歌曲列表。
     */
    private fun parseCachedSongs(json: String): List<Song> {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val arr = obj.get("songs")?.asJsonArray
                ?: // 兼容旧格式（纯数组）
                JsonParser.parseString(json).asJsonArray
            arr.mapNotNull { JsonUtils.parseSong(it) }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 获取歌单歌曲。使用类型安全 API。
     * 非 loadMore 模式下优先使用缓存，后台刷新后更新缓存。
     */
    fun fetchPlaylistSongs(playlistId: Long, isLoadMore: Boolean = false) {
        if (isLoadMore) {
            if (isFetchingMorePlaylistSongs || !hasMorePlaylistSongs) return
            isFetchingMorePlaylistSongs = true
        } else {
            // 防止重复获取同一个歌单
            if (fetchingPlaylistId == playlistId) return
            fetchingPlaylistId = playlistId

            currentPlaylistMetadata = null
            playlistSongsOffset = 0
            hasMorePlaylistSongs = true

            // 有缓存则秒显示，后台静默刷新；无缓存则显示 loading
            val cachedJson = CacheManager.load(getApplication(), CacheManager.CacheType.PLAYLIST_DETAIL, playlistId.toString())
            if (cachedJson != null) {
                try {
                    val cachedSongs = parseCachedSongs(cachedJson)
                    if (cachedSongs.isNotEmpty()) {
                        playlistSongs = cachedSongs
                        // 优先从缓存恢复元数据，其次从用户歌单列表查找
                        currentPlaylistMetadata = parseCachedPlaylist(cachedJson)
                            ?: userPlaylists.find { it.id == playlistId }
                        // 有缓存：后台刷新但不显示 loading
                        refreshPlaylistInBackground(playlistId)
                        return
                    }
                } catch (_: Exception) {}
            }
            // 无缓存：显示 loading
            playlistSongs = emptyList()
            isPlaylistLoading = true
        }

        viewModelScope.launch {
            try {
                coroutineScope {
                    // 并行发起详情和歌曲请求，不串行等待
                    val limit = 100
                    val detailDef = if (!isLoadMore) async(Dispatchers.IO) { api.getPlaylistDetail(playlistId) } else null
                    val tracksDef = async(Dispatchers.IO) { api.getPlaylistTracks(playlistId, limit = limit, offset = playlistSongsOffset) }

                    // 处理详情（与歌曲请求并行进行）
                    if (!isLoadMore && detailDef != null) {
                        val detailBody = detailDef.await()
                        val plObj = detailBody.get("playlist")?.asJsonObject
                        if (plObj != null) {
                            currentPlaylistMetadata = JsonUtils.parsePlaylist(plObj)
                        }
                    }

                    val tracksBody = tracksDef.await()
                    val songs = tracksBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()

                    if (songs.size < limit) {
                        hasMorePlaylistSongs = false
                    }
                    if (songs.isNotEmpty()) {
                        playlistSongsOffset += songs.size
                        playlistSongs = if (isLoadMore) playlistSongs + songs else songs
                    } else if (!isLoadMore) {
                        playlistSongs = emptyList()
                    }

                    // 缓存歌单元数据和歌曲（仅首次加载且有数据时）
                    if (!isLoadMore && playlistSongs.isNotEmpty()) {
                        CacheManager.save(
                            getApplication(),
                            CacheManager.CacheType.PLAYLIST_DETAIL,
                            playlistId.toString(),
                            playlistCacheToJson(currentPlaylistMetadata, playlistSongs)
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error fetching playlist songs", e)
            } finally {
                if (isLoadMore) {
                    isFetchingMorePlaylistSongs = false
                } else {
                    isPlaylistLoading = false
                    fetchingPlaylistId = null
                }
            }
        }
    }

    /**
     * 后台静默刷新歌单歌曲（有缓存时使用）。
     * 不改变 isLoading 状态，刷新完成后更新数据和缓存。
     */
    private fun refreshPlaylistInBackground(playlistId: Long) {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val detailDef = async(Dispatchers.IO) { api.getPlaylistDetail(playlistId) }
                    val detailBody = detailDef.await()
                    val plObj = detailBody.get("playlist")?.asJsonObject
                    if (plObj != null) {
                        withContext(Dispatchers.Main) {
                            currentPlaylistMetadata = JsonUtils.parsePlaylist(plObj)
                        }
                    }

                    val allSongs = mutableListOf<Song>()
                    var offset = 0
                    val limit = 100
                    while (true) {
                        val tracksBody = withContext(Dispatchers.IO) { api.getPlaylistTracks(playlistId, limit = limit, offset = offset) }
                        val songs = tracksBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                        if (songs.isEmpty()) break
                        allSongs.addAll(songs)
                        if (songs.size < limit) break
                        offset += songs.size
                    }

                    if (allSongs.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            playlistSongs = allSongs
                            playlistSongsOffset = allSongs.size
                            hasMorePlaylistSongs = false
                        }
                        CacheManager.save(
                            getApplication(),
                            CacheManager.CacheType.PLAYLIST_DETAIL,
                            playlistId.toString(),
                            playlistCacheToJson(currentPlaylistMetadata, allSongs)
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Background refresh failed", e)
            } finally {
                // 只在仍是同一歌单时清除标志，避免清除后续歌单的获取状态
                withContext(Dispatchers.Main) {
                    if (fetchingPlaylistId == playlistId) fetchingPlaylistId = null
                }
            }
        }
    }

    /**
     * 获取专辑详情及歌曲。使用类型安全 API。
     */
    fun fetchAlbumSongs(albumId: Long) {
        albumSongs = emptyList()
        currentAlbumMetadata = null
        viewModelScope.launch {
            isAlbumLoading = true
            try {
                val body = withContext(Dispatchers.IO) { api.getAlbumDetail(albumId) }
                val albumObj = body.get("album")?.asJsonObject
                if (albumObj != null) {
                    currentAlbumMetadata = Playlist(
                        id = albumObj.get("id")?.asLong ?: albumId,
                        name = albumObj.get("name")?.asString ?: "",
                        coverImgUrl = albumObj.get("picUrl")?.asString,
                        trackCount = albumObj.get("size")?.asInt ?: 0,
                        creatorName = albumObj.get("artist")?.asJsonObject?.get("name")?.asString,
                        description = albumObj.get("description")?.asString
                    )
                }
                albumSongs = body.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error fetching album songs", e)
            } finally {
                isAlbumLoading = false
            }
        }
    }

    /**
     * 挂起函数版本：获取歌单歌曲。
     */
    suspend fun getPlaylistSongs(playlistId: Long): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val tracksBody = api.getPlaylistTracks(playlistId)
                tracksBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 获取云盘歌曲。使用类型安全 API。
     *
     * 云盘 API (`user/cloud`) 返回的数据结构可能是：
     * - `{ "data": [ {songId, songName, ...}, ... ] }`
     * - 或嵌套格式: `{ "data": { "data": [...], "count": N, ... } }`
     *
     * 本方法兼容两种格式。
     */
    fun fetchCloudSongs() {
        viewModelScope.launch {
            isCloudLoading = true
            try {
                if (cookie == null) {
                    isCloudLoading = false
                    return@launch
                }

                val allSongs = mutableListOf<Song>()
                val allElements = mutableListOf<com.google.gson.JsonElement>()
                val seenIds = mutableSetOf<String>()
                var offset = 0
                val limit = 200

                withContext(Dispatchers.IO) {
                    while (true) {
                        val body = api.getUserCloud(limit = limit, offset = offset)
                        val dataElem = body.get("data")

                        val songs = when {
                            dataElem == null || dataElem.isJsonNull -> emptyList()
                            // 直接是数组格式: {"data": [...]}
                            dataElem.isJsonArray -> dataElem.asJsonArray.mapNotNull { JsonUtils.parseSong(it) }
                            // 嵌套格式: {"data": {"data": [...], ...}}
                            dataElem.isJsonObject -> {
                                val innerData = dataElem.asJsonObject.get("data")
                                if (innerData != null && innerData.isJsonArray) {
                                    innerData.asJsonArray.mapNotNull { JsonUtils.parseSong(it) }
                                } else {
                                    // 尝试从整个 data 对象中查找数组
                                    JsonUtils.findJsonArray(dataElem, "data")?.mapNotNull { JsonUtils.parseSong(it) }
                                        ?: emptyList()
                                }
                            }
                            else -> emptyList()
                        }

                        val elements = when {
                            dataElem == null || dataElem.isJsonNull -> emptyList()
                            dataElem.isJsonArray -> dataElem.asJsonArray.toList()
                            dataElem.isJsonObject -> {
                                val innerData = dataElem.asJsonObject.get("data")
                                if (innerData != null && innerData.isJsonArray) {
                                    innerData.asJsonArray.toList()
                                } else {
                                    cp.player.util.JsonUtils.findJsonArray(dataElem, "data")?.toList() ?: emptyList()
                                }
                            }
                            else -> emptyList()
                        }

                        if (songs.isEmpty()) break

                        var addedNew = false
                        for (song in songs) {
                            if (seenIds.add(song.id)) {
                                allSongs.add(song)
                                addedNew = true
                            }
                        }

                        allElements.addAll(elements)

                        // 某些 provider 可能不支持 offset 返回重复数据，或者已经返回所有数据
                        if (!addedNew || songs.size < limit) break

                        offset += limit
                    }
                }

                cloudSongs = allSongs

                // Extract simpleSong.id and bind it
                withContext(Dispatchers.IO) {
                    for (element in allElements) {
                        try {
                            if (!element.isJsonObject) continue
                            val obj = element.asJsonObject
                            val songId = obj.get("songId")?.asString ?: obj.get("id")?.asString ?: continue
                            val simpleSong = obj.get("simpleSong")?.takeIf { it.isJsonObject }?.asJsonObject
                            val realId = simpleSong?.get("id")?.asString

                            if (!realId.isNullOrEmpty() && realId != "0" && realId != songId) {
                                val cloudSongId = "cloud_$songId"
                                if (cp.player.manager.LocalMusicManager.getBinding(cloudSongId) == null) {
                                    val realSong = cp.player.util.JsonUtils.parseSong(simpleSong)
                                    if (realSong != null) {
                                        withContext(Dispatchers.Main) {
                                            cp.player.manager.LocalMusicManager.bind(getApplication(), cloudSongId, realSong)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UserViewModel", "Error binding simpleSong id", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error fetching cloud songs", e)
            } finally {
                isCloudLoading = false
            }
        }
    }

    /**
     * 取消收藏歌单。使用类型安全 API。
     */
    fun unsubscribePlaylist(pid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.subscribePlaylist(pid, 2) // t=2 = unsubscribe
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    userPlaylists = userPlaylists.filter { it.id != pid }
                }
            }
        }
    }

    /**
     * 切换喜欢状态。使用类型安全 API。
     */
    fun toggleLike(songId: String, like: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.likeSong(songId, like)
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    favoriteSongs = if (like) favoriteSongs + songId else favoriteSongs - songId
                }
            }
        }
    }

    /**
     * 标记不喜欢推荐歌曲。使用类型安全 API。
     */
    fun dislikeSong(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.dislikeSong(songId)
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    recommendedSongs = recommendedSongs.filter { it.id != songId }
                }
            }
        }
    }

    /**
     * 添加歌曲到歌单。使用类型安全 API。
     */
    fun addSongsToPlaylist(pid: Long, ids: List<String>, @Suppress("UNUSED_PARAMETER") cookie: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                api.addTracksToPlaylist(pid, ids)
            }
            fetchPlaylistSongs(pid)
        }
    }

    /**
     * 从歌单删除歌曲。使用类型安全 API。
     */
    fun removeSongsFromPlaylist(pid: Long, ids: List<String>, @Suppress("UNUSED_PARAMETER") cookie: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                api.removeTracksFromPlaylist(pid, ids)
            }
            fetchPlaylistSongs(pid)
        }
    }

    /**
     * 创建歌单。使用类型安全 API。
     */
    fun createPlaylist(name: String, privacy: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.createPlaylist(name, privacy)
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    fetchUserData()
                }
            }
        }
    }

    /**
     * 删除歌单。使用类型安全 API。
     */
    fun deletePlaylist(pid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = api.deletePlaylist(pid)
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    userPlaylists = userPlaylists.filter { it.id != pid }
                }
            }
        }
    }

    /**
     * 获取其他用户/歌手资料。使用类型安全 API。
     */
    fun fetchOtherUserProfile(uid: Long) {
        if (otherUserViewState.uid == uid && otherUserViewState.profile != null && !otherUserViewState.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    otherUserViewState = OtherUserViewState(uid = uid, isLoading = true)
                }

                coroutineScope {
                    val userDef = async { try { api.getUserDetail(uid) } catch (e: Exception) { null } }
                    val artistDef = async { try { api.getArtistDetail(uid) } catch (e: Exception) { null } }

                    val userBody = userDef.await()
                    val artistBody = artistDef.await()

                    var profile: UserProfile? = null
                    var playlists = emptyList<Playlist>()
                    var songs = emptyList<Song>()
                    var albums = emptyList<Playlist>()
                    var isArtist = false

                    val profileJson = userBody?.get("profile")?.asJsonObject
                    if (profileJson != null) {
                        profile = UserProfile(
                            userId = uid,
                            nickname = profileJson.get("nickname")?.asString ?: "Unknown",
                            avatarUrl = profileJson.get("avatarUrl")?.asString ?: "",
                            signature = profileJson.get("signature")?.asString,
                            follows = profileJson.get("follows")?.asInt ?: 0,
                            followeds = profileJson.get("followeds")?.asInt ?: 0
                        )
                        // 使用 user/playlist 获取该用户的全部歌单
                        val plBody = api.getUserPlaylists(uid)
                        playlists = extractPlaylistArray(plBody)?.mapNotNull {
                            JsonUtils.parsePlaylist(it)
                        } ?: emptyList()
                    }

                    val artistData = artistBody?.get("data")?.asJsonObject
                    if (artistData != null) {
                        isArtist = true
                        val artist = artistData.get("artist")?.asJsonObject
                        if (profile == null) {
                            profile = UserProfile(
                                userId = uid,
                                nickname = artist?.get("name")?.asString ?: "Unknown",
                                avatarUrl = artist?.get("cover")?.asString ?: artist?.get("picUrl")?.asString ?: "",
                                signature = artist?.get("briefDesc")?.asString,
                                follows = 0,
                                followeds = artistData.get("user")?.asJsonObject?.get("followeds")?.asInt ?: 0
                            )
                        }

                        val songsBody = api.getArtistSongs(uid)
                        val rawSongs = songsBody.get("songs")?.asJsonArray
                        if (rawSongs != null && rawSongs.size() > 0) {
                            val ids = rawSongs.map { it.asJsonObject.get("id").asString }
                            val detailBody = api.getSongDetail(ids)
                            songs = detailBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                        } else {
                            songs = emptyList()
                        }

                        val albumBody = api.getArtistAlbums(uid)
                        albums = albumBody.get("hotAlbums")?.asJsonArray?.mapNotNull {
                            JsonUtils.parsePlaylist(it)
                        } ?: emptyList()
                    }

                    withContext(Dispatchers.Main) {
                        otherUserViewState = OtherUserViewState(
                            uid = uid,
                            profile = profile,
                            playlists = playlists,
                            songs = songs,
                            albums = albums,
                            isArtist = isArtist,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error fetching other user profile", e)
                withContext(Dispatchers.Main) {
                    otherUserViewState = otherUserViewState.copy(isLoading = false)
                    if (otherUserViewState.profile == null) {
                        otherUserViewState = otherUserViewState.copy(
                            profile = UserProfile(uid, "Unknown User", "")
                        )
                    }
                }
            }
        }
    }
}
