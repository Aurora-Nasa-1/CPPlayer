package cp.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

/**
 * 本地音频文件封面提取工具。
 *
 * 使用 [MediaMetadataRetriever.getEmbeddedPicture] 从 MP3/FLAC/M4A 等音频文件中
 * 提取内嵌专辑封面，缓存到应用 cache 目录供 Coil 加载。
 */
object CoverArtExtractor {
    private const val TAG = "CoverArtExtractor"
    private const val COVER_DIR = "cover_art"

    /** 缓存中的哨兵值，表示该歌曲无内嵌封面 */
    private const val NO_ART = ""

    // 内存 LRU 缓存：songId → file path（NO_ART 表示无封面）
    private val memoryCache = LruCache<String, String>(80)

    /**
     * 获取本地歌曲封面路径。
     *
     * 优先级：内存缓存 → 磁盘缓存 → 从音频文件提取。
     *
     * @return 封面文件的 `file://` URI 字符串，无封面时返回 null
     */
    fun getOrExtract(context: Context, songId: String, filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        if (songId.isBlank()) return null

        val normalizedId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (normalizedId.isBlank()) return null

        // 1. 内存缓存
        val cached = memoryCache.get(normalizedId)
        if (cached != null) return cached.ifBlank { null }

        // 2. 磁盘缓存
        val coverFile = File(context.cacheDir, "$COVER_DIR/$normalizedId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            val path = "file://${coverFile.absolutePath}"
            memoryCache.put(normalizedId, path)
            return path
        }

        // 3. 从音频文件提取
        return try {
            val artBytes = extractEmbeddedArt(filePath)
            if (artBytes != null) {
                coverFile.parentFile?.mkdirs()
                FileOutputStream(coverFile).use { it.write(artBytes) }
                val path = "file://${coverFile.absolutePath}"
                memoryCache.put(normalizedId, path)
                path
            } else {
                memoryCache.put(normalizedId, NO_ART)
                null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to extract cover art for $songId", e)
            memoryCache.put(normalizedId, NO_ART)
            null
        }
    }

    /**
     * 从音频文件提取嵌入的封面图片字节数据。
     */
    private fun extractEmbeddedArt(filePath: String): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            retriever.release()
            art
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清除指定歌曲的封面缓存。
     */
    fun clearCache(context: Context, songId: String) {
        val normalizedId = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        memoryCache.remove(normalizedId)
        File(context.cacheDir, "$COVER_DIR/$normalizedId.jpg").delete()
    }

    /**
     * 清除所有封面缓存。
     */
    fun clearAllCache(context: Context) {
        memoryCache.evictAll()
        File(context.cacheDir, COVER_DIR).deleteRecursively()
    }
}
