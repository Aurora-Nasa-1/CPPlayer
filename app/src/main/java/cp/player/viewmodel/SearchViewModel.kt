package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import cp.player.model.*
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import kotlinx.coroutines.*

class SearchViewModel(application: Application) : BaseViewModel(application) {
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searchPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var hotSearches by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var searchHistory by mutableStateOf(UserPreferences.getSearchHistory(application))
    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
    var searchType by mutableIntStateOf(1)

    init {
        fetchHotSearches()
    }

    fun search(keywords: String, type: Int = 1) {
        searchType = type
        if (keywords.isBlank()) return

        val newHistory = (listOf(keywords) + searchHistory.filter { it != keywords }).take(10)
        searchHistory = newHistory
        UserPreferences.saveSearchHistory(getApplication(), newHistory)

        viewModelScope.launch {
            isLoading = true
            try {
                val body = withContext(Dispatchers.IO) {
                    callApi("cloudsearch", mapOf("keywords" to keywords, "type" to type.toString()))
                }
                val resultObj = body.get("result")?.asJsonObject
                when (type) {
                    1 -> {
                        searchResults = resultObj?.get("songs")?.asJsonArray?.mapNotNull { JsonUtils.parseSong(it) } ?: emptyList()
                        searchPlaylists = emptyList()
                    }
                    1000 -> {
                        searchPlaylists = resultObj?.get("playlists")?.asJsonArray?.map {
                            val obj = it.asJsonObject
                            Playlist(obj.get("id").asLong, obj.get("name").asString, obj.get("coverImgUrl").asString, obj.get("trackCount").asInt, null, null)
                        } ?: emptyList()
                        searchResults = emptyList()
                    }
                }
            } finally { isLoading = false }
        }
    }

    fun fetchHotSearches() {
        viewModelScope.launch {
            val body = withContext(Dispatchers.IO) { callApi("search/hot/detail") }
            hotSearches = body.get("data")?.asJsonArray?.map {
                val obj = it.asJsonObject
                obj.get("searchWord").asString to (obj.get("content")?.asString ?: "")
            } ?: emptyList()
        }
    }

    fun fetchSuggestions(keywords: String) {
        if (keywords.isBlank()) { searchSuggestions = emptyList(); return }
        viewModelScope.launch {
            val body = withContext(Dispatchers.IO) { callApi("search/suggest", mapOf("keywords" to keywords, "type" to "mobile")) }
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
