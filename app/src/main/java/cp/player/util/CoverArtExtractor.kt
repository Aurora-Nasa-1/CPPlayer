package cp.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

/**
 * 本地音频文件封面提取工具。
 *
 * 使用 [MediaMetadataRetriever.getEmbeddedPicture] 从 MP3/FLAC/M4A 等音频文件中
 * 提取内嵌专辑封面，缓存到应用 cache 目录供 Coil 加载。
 *
 * 对于 DSF/DFF (DSD) 格式，使用 [DsdMetadataParser] 提取内嵌封面。
 */
object CoverArtExtractor {
    private const val TAG = "CoverArtExtractor"
    private const val COVER_DIR = "cover_art"

    /** 缓存中的哨兵值，表示该歌曲无内嵌封面（仅在内存缓存中使用，不写入磁盘） */
    private const val NO_ART = ""

    // 内存 LRU 缓存：songId → file path（NO_ART 表示无封面）
    private val memoryCache = LruCache<String, String>(80)

    private val INVALID_ID_CHARS_REGEX = Regex("[^a-zA-Z0-9_-]")

    /**
     * 获取本地歌曲封面路径。
     *
     * 优先级：内存缓存 → 磁盘缓存 → 从音频文件提取。
     *
     * @param context 上下文
     * @param songId 歌曲 ID（用作缓存 key）
     * @param filePath 音频文件路径（file:// URI 或普通路径）
     * @param contentUri 可选的 content:// URI，作为 filePath 提取失败时的回退
     * @return 封面文件的 `file://` URI 字符串，无封面时返回 null
     */
    suspend fun getOrExtract(context: Context, songId: String, filePath: String?, contentUri: String? = null): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (filePath.isNullOrBlank() && contentUri.isNullOrBlank()) return@withContext null
        if (songId.isBlank()) return@withContext null

        val normalizedId = songId.replace(INVALID_ID_CHARS_REGEX, "_")
        if (normalizedId.isBlank()) return@withContext null

        // 1. 内存缓存（仅缓存成功结果，不缓存 NO_ART 以允许重试）
        val cached = memoryCache.get(normalizedId)
        if (cached != null && cached != NO_ART) return@withContext cached

        // 2. 磁盘缓存
        val coverFile = File(context.cacheDir, "$COVER_DIR/$normalizedId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            val path = "file://${coverFile.absolutePath}"
            memoryCache.put(normalizedId, path)
            return@withContext path
        }

        // 3. 从音频文件提取
        return@withContext try {
            var artBytes = extractEmbeddedArtWithFallback(context, filePath, contentUri)
            if (artBytes != null) {
                coverFile.parentFile?.mkdirs()
                FileOutputStream(coverFile).use { it.write(artBytes) }
                val path = "file://${coverFile.absolutePath}"
                memoryCache.put(normalizedId, path)
                path
            } else {
                // 不缓存失败结果，允许下次重试（文件可能稍后可用）
                null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to extract cover art for $songId", e)
            null
        }
    }

    /**
     * 带回退的封面提取：filePath → contentUri → 通过 contentUri 解析真实路径。
     * 对 DSD 文件使用 [DsdMetadataParser]，其他格式使用 [MediaMetadataRetriever]。
     */
    private fun extractEmbeddedArtWithFallback(context: Context, filePath: String?, contentUri: String?): ByteArray? {
        // 尝试1：直接使用 filePath
        if (!filePath.isNullOrBlank()) {
            val art = extractEmbeddedArt(context, filePath)
            if (art != null) return art
        }

        // 尝试2：使用 contentUri
        if (!contentUri.isNullOrBlank() && contentUri.startsWith("content://")) {
            val art = extractEmbeddedArt(context, contentUri)
            if (art != null) return art

            // 尝试3：从 contentUri 解析真实文件路径（Scoped Storage 下 DATA 列弃用的回退）
            val resolvedPath = resolveContentUriToFilePath(context, contentUri)
            if (!resolvedPath.isNullOrBlank() && resolvedPath != filePath) {
                val art2 = extractEmbeddedArt(context, resolvedPath)
                if (art2 != null) return art2
            }
        }

        // 尝试4：如果只有 contentUri，尝试通过 MediaStore 查询文件路径
        if (filePath.isNullOrBlank() && !contentUri.isNullOrBlank() && contentUri.startsWith("content://")) {
            val resolvedPath = resolveContentUriToFilePath(context, contentUri)
            if (!resolvedPath.isNullOrBlank()) {
                val art = extractEmbeddedArt(context, resolvedPath)
                if (art != null) return art
            }
        }

        return null
    }

