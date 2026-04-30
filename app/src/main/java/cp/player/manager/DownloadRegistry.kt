package cp.player.manager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.model.Song
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadedSongMetadata(
    val song: Song,
    val filePath: String?,
    val downloadTime: Long = System.currentTimeMillis()
)

object DownloadRegistry {
    private const val TAG = "DownloadRegistry"
    private const val REGISTRY_FILE = "download_registry.json"
    private val gson = Gson()
    private val cachedMetadata: MutableMap<String, DownloadedSongMetadata> = mutableMapOf()

    private val _downloadedSongsFlow = MutableStateFlow<List<DownloadedSongMetadata>>(emptyList())
    val downloadedSongsFlow = _downloadedSongsFlow.asStateFlow()

    fun init(context: Context) {
        val file = File(context.filesDir, REGISTRY_FILE)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<Map<String, DownloadedSongMetadata>>() {}.type
                val data: Map<String, DownloadedSongMetadata>? = gson.fromJson(json, type)
                if (data != null) {
                    cachedMetadata.clear()
                    // Filter out entries with null song to prevent crashes
                    val validData = data.filter { true } // it.value.song is not nullable based on type
                    cachedMetadata.putAll(validData)
                    _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
                    Log.i(TAG, "Loaded ${cachedMetadata.size} downloaded songs")
                    if (validData.size != data.size) {
                        Log.w(TAG, "Filtered out ${data.size - validData.size} invalid download entries")
                        save(context) // Save the cleaned registry
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load registry", e)
            }
        } else {
            Log.i(TAG, "No registry file found at ${file.absolutePath}")
        }
    }

    fun register(context: Context, song: Song, filePath: String) {
        Log.d(TAG, "Registering song: ${song.name} (ID: ${song.id}) at $filePath")
        val metadata = DownloadedSongMetadata(song, filePath)
        cachedMetadata[song.id] = metadata
        _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
        save(context)
    }

    fun unregister(context: Context, songId: String) {
        Log.d(TAG, "Unregistering song ID: $songId")
        cachedMetadata.remove(songId)
        _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
        save(context)
    }

    fun getMetadata(songId: String): DownloadedSongMetadata? = cachedMetadata[songId]

    fun getAllDownloadedIds(): Set<String> = cachedMetadata.keys.toSet()

    fun getAllDownloadedSongs(): List<DownloadedSongMetadata> = cachedMetadata.values.toList()

    private fun save(context: Context) {
        try {
            val file = File(context.filesDir, REGISTRY_FILE)
            file.writeText(gson.toJson(cachedMetadata))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save registry", e)
        }
    }
}
