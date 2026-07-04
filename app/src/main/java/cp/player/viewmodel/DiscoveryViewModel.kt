package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.provider.ProviderManager
import cp.player.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 榜单条目数据。
 */
data class ToplistEntry(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val description: String? = null,
    val playCount: Long = 0L,
    val updateFrequency: String? = null
)

/**
 * 发现页 ViewModel。
 *
 * 管理排行榜、推荐歌单、新歌速递、精品歌单等音乐发现数据。
 */
class DiscoveryViewModel(application: Application) : BaseViewModel(application) {

    // ======================== 状态 ========================

    /** 所有榜单列表 */
    var toplists by mutableStateOf<List<ToplistEntry>>(emptyList())

    /** 推荐歌单（无需登录） */
    var personalizedPlaylists by mutableStateOf<List<Playlist>>(emptyList())

    /** 推荐新歌 */
    var personalizedNewSongs by mutableStateOf<List<Song>>(emptyList())

    /** 精品歌单 */
    var highqualityPlaylists by mutableStateOf<List<Playlist>>(emptyList())

    /** 新歌速递 */
    var topSongs by mutableStateOf<List<Song>>(emptyList())

    /** 当前查看的榜单详情歌曲 */
    var rankingDetailSongs by mutableStateOf<List<Song>>(emptyList())

    /** 当前查看的榜单元数据 */
    var rankingDetailMetadata by mutableStateOf<Playlist?>(null)

    /** 是否正在加载发现页数据 */
    var isDiscoveryLoading by mutableStateOf(false)

    /** 是否正在加载榜单详情 */
    var isRankingDetailLoading by mutableStateOf(false)

    /** 是否正在加载新歌速递 */
    var isTopSongsLoading by mutableStateOf(false)

    init {
        // 监听提供商变更
        viewModelScope.launch {
            var isFirst = true
            ProviderManager.currentProviderFlow.collect { provider ->
                if (isFirst) { isFirst = false; return@collect }
                if (provider != null) {
                    // 清空旧数据
                    toplists = emptyList()
                    personalizedPlaylists = emptyList()
                    personalizedNewSongs = emptyList()
                    highqualityPlaylists = emptyList()
                    topSongs = emptyList()
                }
            }
        }
    }

    // ======================== 发现页数据 ========================

    /**
     * 并行获取发现页所有数据：推荐歌单 + 推荐新歌 + 精品歌单。
     */
    fun fetchDiscoveryData() {
        if (isDiscoveryLoading) return
        viewModelScope.launch {
            isDiscoveryLoading = true
            try {
                coroutineScope {
                    val recDef = async(Dispatchers.IO) { api.getPersonalizedPlaylists(15) }
                    val newSongDef = async(Dispatchers.IO) { api.getPersonalizedNewSongs(10) }
                    val hqDef = async(Dispatchers.IO) { api.getHighqualityPlaylists(limit = 15) }

                    // 推荐歌单
                    try {
                        val recBody = recDef.await()
                        val recArr = recBody.get("result")?.asJsonArray
                        personalizedPlaylists = recArr?.mapNotNull { JsonUtils.parsePlaylist(it) } ?: emptyList()
                    } catch (e: Exception) {
                        android.util.Log.e("DiscoveryVM", "Failed to fetch personalized playlists", e)
                    }

                    // 推荐新歌
                    try {
                        val newSongBody = newSongDef.await()
                        val newSongArr = newSongBody.get("result")?.asJsonArray
                        personalizedNewSongs = newSongArr?.mapNotNull { parseNewSong(it) } ?: emptyList()
                    } catch (e: Exception) {
                        android.util.Log.e("DiscoveryVM", "Failed to fetch new songs", e)
                    }

                    // 精品歌单
                    try {
                        val hqBody = hqDef.await()
                        val hqArr = hqBody.get("playlists")?.asJsonArray
                        highqualityPlaylists = hqArr?.mapNotNull { JsonUtils.parsePlaylist(it) } ?: emptyList()
                    } catch (e: Exception) {
                        android.util.Log.e("DiscoveryVM", "Failed to fetch highquality playlists", e)
                    }
                }
            } finally {
                isDiscoveryLoading = false
            }
        }
    }

    // ======================== 排行榜列表 ========================

    /**
     * 获取所有榜单列表。
     */
    fun fetchToplist() {
        if (toplists.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { api.getToplistDetail() }
                val listArr = body.get("list")?.asJsonArray
                toplists = listArr?.mapNotNull { parseToplistEntry(it) } ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("DiscoveryVM", "Failed to fetch toplist", e)
            }
        }
    }

    // ======================== 榜单详情 ========================

    /**
     * 获取榜单详情歌曲列表（复用 playlist/detail API）。
     */
    fun fetchRankingDetail(playlistId: Long) {
        viewModelScope.launch {
            isRankingDetailLoading = true
            rankingDetailSongs = emptyList()
            rankingDetailMetadata = null
            try {
                coroutineScope {
                    val detailDef = async(Dispatchers.IO) { api.getPlaylistDetail(playlistId) }
                    val tracksDef = async(Dispatchers.IO) { api.getPlaylistTracks(playlistId) }

                    val detailBody = detailDef.await()
                    val playlistObj = detailBody.get("playlist")?.asJsonObject
                    if (playlistObj != null) {
                        rankingDetailMetadata = JsonUtils.parsePlaylist(playlistObj)
                    }

                    val tracksBody = tracksDef.await()
                    val songsArr = tracksBody.get("songs")?.asJsonArray
                    rankingDetailSongs = songsArr?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("DiscoveryVM", "Failed to fetch ranking detail", e)
            } finally {
                isRankingDetailLoading = false
            }
        }
    }

    // ======================== 新歌速递 ========================

    /**
     * 获取新歌速递。
     * @param type 地区: 0=全部, 7=华语, 96=欧美, 8=日本, 16=韩国
     */
    fun fetchTopSongs(type: Int = 0) {
        viewModelScope.launch {
            isTopSongsLoading = true
            try {
                val body = withContext(Dispatchers.IO) { api.getTopSongs(type) }
                val dataArr = body.get("data")?.asJsonArray
                topSongs = dataArr?.mapNotNull { el ->
                    try {
                        val obj = el.asJsonObject
                        // top_song 返回格式有 songInfo 嵌套
                        val songObj = obj.getAsJsonObject("songInfo") ?: obj
                        JsonUtils.parseSong(songObj)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("DiscoveryVM", "Failed to fetch top songs", e)
            } finally {
                isTopSongsLoading = false
            }
        }
    }

    // ======================== 解析工具 ========================

    private fun parseToplistEntry(el: com.google.gson.JsonElement): ToplistEntry? {
        return try {
            val obj = el.asJsonObject
            ToplistEntry(
                id = obj.get("id")?.asLong ?: return null,
                name = obj.get("name")?.asString ?: "",
                coverImgUrl = obj.get("coverImgUrl")?.asString
                    ?: obj.get("picUrl")?.asString,
                description = obj.get("description")?.asString,
                playCount = obj.get("playCount")?.asLong ?: 0L,
                updateFrequency = obj.get("updateFrequency")?.asString
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析推荐新歌（格式与标准 Song 不同，需要从歌曲对象中提取）。
     */
    private fun parseNewSong(el: com.google.gson.JsonElement): Song? {
        return try {
            val obj = el.asJsonObject
            // personalized_newsong 返回格式: { id, name, song: { ... }, alg }
            val songObj = obj.getAsJsonObject("song") ?: obj
            JsonUtils.parseSong(songObj)
        } catch (e: Exception) {
            null
        }
    }
}
