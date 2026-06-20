package cp.player.manager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.model.LocalSongMetadata
import cp.player.model.Song
import cp.player.util.CoverArtExtractor
import cp.player.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

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

    /**
     * 批量自动同步：为未绑定的本地歌曲搜索最匹配的云端歌曲并绑定。
     *
     * @param songs 待同步的本地歌曲列表
     * @param context Android Context
     * @return Triple(成功数, 跳过数(已绑定), 失败数)
     */
    suspend fun autoBindBatch(
        songs: List<LocalSongMetadata>,
        context: Context
    ): Triple<Int, Int, Int> {
        val api = cp.player.api.MusicApiServiceFactory.instance
        var success = 0
        var skipped = 0
        var failed = 0

        for (localSong in songs) {
            // 已绑定则跳过
            if (bindings.containsKey(localSong.songId)) {
                skipped++
                continue
            }

            try {
                val query = "${localSong.songName} ${localSong.artist}"
                val body = withContext(Dispatchers.IO) {
                    api.search(query, 1)
                }
                val resultObj = body.get("result")?.asJsonObject
                val candidates = resultObj?.get("songs")?.asJsonArray?.mapNotNull {
                    JsonUtils.parseSong(it)
                } ?: emptyList()

                if (candidates.isEmpty()) {
                    failed++
                    Log.w(TAG, "AutoBind: no results for [${localSong.songName} - ${localSong.artist}]")
                    continue
                }

                val bestMatch = candidates.maxByOrNull { scoreMatch(localSong, it) }
                if (bestMatch != null && scoreMatch(localSong, bestMatch) >= 50) {
                    bind(context, localSong.songId, bestMatch)
                    success++
                    Log.i(TAG, "AutoBind: bound [${localSong.songName}] → [${bestMatch.name}] (score=${scoreMatch(localSong, bestMatch)})")
                } else {
                    failed++
                    Log.w(TAG, "AutoBind: no good match for [${localSong.songName}] (best=${bestMatch?.name}, score=${bestMatch?.let { scoreMatch(localSong, it) }})")
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "AutoBind: error for [${localSong.songName}]", e)
            }
        }

        Log.i(TAG, "AutoBind batch done: success=$success, skipped=$skipped, failed=$failed")
        return Triple(success, skipped, failed)
    }

    /**
     * 评分算法：本地歌曲 vs 云端搜索结果的匹配度。
     * 满分 100，阈值 50 才自动绑定。
     */
    private fun scoreMatch(local: LocalSongMetadata, cloud: Song): Int {
        var score = 0

        // 标题匹配 (0~40)
        val localTitle = normalize(local.songName)
        val cloudTitle = normalize(cloud.name)
        score += when {
            localTitle == cloudTitle -> 40
            localTitle.contains(cloudTitle) || cloudTitle.contains(localTitle) -> 25
            similarity(localTitle, cloudTitle) > 0.6f -> 15
            else -> 0
        }

        // 歌手匹配 (0~30)
        val localArtist = normalize(local.artist)
        val cloudArtist = normalize(cloud.artist)
        score += when {
            localArtist == cloudArtist -> 30
            localArtist.contains(cloudArtist) || cloudArtist.contains(localArtist) -> 20
            similarity(localArtist, cloudArtist) > 0.5f -> 10
            else -> 0
        }

        // 专辑匹配 (0~10)
        val localAlbum = normalize(local.album)
        val cloudAlbum = normalize(cloud.album)
        if (localAlbum == cloudAlbum && localAlbum.isNotEmpty()) {
            score += 10
        }

        // 时长匹配需要从 MediaStore 获取，这里用 0 跳过
        // 如果后续 LocalSongMetadata 加入 duration 字段可以启用

        return score
    }

    /** 字符串归一化：去空格、转小写 */
    private fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /** 简单相似度：基于公共子串比例 */
    private fun similarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val common = a.commonPrefixWith(b).length + a.commonSuffixWith(b).length
        return common.toFloat() / maxOf(a.length, b.length)
    }
}
