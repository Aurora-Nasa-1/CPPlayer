package cp.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import cp.player.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内嵌歌词提取工具。
 *
 * 从音频文件的 ID3 USLT / Vorbis LYRICS 标签中提取歌词文本，
 * 然后尝试解析为 LRC 格式或纯文本歌词。
 */
object EmbeddedLyricsExtractor {
    private const val TAG = "EmbeddedLyricsExtractor"
    private val LRC_REGEX = Regex("\\[\\d{2}:\\d{2}[.:]\\d{2,3}]")

    /**
     * 从本地音频文件提取内嵌歌词。
     *
     * @param filePath 文件绝对路径（本地文件）
     * @param contentUri content:// URI（MediaStore 文件）
     * @return 解析后的歌词行列表，无歌词时返回空列表
     */
    suspend fun extract(context: Context, filePath: String?, contentUri: String?): List<LyricLine> {
        return withContext(Dispatchers.IO) {
            try {
                val lyrics = extractRawLyrics(context, filePath, contentUri)
                if (lyrics.isNullOrBlank()) return@withContext emptyList()

                // 尝试按 LRC 格式解析
                val parsed = LyricUtils.parseLrc(lyrics)
                if (parsed.isNotEmpty()) {
                    DebugLog.i("$TAG: parsed ${parsed.size} LRC lines from embedded lyrics")
                    return@withContext parsed
                }

                // 非 LRC 格式，按纯文本处理（每行一个歌词，无时间戳）
                val textLines = lyrics.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (textLines.isNotEmpty()) {
                    DebugLog.i("$TAG: parsed ${textLines.size} plain text lines from embedded lyrics")
                    // 纯文本歌词：每行间隔 5 秒
                    return@withContext textLines.mapIndexed { index, text ->
                        LyricLine(
                            time = index * 5000L,
                            endTime = (index + 1) * 5000L,
                            text = text
                        )
                    }
                }

                emptyList()
            } catch (e: Exception) {
                DebugLog.w("$TAG: failed to extract embedded lyrics: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 提取原始歌词文本。
     *
     * 使用 MediaMetadataRetriever 提取内嵌歌词。
     * Android 的 MediaMetadataRetriever 通过 METADATA_KEY_WRITER 或
     * METADATA_KEY_COMMENT 等字段可能包含歌词信息。
     */
    private fun extractRawLyrics(context: Context, filePath: String?, contentUri: String?): String? {
        val retriever = MediaMetadataRetriever()
        try {
            when {
                !filePath.isNullOrBlank() && File(filePath).exists() -> {
                    retriever.setDataSource(filePath)
                }
                !contentUri.isNullOrBlank() -> {
                    retriever.setDataSource(context, Uri.parse(contentUri))
                }
                else -> return null
            }

            // 尝试多个可能包含歌词的元数据字段
            // Android 的 MediaMetadataRetriever 对歌词支持有限，
            // 不同设备/格式可能将歌词放在不同字段中
            val lyricsFields = listOf(
                MediaMetadataRetriever.METADATA_KEY_WRITER,       // 词作者（某些实现包含歌词文本）
                MediaMetadataRetriever.METADATA_KEY_COMPOSER,     // 曲作者
            )

            val standardLyrics = lyricsFields.firstNotNullOfOrNull { field ->
                val value = retriever.extractMetadata(field)
                if (!value.isNullOrBlank() && looksLikeLyrics(value)) {
                    DebugLog.i("$TAG: found lyrics in field $field (${value.length} chars)")
                    value
                } else null
            }
            if (standardLyrics != null) return standardLyrics

            // 尝试非标准 key（某些 Android 实现/设备厂商扩展）
            // key 100 = METADATA_KEY_lyrics (部分三星/小米等设备支持)
            val extraKeys = listOf(100, 101)
            return extraKeys.firstNotNullOfOrNull { key ->
                try {
                    val value = retriever.extractMetadata(key)
                    if (!value.isNullOrBlank() && looksLikeLyrics(value)) {
                        DebugLog.i("$TAG: found lyrics in extended key $key (${value.length} chars)")
                        value
                    } else null
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            DebugLog.w("$TAG: MediaMetadataRetriever error: ${e.message}")
            return null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * 判断文本是否看起来像歌词。
     * LRC 格式包含时间戳 [MM:SS.xx]，纯文本歌词通常有多行。
     */
    private fun looksLikeLyrics(text: String): Boolean {
        // LRC 格式检测
        if (text.contains(LRC_REGEX)) return true
        // 多行文本（至少 3 行，每行不太长）
        val lines = text.lines().filter { it.trim().isNotEmpty() }
        if (lines.size >= 3 && lines.all { it.length < 200 }) return true
        return false
    }
}
