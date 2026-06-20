package cp.player.manager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.model.Song
import cp.player.util.CoverArtExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 本地音乐管理器。
 *
 * 负责：
 * 1. 管理本地歌曲与云端歌曲的关联绑定
 * 2. 协调封面提取（内嵌封面 → 云端封面）
 */
object LocalMusicManager {
    private const val TAG = "LocalMusicManager"
    private const val BINDINGS_FILE = "local_cloud_bindings.json"
    private val gson = Gson()

    /** 本地歌曲 ID → 云端绑定信息 */
    private val bindings = mutableMapOf<String, CloudBinding>()

    private val _bindingsFlow = MutableStateFlow<Map<String, CloudBinding>>(emptyMap())
    val bindingsFlow = _bindingsFlow.asStateFlow()

    /**
     * 云端歌曲绑定信息。
     */
    data class CloudBinding(
        val cloudSongId: String,
        val cloudSongName: String,
        val cloudArtist: String,
        val cloudAlbum: String,
        val cloudCoverUrl: String? = null
    )

    fun init(context: Context) {
        val file = File(context.filesDir, BINDINGS_FILE)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<Map<String, CloudBinding>>() {}.type
                val data: Map<String, CloudBinding>? = gson.fromJson(json, type)
                if (data != null) {
                    bindings.clear()
                    bindings.putAll(data)
                    _bindingsFlow.value = bindings.toMap()
                    Log.i(TAG, "Loaded ${bindings.size} cloud bindings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bindings", e)
            }
        }
    }

    /**
     * 绑定本地歌曲到云端歌曲。
     */
    fun bind(context: Context, localSongId: String, cloudSong: Song) {
        val binding = CloudBinding(
            cloudSongId = cloudSong.id,
            cloudSongName = cloudSong.name,
            cloudArtist = cloudSong.artist,
            cloudAlbum = cloudSong.album,
            cloudCoverUrl = cloudSong.albumArtUrl
        )
        bindings[localSongId] = binding
        _bindingsFlow.value = bindings.toMap()
        save(context)
        Log.i(TAG, "Bound local[$localSongId] → cloud[${cloudSong.id}] ${cloudSong.name}")
    }

    /**
     * 解除本地歌曲的云端绑定。
     */
    fun unbind(context: Context, localSongId: String) {
        bindings.remove(localSongId)
        _bindingsFlow.value = bindings.toMap()
        save(context)
        Log.i(TAG, "Unbound local[$localSongId]")
    }

    /**
     * 获取本地歌曲的云端绑定。
     */
    fun getBinding(localSongId: String): CloudBinding? = bindings[localSongId]

    /**
     * 将绑定信息转换为云端 Song 对象（用于播放、获取歌词等）。
     */
    fun getCloudSong(localSongId: String): Song? {
        val binding = bindings[localSongId] ?: return null
        return Song(
            id = binding.cloudSongId,
            name = binding.cloudSongName,
            artist = binding.cloudArtist,
            album = binding.cloudAlbum,
            albumArtUrl = binding.cloudCoverUrl
        )
    }

    /**
     * 获取本地歌曲封面路径。
     *
     * 优先级：
     * 1. 云端绑定的封面 URL
     * 2. 从音频文件提取的内嵌封面（缓存到磁盘）
     *
     * @return 可供 Coil 加载的 URL 字符串（http/https 或 file://），无封面时返回 null
     */
    fun getCoverArt(context: Context, localSongId: String, filePath: String?): String? {
        // 1. 云端绑定封面
        val binding = bindings[localSongId]
        if (binding?.cloudCoverUrl != null) {
            return binding.cloudCoverUrl
        }

        // 2. 内嵌封面提取
        return CoverArtExtractor.getOrExtract(context, localSongId, filePath)
    }

    private fun save(context: Context) {
        try {
            val file = File(context.filesDir, BINDINGS_FILE)
            file.writeText(gson.toJson(bindings))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bindings", e)
        }
    }
}
