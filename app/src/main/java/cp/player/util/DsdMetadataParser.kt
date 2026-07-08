package cp.player.util

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DSF/DFF (DSD) 音频文件元数据解析器。
 *
 * - DSF: 解析 ID3v2 标签（标题、歌手、专辑、封面）
 * - DFF: 解析 FORM/SND 结构，提取基本信息
 */
object DsdMetadataParser {
    private const val TAG = "DsdMetadataParser"

    /**
     * 解析结果数据类。
     */
    data class DsdMetadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val coverArt: ByteArray? = null,
        val sampleRate: Int = 0,
        val channels: Int = 0,
        val bitsPerSample: Int = 1 // DSD 通常为 1-bit
    )

    /**
     * 解析 DSF/DFF 文件的元数据。
     *
     * @param filePath 文件路径（支持 file:// URI 和普通路径）
     * @return 解析结果，失败返回 null
     */
    fun parse(filePath: String): DsdMetadata? {
        val cleanPath = if (filePath.startsWith("file://")) {
            filePath.removePrefix("file://")
        } else {
            filePath
        }

        val file = File(cleanPath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val ext = file.extension.lowercase()
            when (ext) {
                "dsf" -> parseDsf(file)
                "dff" -> parseDff(file)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse DSD file: $filePath", e)
            null
        }
    }

    /**
     * 解析 DSF 文件。
     * DSF 文件结构：DSD chunk → fmt chunk → data chunk → ID3v2 tag
     */
    private fun parseDsf(file: File): DsdMetadata? {
        RandomAccessFile(file, "r").use { raf ->
            // 1. 验证 DSF 文件头
            val header = ByteArray(4)
            raf.readFully(header)
            if (String(header) != "DSD ") return null

            // 2. 读取文件大小和元数据偏移
            raf.seek(12)
            val fileSize = raf.readLittleEndianLong()
            val metadataOffset = raf.readLittleEndianLong()

            // 3. 读取 fmt chunk
            raf.seek(28)
            val fmtChunkId = ByteArray(4)
            raf.readFully(fmtChunkId)
            if (String(fmtChunkId) != "fmt ") return null

            val fmtChunkSize = raf.readLittleEndianLong()
            val formatVersion = raf.readLittleEndianInt()
            val formatId = raf.readLittleEndianInt()
            val channelType = raf.readLittleEndianInt()
            val channelNum = raf.readLittleEndianInt()
            val sampleFrequency = raf.readLittleEndianInt()
            val bitsPerSample = raf.readLittleEndianInt()

            // 4. 如果有元数据偏移，解析 ID3v2 标签
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var coverArt: ByteArray? = null

            if (metadataOffset > 0) {
                val id3Data = readId3v2Tag(raf, metadataOffset)
                if (id3Data != null) {
                    title = id3Data.title
                    artist = id3Data.artist
                    album = id3Data.album
                    coverArt = id3Data.coverArt
                }
            }

            return DsdMetadata(
                title = title,
                artist = artist,
                album = album,
                coverArt = coverArt,
                sampleRate = sampleFrequency,
                channels = channelNum,
                bitsPerSample = bitsPerSample
            )
        }
    }

    /**
     * 解析 DFF 文件。
     * DFF 文件结构：FRM8 → PROP → FS  sampleRate channels → SND
     */
    private fun parseDff(file: File): DsdMetadata? {
        RandomAccessFile(file, "r").use { raf ->
            // 1. 验证 FORM 头
            val formId = ByteArray(4)
            raf.readFully(formId)
            if (String(formId) != "FRM8") return null

            val formSize = raf.readBigEndianLong()

            // 2. 验证 DSD form type
            val formType = ByteArray(4)
            raf.readFully(formType)
            if (String(formType) != "DSD ") return null

            var sampleRate = 0
            var channels = 0
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var coverArt: ByteArray? = null

            // 3. 遍历 chunks
            val endPos = raf.filePointer + formSize - 4
            while (raf.filePointer < endPos) {
                val chunkId = ByteArray(4)
                if (raf.read(chunkId) != 4) break
                val chunkIdStr = String(chunkId)

                val chunkSize = raf.readBigEndianLong()
                val chunkStart = raf.filePointer

                when (chunkIdStr) {
                    "PROP" -> {
                        val propType = ByteArray(4)
                        raf.readFully(propType)
                        if (String(propType) != "SND ") break

                        // 遍历 PROP 内的 chunks
                        val propEnd = chunkStart + chunkSize - 4
                        while (raf.filePointer < propEnd) {
                            val subChunkId = ByteArray(4)
                            if (raf.read(subChunkId) != 4) break
                            val subChunkIdStr = String(subChunkId)

                            val subChunkSize = raf.readBigEndianLong()
                            val subChunkStart = raf.filePointer

                            when (subChunkIdStr) {
                                "FS  " -> {
                                    sampleRate = raf.readBigEndianInt()
                                }
                                "CHNL" -> {
                                    channels = raf.readBigEndianShort().toInt() and 0xFFFF
                                }
                                "CMPR" -> {
                                    // 压缩类型（DSD 或 DSDZIP）
                                    raf.skipBytes(subChunkSize.toInt())
                                }
                                else -> {
                                    raf.seek(subChunkStart + subChunkSize)
                                }
                            }

                            // 对齐到偶数字节
                            if (subChunkSize % 2 != 0L) raf.skipBytes(1)
                        }
                    }
                    "ID3 " -> {
                        // DFF 文件末尾可能有 ID3v2 标签
                        val id3Data = parseId3v2FromBuffer(raf, chunkSize.toInt())
                        if (id3Data != null) {
                            title = id3Data.title
                            artist = id3Data.artist
                            album = id3Data.album
                            coverArt = id3Data.coverArt
                        }
                    }
                    "DSD " -> {
                        // 音频数据块，跳过
                        raf.seek(chunkStart + chunkSize)
                    }
                    else -> {
                        raf.seek(chunkStart + chunkSize)
                    }
                }

                // 对齐到偶数字节
                if (chunkSize % 2 != 0L) raf.skipBytes(1)
            }

            return DsdMetadata(
                title = title,
                artist = artist,
                album = album,
                coverArt = coverArt,
                sampleRate = sampleRate,
                channels = channels
            )
        }
    }

    /**
     * 从指定偏移读取 ID3v2 标签。
     */
    private fun readId3v2Tag(raf: RandomAccessFile, offset: Long): Id3Result? {
        raf.seek(offset)
        val id3Header = ByteArray(10)
        if (raf.read(id3Header) != 10) return null

        // 验证 ID3v2 标识
        if (id3Header[0] != 'I'.code.toByte() ||
            id3Header[1] != 'D'.code.toByte() ||
            id3Header[2] != '3'.code.toByte()) {
            return null
        }

        val version = ((id3Header[3].toInt() and 0xFF) shl 8) or (id3Header[4].toInt() and 0xFF)
        val flags = id3Header[5].toInt() and 0xFF
        val size = decodeSyncsafeInt(id3Header, 6)

        val tagData = ByteArray(size)
        raf.readFully(tagData)

        return parseId3v2Frames(tagData, version, flags)
    }

    /**
     * 从缓冲区解析 ID3v2 标签。
     */
    private fun parseId3v2FromBuffer(raf: RandomAccessFile, size: Int): Id3Result? {
        val id3Header = ByteArray(10)
        if (raf.read(id3Header) != 10) return null

        if (id3Header[0] != 'I'.code.toByte() ||
            id3Header[1] != 'D'.code.toByte() ||
            id3Header[2] != '3'.code.toByte()) {
            return null
        }

        val version = ((id3Header[3].toInt() and 0xFF) shl 8) or (id3Header[4].toInt() and 0xFF)
        val flags = id3Header[5].toInt() and 0xFF
        val tagSize = decodeSyncsafeInt(id3Header, 6)

        val tagData = ByteArray(tagSize)
        raf.readFully(tagData)

        return parseId3v2Frames(tagData, version, flags)
    }

    /**
     * 解析 ID3v2 帧。
     */
    private fun parseId3v2Frames(data: ByteArray, version: Int, flags: Int): Id3Result? {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var coverArt: ByteArray? = null

        var offset = 0
        while (offset < data.size - 10) {
            val frameId = String(data, offset, 4)
            if (frameId[0] == ' ') break

            val frameSize = if (version >= 4) {
                decodeSyncsafeInt(data, offset + 4)
            } else {
                ((data[offset + 4].toInt() and 0xFF) shl 24) or
                ((data[offset + 5].toInt() and 0xFF) shl 16) or
                ((data[offset + 6].toInt() and 0xFF) shl 8) or
                (data[offset + 7].toInt() and 0xFF)
            }

            val frameFlags = ((data[offset + 8].toInt() and 0xFF) shl 8) or
                             (data[offset + 9].toInt() and 0xFF)

            if (frameSize <= 0 || offset + 10 + frameSize > data.size) break

            val frameData = data.copyOfRange(offset + 10, offset + 10 + frameSize)

            when (frameId) {
                "TIT2" -> title = decodeTextFrame(frameData)
                "TPE1" -> artist = decodeTextFrame(frameData)
                "TALB" -> album = decodeTextFrame(frameData)
                "APIC" -> coverArt = decodeApicFrame(frameData)
            }

            offset += 10 + frameSize
        }

        if (title == null && artist == null && album == null && coverArt == null) return null

        return Id3Result(title, artist, album, coverArt)
    }

    /**
     * 解码文本帧。
     */
    private fun decodeTextFrame(data: ByteArray): String? {
        if (data.isEmpty()) return null

        val encoding = data[0].toInt() and 0xFF
        val textBytes = data.copyOfRange(1, data.size)

        return try {
            when (encoding) {
                0 -> String(textBytes, Charsets.ISO_8859_1)
                1 -> {
                    // UTF-16 with BOM
                    if (textBytes.size >= 2) {
                        val bom = (textBytes[0].toInt() and 0xFF) or ((textBytes[1].toInt() and 0xFF) shl 8)
                        when (bom) {
                            0xFEFF -> String(textBytes, 2, textBytes.size - 2, Charsets.UTF_16BE)
                            0xFFFE -> String(textBytes, 2, textBytes.size - 2, Charsets.UTF_16LE)
                            else -> String(textBytes, Charsets.UTF_16BE)
                        }
                    } else null
                }
                2 -> String(textBytes, Charsets.UTF_16BE) // UTF-16BE without BOM
                3 -> String(textBytes, Charsets.UTF_8)
                else -> String(textBytes, Charsets.UTF_8)
            }.trim().trim(' ')
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解码 APIC 帧（封面图片）。
     */
    private fun decodeApicFrame(data: ByteArray): ByteArray? {
        if (data.size < 10) return null

        try {
            var offset = 0
            val encoding = data[offset].toInt() and 0xFF
            offset++

            // 读取 MIME 类型（null 结尾）
            val mimeEnd = data.indexOf(' '.code.toByte(), offset)
            if (mimeEnd < 0) return null
            offset = mimeEnd + 1

            // 读取图片类型
            val picType = data[offset].toInt() and 0xFF
            offset++

            // 跳过描述（null 结尾）
            when (encoding) {
                0, 3 -> {
                    val descEnd = data.indexOf(' '.code.toByte(), offset)
                    if (descEnd >= 0) offset = descEnd + 1
                }
                1, 2 -> {
                    // UTF-16: 查找双 null
                    while (offset < data.size - 1) {
                        if (data[offset] == 0.toByte() && data[offset + 1] == 0.toByte()) {
                            offset += 2
                            break
                        }
                        offset += 2
                    }
                }
            }

            // 剩余数据就是图片
            return if (offset < data.size) {
                data.copyOfRange(offset, data.size)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode APIC frame", e)
            return null
        }
    }

    /**
     * 解码 syncsafe 整数（ID3v2 使用的 4 字节编码，每字节只用 7 位）。
     */
    private fun decodeSyncsafeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
               ((data[offset + 1].toInt() and 0x7F) shl 14) or
               ((data[offset + 2].toInt() and 0x7F) shl 7) or
               (data[offset + 3].toInt() and 0x7F)
    }

    /**
     * RandomAccessFile 扩展：读取小端序 Long。
     */
    private fun RandomAccessFile.readLittleEndianLong(): Long {
        val buf = ByteArray(8)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    /**
     * RandomAccessFile 扩展：读取小端序 Int。
     */
    private fun RandomAccessFile.readLittleEndianInt(): Int {
        val buf = ByteArray(4)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    /**
     * RandomAccessFile 扩展：读取大端序 Long。
     */
    private fun RandomAccessFile.readBigEndianLong(): Long {
        val buf = ByteArray(8)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).long
    }

    /**
     * RandomAccessFile 扩展：读取大端序 Int。
     */
    private fun RandomAccessFile.readBigEndianInt(): Int {
        val buf = ByteArray(4)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }

    /**
     * RandomAccessFile 扩展：读取大端序 Short。
     */
    private fun RandomAccessFile.readBigEndianShort(): Short {
        val buf = ByteArray(2)
        readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).short
    }

    /**
     * 检查文件是否为 DSF/DFF 格式。
     */
    fun isDsdFile(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "dsf" || ext == "dff"
    }

    private data class Id3Result(
        val title: String?,
        val artist: String?,
        val album: String?,
        val coverArt: ByteArray?
    )
}
