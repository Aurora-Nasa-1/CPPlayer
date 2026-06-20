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
    val downloadTime: Long = System.currentTimeMillis(),
    /** 下载时的音乐提供商 ID，用于隔离不同提供商的下载 */
    val providerId: String = "",
    /** 本地封面文件路径（file:// URI），下载时提取并持久化，重启后仍可用 */
    val localCoverPath: String? = null
)

/**
 * 下载注册表。
 *
 * 管理所有已下载歌曲的元数据。支持不同音乐提供商的下载隔离：
 * - 通过 [providerId] 区分来自不同提供商的同一首歌
 * - 内部 key 格式为 `{providerId}:{songId}`，无 provider 时直接使用 `songId`
 */
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
                    // 过滤掉无效条目（song.id 为空的）
                    val validData = data.filter { it.value.song.id.isNotBlank() }
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

    /**
     * 构建带提供商前缀的 key，用于隔离不同提供商的下载。
     */
    private fun buildKey(songId: String, providerId: String): String {
        return if (providerId.isNotEmpty()) "$providerId:$songId" else songId
    }

    /**
     * 注册已下载的歌曲。
     *
     * @param providerId 音乐提供商 ID
     */
    fun register(context: Context, song: Song, filePath: String, providerId: String = "") {
        val key = buildKey(song.id, providerId)
        Log.d(TAG, "Registering song: ${song.name} (Key: $key) at $filePath")
        // 移除同一 song.id 的旧条目，防止不同 provider 导致重复
        cachedMetadata.entries.removeAll { it.value.song.id == song.id }
        val metadata = DownloadedSongMetadata(song, filePath, providerId = providerId)
        cachedMetadata[key] = metadata
        _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
        save(context)
    }

    fun unregister(context: Context, songId: String, providerId: String = "") {
        val key = buildKey(songId, providerId)
        Log.d(TAG, "Unregistering song Key: $key")
        cachedMetadata.remove(key)
        _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
        save(context)
    }

    fun getMetadata(songId: String, providerId: String = ""): DownloadedSongMetadata? {
        val key = buildKey(songId, providerId)
        return cachedMetadata[key]
    }

    /**
     * 检查歌曲是否已下载（兼容旧数据：先查带 provider 的 key，再查不带的）。
     */
    fun isDownloaded(songId: String, providerId: String = ""): Boolean {
        if (providerId.isNotEmpty()) {
            val key = buildKey(songId, providerId)
            if (cachedMetadata.containsKey(key)) return true
        }
        return cachedMetadata.containsKey(songId)
    }

    /**
     * 更新已下载歌曲的本地封面路径。
     */
    fun updateCoverPath(context: Context, songId: String, coverPath: String, providerId: String = "") {
        val key = buildKey(songId, providerId)
        val existing = cachedMetadata[key] ?: return
        cachedMetadata[key] = existing.copy(localCoverPath = coverPath)
        _downloadedSongsFlow.value = cachedMetadata.values.toList().sortedByDescending { it.downloadTime }
        save(context)
    }

    fun getAllDownloadedIds(): Set<String> = cachedMetadata.values.map { it.song.id }.toSet()

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
