package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import cp.player.api.MusicApiMethod
import cp.player.model.*
import cp.player.provider.ProviderManager
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import kotlinx.coroutines.*

class SearchViewModel(application: Application) : BaseViewModel(application) {
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searchPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var searchArtists by mutableStateOf<List<Artist>>(emptyList())
    var hotSearches by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var searchHistory by mutableStateOf(UserPreferences.getSearchHistory(application))
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var searchType by mutableIntStateOf(1)
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)

    init {
        fetchHotSearches()
        // 监听提供商变更：切换时重置搜索状态
        viewModelScope.launch {
            var isFirst = true
            ProviderManager.currentProviderFlow.collect { provider ->
                if (isFirst) { isFirst = false; return@collect }
                if (provider != null) {
                    android.util.Log.i("SearchViewModel", "Provider changed to ${provider.id}, resetting search state")
                    searchResults = emptyList()
                    searchPlaylists = emptyList()
                    searchArtists = emptyList()
                    searchSuggestions = emptyList()
                    searchQuery = ""
                    searchHistory = UserPreferences.getSearchHistory(getApplication())
                    fetchHotSearches()
                }
            }
        }
    }

    fun search(keywords: String, type: Int = 1) {
        searchType = type
        searchQuery = keywords
        if (keywords.isBlank()) return

        val newHistory = (listOf(keywords) + searchHistory.filter { it != keywords }).take(10)
        searchHistory = newHistory
        UserPreferences.saveSearchHistory(getApplication(), newHistory)

        viewModelScope.launch {
            isSearching = true
            try {
                val body = withContext(Dispatchers.IO) {
                    api.search(keywords, type)
                }
                val resultObj = body.get("result")?.asJsonObject
                when (type) {
                    MusicApiMethod.SEARCH_TYPE_SONG -> {
                        searchResults = resultObj?.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                        searchPlaylists = emptyList()
                        searchArtists = emptyList()
                    }
                    MusicApiMethod.SEARCH_TYPE_ALBUM -> {
                        // 搜索专辑：将专辑映射为 Playlist 以复用 PlaylistItem 显示
                        searchPlaylists = resultObj?.get("albums")?.asJsonArray?.mapNotNull { element ->
                            try {
                                val obj = element.asJsonObject
                                val artists = obj.get("artists")?.asJsonArray
                                val artistName = artists?.get(0)?.asJsonObject?.get("name")?.asString
                                cp.player.model.Playlist(
                                    id = obj.get("id")?.asLong ?: return@mapNotNull null,
                                    name = obj.get("name")?.asString ?: "",
                                    coverImgUrl = obj.get("picUrl")?.asString,
                                    trackCount = obj.get("size")?.asInt ?: 0,
                                    creatorName = artistName,
                                    description = obj.get("description")?.asString
                                )
                            } catch (e: Exception) { null }
                        } ?: emptyList()
                        searchResults = emptyList()
                        searchArtists = emptyList()
                    }
                    MusicApiMethod.SEARCH_TYPE_ARTIST -> {
                        // 搜索歌手：解析为独立的 Artist 数据类
                        searchArtists = resultObj?.get("artists")?.asJsonArray?.mapNotNull { element ->
                            try {
                                val obj = element.asJsonObject
                                Artist(
                                    id = obj.get("id")?.asLong ?: return@mapNotNull null,
                                    name = obj.get("name")?.asString ?: "",
                                    picUrl = obj.get("picUrl")?.asString,
                                    alias = obj.get("alias")?.asJsonArray?.map { it.asString } ?: emptyList(),
                                    albumSize = obj.get("albumSize")?.asInt ?: 0,
                                    briefDesc = obj.get("briefDesc")?.asString
                                )
                            } catch (e: Exception) { null }
                        } ?: emptyList()
                        searchResults = emptyList()
                        searchPlaylists = emptyList()
                    }
                    MusicApiMethod.SEARCH_TYPE_PLAYLIST -> {
                        searchPlaylists = resultObj?.get("playlists")?.asJsonArray?.mapNotNull {
                            JsonUtils.parsePlaylist(it)
                        } ?: emptyList()
                        searchResults = emptyList()
                        searchArtists = emptyList()
                    }
                }
            } finally { isSearching = false }
        }
    }

    fun fetchHotSearches() {
        viewModelScope.launch {
            val body = withContext(Dispatchers.IO) { api.getHotSearches() }
            hotSearches = body.get("data")?.asJsonArray?.map {
                val obj = it.asJsonObject
                obj.get("searchWord").asString to (obj.get("content")?.asString ?: "")
            } ?: emptyList()
        }
    }

    fun fetchSuggestions(keywords: String) {
        if (keywords.isBlank()) { searchSuggestions = emptyList(); return }
        viewModelScope.launch {
            val body = withContext(Dispatchers.IO) { api.getSearchSuggestions(keywords) }
            searchSuggestions = body.get("result")?.asJsonObject?.get("allMatch")?.asJsonArray?.map {
                it.asJsonObject.get("keyword").asString
            } ?: emptyList()
        }
    }

    fun clearHistory() {
        searchHistory = emptyList()
        UserPreferences.saveSearchHistory(getApplication(), emptyList())
    }
}