    /**
     * 从 content:// URI 解析真实的文件系统路径。
     * 尝试多种方法：ContentResolver query、openFileDescriptor。
     */
    private fun resolveContentUriToFilePath(context: Context, contentUri: String): String? {
        try {
            val uri = Uri.parse(contentUri)

            // 方法1：通过 MediaStore 查询 DATA 列
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex >= 0) {
                            val path = cursor.getString(dataIndex)
                            if (!path.isNullOrBlank() && File(path).exists()) return path
                        }
                    }
                }
            } catch (_: Exception) {}

            // 方法2：通过 openFileDescriptor 获取真实路径（对 FUSE 文件系统可能有效）
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fdPath = "/proc/self/fd/${pfd.fd}"
                    val link = File(fdPath).canonicalPath
                    if (link != fdPath && File(link).exists()) return link
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to resolve content URI: $contentUri", e)
        }
        return null
    }

    /**
     * 从音频文件提取嵌入的封面图片字节数据。
     * 支持文件路径、content:// URI 以及 DSF/DFF 格式。
     *
     * DSD 文件检测策略：
     * 1. 文件扩展名检测（.dsf/.dff）
     * 2. 文件头魔数检测（DSD → "DSD "，DFF → "FRM8"）
     */
    private fun extractEmbeddedArt(context: Context, filePath: String): ByteArray? {
        // 优先尝试 DSF/DFF 格式解析（扩展名检测 + 魔数检测）
        if (isDsdFileByExtensionOrMagic(context, filePath)) {
            val resolvedPath = if (filePath.startsWith("content://")) {
                resolveContentUriToFilePath(context, filePath) ?: filePath
            } else {
                filePath
            }
            val metadata = DsdMetadataParser.parse(resolvedPath)
            if (metadata?.coverArt != null) {
                return metadata.coverArt
            }
        }

        // 使用 MediaMetadataRetriever 解析其他格式
        return try {
            val retriever = MediaMetadataRetriever()
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            val art = retriever.embeddedPicture
            retriever.release()
            art
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过扩展名或文件头魔数判断是否为 DSD 文件。
     * 解决 Android 10+ 下 DATA 列返回 null 导致 content:// URI 无法通过扩展名识别 DSD 文件的问题。
     */
    private fun isDsdFileByExtensionOrMagic(context: Context, filePath: String): Boolean {
        // 方法1：扩展名检测
        if (DsdMetadataParser.isDsdFile(filePath)) return true

        // 方法2：文件头魔数检测（DSF = "DSD "，DFF = "FRM8"）
        try {
            if (filePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(filePath))?.use { stream ->
                    val header = ByteArray(4)
                    if (stream.read(header) == 4) {
                        val magic = String(header)
                        return magic == "DSD " || magic == "FRM8"
                    }
                }
            } else {
                val cleanPath = filePath.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists() && file.canRead()) {
                    java.io.RandomAccessFile(file, "r").use { raf ->
                        val header = ByteArray(4)
                        raf.readFully(header)
                        val magic = String(header)
                        return magic == "DSD " || magic == "FRM8"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to detect DSD format by magic bytes: $filePath", e)
        }

        return false
    }

    /**
     * 清除指定歌曲的封面缓存。
     */
    fun clearCache(context: Context, songId: String) {
        val normalizedId = songId.replace(INVALID_ID_CHARS_REGEX, "_")
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
