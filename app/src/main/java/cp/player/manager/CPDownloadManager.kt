package cp.player.manager

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import cp.player.api.MusicApiMethod
import cp.player.api.MusicApiService
import cp.player.api.MusicApiServiceFactory
import cp.player.model.DownloadStatus
import cp.player.model.DownloadTask
import cp.player.model.LocalSongMetadata
import cp.player.model.Song
import cp.player.monitor.HealthMonitor
import cp.player.provider.ProviderManager
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class CPDownloadManager(private val application: Application) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val okHttpClient = OkHttpClient()
    private val api: MusicApiService = MusicApiServiceFactory.instance
    private val providerId: String get() = cp.player.provider.ProviderManager.currentProvider?.id ?: "default"

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks = _tasks.asStateFlow()

    private val _completedSongs = MutableStateFlow<Set<String>>(DownloadRegistry.getAllDownloadedIds())
    val completedSongs = _completedSongs.asStateFlow()

    private val _localSongs = MutableStateFlow<List<LocalSongMetadata>>(emptyList())
    val localSongs = _localSongs.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val downloadMutex = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    init {
        scanLocalMusic()
    }

    fun scanLocalMusic() {
        scope.launch {
            val localList = mutableListOf<LocalSongMetadata>()
            val existingPaths = mutableSetOf<String>() // 用于去重

            // 0. 触发 Android 媒体库扫描（确保新文件被索引）
            triggerMediaScan()

            // 1. 从 MediaStore 查询音频文件
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            try {
                application.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown"
                        val album = cursor.getString(albumColumn) ?: "Unknown"
                        val fileName = cursor.getString(nameColumn) ?: "Unknown"
                        val path = cursor.getString(dataColumn) // DATA 列在 Android 10+ 已弃用，可能返回 null

                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val localId = "local_$id"

                        // 尝试从 LocalMusicManager 获取已绑定的云端歌曲 ID
                        val cloudSongId = LocalMusicManager.getBinding(localId)?.cloudSongId

                        localList.add(LocalSongMetadata(
                            songId = localId,
                            fileName = fileName,
                            songName = title,
                            artist = artist,
                            album = album,
                            albumArtUrl = uri.toString(), // 音频 content URI，用于播放和封面提取回退
                            filePath = path, // 实际文件路径（可能为 null），用于封面提取
                            cloudSongId = cloudSongId
                        ))
                        if (path != null) existingPaths.add(path)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CPDownloadManager", "Failed to scan local music from MediaStore", e)
            }

            // 2. 手动扫描 DSF/DFF 文件（MediaStore 可能不索引这些格式）
            scanDsdFiles(localList, existingPaths)

            _localSongs.value = localList
        }
    }

    /**
     * 触发 Android 媒体库扫描常见音乐目录。
     * 确保新添加的文件（包括 DSF/DFF）被 MediaStore 索引。
     */
    private suspend fun triggerMediaScan() = withContext(Dispatchers.IO) {
        try {
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val directories = listOf(
                musicDir,
                File(musicDir, "CPPlayer"),
                downloadDir
            )
            val pathsToScan = mutableListOf<String>()
            for (dir in directories) {
                if (dir.exists() && dir.isDirectory) {
                    collectAudioFiles(dir, pathsToScan, depth = 0, maxDepth = 3)
                }
            }
            if (pathsToScan.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(
                    application,
                    pathsToScan.toTypedArray(),
                    null,
                    null
                )
                // 等待扫描完成
                kotlinx.coroutines.delay(1000)
            }
        } catch (e: Exception) {
            android.util.Log.e("CPDownloadManager", "Failed to trigger media scan", e)
        }
    }

    /**
     * 递归收集目录中的音频文件路径（用于媒体扫描）。
     */
    private fun collectAudioFiles(dir: File, result: MutableList<String>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                collectAudioFiles(file, result, depth + 1, maxDepth)
            } else if (file.isFile && isAudioFile(file.extension)) {
                result.add(file.absolutePath)
            }
        }
    }

    /**
     * 判断文件扩展名是否为支持的音频格式。
     */
    private fun isAudioFile(extension: String): Boolean {
        val ext = extension.lowercase()
        return ext in setOf(
            "mp3", "flac", "ogg", "oga", "opus", "m4a", "wav",
            "aac", "wma", "aif", "aiff", "dsf", "dff", "wv"
        )
    }

    /**
     * 手动扫描常见音乐目录中的 DSF/DFF 文件。
     * 使用 MediaStore 查询可能无法索引 DSD 格式文件。
     */
    private suspend fun scanDsdFiles(localList: MutableList<LocalSongMetadata>, existingPaths: MutableSet<String>) = withContext(Dispatchers.IO) {
        try {
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val directories = listOf(
                musicDir,
                File(musicDir, "CPPlayer"),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            )

            for (dir in directories) {
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryForDsd(dir, localList, existingPaths, depth = 0, maxDepth = 3)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CPDownloadManager", "Failed to scan DSD files", e)
        }
    }

    /**
     * 递归扫描目录中的 DSF/DFF 文件。
     */
    private fun scanDirectoryForDsd(
        dir: File,
        localList: MutableList<LocalSongMetadata>,
        existingPaths: MutableSet<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryForDsd(file, localList, existingPaths, depth + 1, maxDepth)
            } else if (file.isFile && (file.extension.equals("dsf", ignoreCase = true) || file.extension.equals("dff", ignoreCase = true))) {
                val path = file.absolutePath
                if (existingPaths.contains(path)) continue

                // 解析 DSD 文件元数据
                val metadata = cp.player.util.DsdMetadataParser.parse(path)
                val title = metadata?.title ?: file.nameWithoutExtension
                val artist = metadata?.artist ?: "Unknown"
                val album = metadata?.album ?: "DSD"

                // 生成唯一 ID（基于文件路径哈希）
                val localId = "dsd_${path.hashCode().toUInt()}"

                // 尝试从 LocalMusicManager 获取已绑定的云端歌曲 ID
                val cloudSongId = LocalMusicManager.getBinding(localId)?.cloudSongId

                localList.add(LocalSongMetadata(
                    songId = localId,
                    fileName = file.name,
                    songName = title,
                    artist = artist,
                    album = album,
                    albumArtUrl = "file://$path", // DSD 文件路径
                    filePath = path,
                    cloudSongId = cloudSongId
                ))
                existingPaths.add(path)
            }
        }
    }

    private fun getStorageStreamAndPath(song: Song): Pair<OutputStream, String>? {
        val userDownloadDir = UserPreferences.getDownloadDir(application)
        val sanitizedName = song.name.replace(INVALID_FILE_CHARS_REGEX, "_")
        val sanitizedArtist = song.artist.replace(INVALID_FILE_CHARS_REGEX, "_")
        val fileName = "$sanitizedName - $sanitizedArtist [${song.id}].mp3"

        if (userDownloadDir != null) {
            try {
                val treeUri = Uri.parse(userDownloadDir)
                val tree = DocumentFile.fromTreeUri(application, treeUri)
                val file = tree?.createFile("audio/mpeg", fileName)
                if (file != null) {
                    val out = application.contentResolver.openOutputStream(file.uri)
                    if (out != null) return Pair(out, file.uri.toString())
                }
            } catch (e: Exception) {
                android.util.Log.e("CPDownloadManager", "Failed to create file in user dir", e)
            }
        }

        // Default public storage path
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val cpDir = File(musicDir, "CPPlayer")
        if (!cpDir.exists()) cpDir.mkdirs()

        val localFile = File(cpDir, fileName)
        val out = localFile.outputStream()
        return Pair(out, localFile.absolutePath)
    }

    fun downloadSong(song: Song, cookie: String?, quality: String = "standard", allowCellular: Boolean = false) {
        if (DownloadRegistry.isDownloaded(song.id, providerId)) return
        if (_tasks.value.containsKey(song.id)) return
        if (downloadMutex.putIfAbsent(song.id, true) != null) return

        _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.DOWNLOADING, 0f, -1L)) }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(application, "Starting download: ${song.name}", Toast.LENGTH_SHORT).show()
        }

        val job = scope.launch {
            try {
                // Get URL via MusicApiService
                var url: String? = null
                val downloadBody = api.getSongDownloadUrl(song.id, quality)
                url = JsonUtils.findUrl(downloadBody)

                if (url == null) {
                    // 记录下载 URL 回退
                    HealthMonitor.recordCall(HealthMonitor.ApiCallRecord(
                        timestamp = System.currentTimeMillis(),
                        providerId = ProviderManager.getCurrentProviderId(),
                        method = "DOWNLOAD_URL_FALLBACK",
                        durationMs = 0,
                        success = false,
                        wasFallback = true,
                        fallbackFrom = "getSongDownloadUrl",
                        errorMessage = "下载 URL 解析失败，回退到 getSongUrlFallback"
                    ))
                    val fallbackBody = api.getSongUrlFallback(song.id, quality)
                    url = JsonUtils.findUrl(fallbackBody)
                }

                if (url == null || !url.startsWith("http")) {
                    throw Exception("No valid audio URL found")
                }

                val requestBuilder = Request.Builder().url(url)
                    .addHeader("User-Agent", "NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)")
                cookie?.let { requestBuilder.addHeader("Cookie", it) }

                val request = requestBuilder.build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")

                val bodyContent = response.body
                val totalBytes = bodyContent.contentLength()

                val (outputStream, filePath) = getStorageStreamAndPath(song) ?: throw Exception("Could not create output file")

                bodyContent.byteStream().use { input ->
                    outputStream.use { output ->
                        copyToTrackProgress(input, output, totalBytes, song.id)
                    }
                }

                DownloadRegistry.register(application, song, filePath, providerId)
                _completedSongs.update { it + song.id }
                _tasks.update { it + (song.id to it[song.id]!!.copy(status = DownloadStatus.COMPLETED, progress = 1f)) }

                // 提取内嵌封面并持久化到 filesDir（cacheDir 可能被系统清理）
                try {
                    val actualPath = if (filePath.startsWith("content://")) null else filePath
                    if (actualPath != null) {
                        val coverUri = cp.player.util.CoverArtExtractor.getOrExtract(application, song.id, actualPath)
                        if (coverUri != null) {
                            // 复制到 filesDir 以持久化
                            val persistentDir = java.io.File(application.filesDir, "downloaded_covers")
                            persistentDir.mkdirs()
                            val normalizedId = song.id.replace(INVALID_ID_CHARS_REGEX, "_")
                            val persistentFile = java.io.File(persistentDir, "$normalizedId.jpg")
                            val cacheFile = java.io.File(application.cacheDir, "cover_art/$normalizedId.jpg")
                            if (cacheFile.exists() && (!persistentFile.exists() || persistentFile.length() == 0L)) {
                                cacheFile.copyTo(persistentFile, overwrite = true)
                            }
                            val persistentUri = "file://${persistentFile.absolutePath}"
                            DownloadRegistry.updateCoverPath(application, song.id, persistentUri, providerId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CPDownloadManager", "Failed to extract cover art for downloaded song", e)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Download completed: ${song.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    android.util.Log.e("CPDownloadManager", "Download error for ${song.name}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    _tasks.update { it + (song.id to it[song.id]!!.copy(status = DownloadStatus.FAILED)) }
                }
            } finally {
                downloadMutex.remove(song.id)
                downloadJobs.remove(song.id)
            }
        }
        downloadJobs[song.id] = job
    }

    private suspend fun copyToTrackProgress(input: InputStream, output: OutputStream, totalBytes: Long, songId: String) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var bytesRead: Int
        var lastProgressUpdateTime = System.currentTimeMillis()

        while (input.read(buffer).also { bytesRead = it } >= 0) {
            yield() // allow cancellation
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdateTime > 500 && totalBytes > 0) {
                val progress = bytesCopied.toFloat() / totalBytes
                _tasks.update {
                    val task = it[songId]
                    if (task != null) it + (songId to task.copy(progress = progress)) else it
                }
                lastProgressUpdateTime = currentTime
            }
        }
    }

    fun cancelDownload(songId: String) {
        downloadJobs[songId]?.cancel()
        downloadJobs.remove(songId)
        _tasks.update { it - songId }
    }

    companion object {
        private val INVALID_FILE_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")
        private val INVALID_ID_CHARS_REGEX = Regex("[^a-zA-Z0-9_-]")
    }
}
