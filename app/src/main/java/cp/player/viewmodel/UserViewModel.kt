package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import cp.player.model.*
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import kotlinx.coroutines.*

class UserViewModel(application: Application) : BaseViewModel(application) {
    var userProfile by mutableStateOf<UserProfile?>(null)
    var userPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var favoriteSongs by mutableStateOf<List<String>>(emptyList())
    var recommendedSongs by mutableStateOf<List<Song>>(emptyList())
    var recommendedPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var cloudSongs by mutableStateOf<List<Song>>(emptyList())

    var playlistSongs by mutableStateOf<List<Song>>(emptyList())
    var currentPlaylistMetadata by mutableStateOf<Playlist?>(null)

    var likedSongsPlaylistId by mutableLongStateOf(0L)
    var otherUserViewState by mutableStateOf(OtherUserViewState())

    init {
        loadCache()
    }

    private fun loadCache() {
        UserPreferences.getUserProfileCache(getApplication())?.let {
            userProfile = Gson().fromJson(it, UserProfile::class.java)
        }
        UserPreferences.getRecommendedSongsCache(getApplication())?.let {
            recommendedSongs = JsonParser.parseString(it).asJsonArray.mapNotNull { s -> JsonUtils.parseSong(s) }
        }
        UserPreferences.getUserPlaylistsCache(getApplication())?.let {
            userPlaylists = JsonParser.parseString(it).asJsonArray.map { p ->
                val obj = p.asJsonObject
                Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt, null, null)
            }
            likedSongsPlaylistId = userPlaylists.find { it.name.contains("喜欢的音乐") }?.id ?: userPlaylists.firstOrNull()?.id ?: 0L
        }
    }

    fun fetchUserData() {
        if (cookie == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                coroutineScope {
                    val statusDef = async(Dispatchers.IO) { callApi("login/status") }
                    val recDef = async(Dispatchers.IO) { callApi("recommend/songs") }
                    val recPlDef = async(Dispatchers.IO) { callApi("recommend/resource") }

                    val statusBody = statusDef.await()
                    val dataElem = statusBody.get("data")
                    val profileElem = if (dataElem != null && dataElem.isJsonObject) dataElem.asJsonObject.get("profile") else statusBody.get("profile")
                    val profileJson = if (profileElem != null && profileElem.isJsonObject) profileElem.asJsonObject else null
                    val uid = profileJson?.get("userId")?.asLong ?: 0L

                    if (uid != 0L) {
                        userProfile = UserProfile(
                            userId = uid,
                            nickname = profileJson?.get("nickname")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown",
                            avatarUrl = profileJson?.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )

                        val plBody = withContext(Dispatchers.IO) { callApi("user/playlist", mapOf("uid" to uid.toString())) }
                        userPlaylists = plBody.get("playlist")?.takeIf { it.isJsonArray }?.asJsonArray?.map {
                            val obj = it.asJsonObject
                            Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt, null, null)
                        } ?: emptyList()

                        val favBody = withContext(Dispatchers.IO) { callApi("likelist", mapOf("uid" to uid.toString())) }
                        favoriteSongs = favBody.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString } ?: emptyList()
                    } else {
                        // Fallback if no valid profile (e.g. anonymous login that returned 200)
                        val savedAccounts = UserPreferences.getSavedAccounts(getApplication())
                        val matchingAccount = savedAccounts.find { it.cookie == cookie }
                        userProfile = if (matchingAccount != null) {
                            UserProfile(userId = matchingAccount.uid, nickname = matchingAccount.nickname, avatarUrl = matchingAccount.avatarUrl)
                        } else {
                            UserProfile(userId = System.currentTimeMillis() % 100000, nickname = "Guest", avatarUrl = "")
                        }
                    }

                    val recBody = recDef.await()
                    val recDataElem = recBody.get("data")
                    val dailySongsElem = if (recDataElem != null && recDataElem.isJsonObject) recDataElem.asJsonObject.get("dailySongs") else recBody.get("dailySongs")
                    recommendedSongs = dailySongsElem?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()

                    val recPlBody = recPlDef.await()
                    val recommendElem = recPlBody.get("recommend") ?: recPlBody.get("data")
                    recommendedPlaylists = recommendElem?.takeIf { it.isJsonArray }?.asJsonArray?.map {
                        val obj = it.asJsonObject
                        Playlist(
                            obj.get("id").asLong,
                            obj.get("name").asString,
                            if (obj.has("picUrl") && !obj.get("picUrl").isJsonNull) obj.get("picUrl").asString else if (obj.has("coverImgUrl") && !obj.get("coverImgUrl").isJsonNull) obj.get("coverImgUrl").asString else "",
                            if (obj.has("trackCount") && !obj.get("trackCount").isJsonNull) obj.get("trackCount").asInt else 0,
                            null,
                            null
                        )
                    } ?: emptyList()
                }
            } finally { isLoading = false }
        }
    }

    fun fetchPlaylistSongs(playlistId: Long) {
        playlistSongs = emptyList()
        currentPlaylistMetadata = null

        viewModelScope.launch {
            isLoading = true
            try {
                coroutineScope {
                    val detailDef = async(Dispatchers.IO) { callApi("playlist/detail", mapOf("id" to playlistId.toString())) }
                    val tracksDef = async(Dispatchers.IO) { callApi("playlist/track/all", mapOf("id" to playlistId.toString(), "limit" to "1000", "offset" to "0")) }

                    val detailBody = detailDef.await()
                    val plObj = detailBody.get("playlist")?.asJsonObject
                    if (plObj != null) {
                        currentPlaylistMetadata = Playlist(
                            plObj.get("id").asLong,
                            plObj.get("name").asString,
                            plObj.get("coverImgUrl").asString,
                            plObj.get("trackCount").asInt,
                            null,
                            null
                        )
                    }

                    val tracksBody = tracksDef.await()
                    val songs = tracksBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()

                    // Update songs after both have finished for atomicity
                    playlistSongs = songs
                }
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error fetching playlist songs", e)
            } finally { isLoading = false }
        }
    }

    suspend fun getPlaylistSongs(playlistId: Long): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val tracksBody = callApi("playlist/track/all", mapOf("id" to playlistId.toString(), "limit" to "1000", "offset" to "0"))
                tracksBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun fetchCloudSongs() {
        viewModelScope.launch {
            isLoading = true
            try {
                if (cookie == null) {
                    isLoading = false
                    return@launch
                }

                val body = withContext(Dispatchers.IO) { callApi("user/cloud", mapOf("limit" to "100")) }
                val songs = body.get("data")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                cloudSongs = songs
            } catch (e: Exception) {
                // Ignore error but ensure isLoading is false
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleLike(songId: String, like: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("like", mapOf("id" to songId, "like" to like.toString()))
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    favoriteSongs = if (like) favoriteSongs + songId else favoriteSongs - songId
                }
            }
        }
    }

    fun dislikeSong(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("recommend/songs/dislike", mapOf("id" to songId))
            if (body.get("code")?.asInt == 200) {
                withContext(Dispatchers.Main) {
                    recommendedSongs = recommendedSongs.filter { it.id != songId }
                }
            }
        }
    }

    fun addSongsToPlaylist(pid: Long, ids: List<String>, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                callApi("playlist/tracks", mapOf("op" to "add", "pid" to pid.toString(), "tracks" to ids.joinToString(",")))
            }
            fetchPlaylistSongs(pid)
        }
    }

    fun removeSongsFromPlaylist(pid: Long, ids: List<String>, cookie: String?) {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                callApi("playlist/tracks", mapOf("op" to "del", "pid" to pid.toString(), "tracks" to ids.joinToString(",")))
            }
            fetchPlaylistSongs(pid)
        }
    }

    fun createPlaylist(name: String, privacy: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("playlist/create", mapOf("name" to name, "privacy" to privacy.toString()))
            if (body.get("code")?.asInt == 200) {
                // Fetch the updated user data to get the new playlist
                fetchUserData()
            }
        }
    }

    fun deletePlaylist(pid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val body = callApi("playlist/delete", mapOf("id" to pid.toString()))
            if (body.get("code")?.asInt == 200) {
                // Remove from local state immediately for fast UI feedback
                withContext(Dispatchers.Main) {
                    userPlaylists = userPlaylists.filter { it.id != pid }
                }
            }
        }
    }

    fun fetchOtherUserProfile(uid: Long) {
        if (otherUserViewState.uid == uid && otherUserViewState.profile != null && !otherUserViewState.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    otherUserViewState = OtherUserViewState(uid = uid, isLoading = true)
                }

                coroutineScope {
                    val userDef = async { try { callApi("user/detail", mapOf("uid" to uid.toString())) } catch (e: Exception) { null } }
                    val artistDef = async { try { callApi("artist/detail", mapOf("id" to uid.toString())) } catch (e: Exception) { null } }

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
                        val plBody = callApi("user/playlist", mapOf("uid" to uid.toString()))
                        playlists = plBody.get("playlist")?.asJsonArray?.map {
                            val obj = it.asJsonObject
                            Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt, null, null)
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

                        val songsBody = callApi("artist/songs", mapOf("id" to uid.toString(), "limit" to "50"))
                        val rawSongs = songsBody.get("songs")?.asJsonArray
                        if (rawSongs != null && rawSongs.size() > 0) {
                            // Fetch full details if picUrl is likely missing (common in artist/songs)
                            val ids = rawSongs.map { it.asJsonObject.get("id").asString }
                            val detailBody = callApi("song/detail", mapOf("ids" to ids.joinToString(",")))
                            songs = detailBody.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                        } else {
                            songs = emptyList()
                        }

                        val albumBody = callApi("artist/album", mapOf("id" to uid.toString(), "limit" to "20"))
                        albums = albumBody.get("hotAlbums")?.asJsonArray?.map {
                            val obj = it.asJsonObject
                            Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("picUrl").asString, obj.get("size").asInt, null, null)
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
                        // Fallback to minimal profile to stop the loading spinner
                        otherUserViewState = otherUserViewState.copy(
                            profile = UserProfile(uid, "Unknown User", "")
                        )
                    }
                }
            }
        }
    }
}
