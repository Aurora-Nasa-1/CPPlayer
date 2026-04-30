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
import cp.player.model.DownloadStatus
import cp.player.model.DownloadTask
import cp.player.model.LocalSongMetadata
import cp.player.model.Song
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
                        val path = cursor.getString(dataColumn)

                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        localList.add(LocalSongMetadata(
                            songId = "local_$id",
                            fileName = fileName,
                            songName = title,
                            artist = artist,
                            album = album,
                            albumArtUrl = uri.toString() // Store URI string here
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CPDownloadManager", "Failed to scan local music", e)
            }
            _localSongs.value = localList
        }
    }

    private fun getStorageStreamAndPath(song: Song): Pair<OutputStream, String>? {
        val userDownloadDir = UserPreferences.getDownloadDir(application)
        val sanitizedName = song.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val sanitizedArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
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
        if (DownloadRegistry.getMetadata(song.id) != null) return
        if (_tasks.value.containsKey(song.id)) return
        if (downloadMutex.putIfAbsent(song.id, true) != null) return

        _tasks.update { it + (song.id to DownloadTask(song, DownloadStatus.DOWNLOADING, 0f, -1L)) }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(application, "Starting download: ${song.name}", Toast.LENGTH_SHORT).show()
        }

        val job = scope.launch {
            try {
                // Get URL
                val params = mutableMapOf("id" to song.id, "level" to quality)
                cookie?.let { params["cookie"] = it }
                val result = ProviderManager.callApi("song/download/url/v1", params)
                val body = com.google.gson.JsonParser.parseString(result).asJsonObject
                var url = JsonUtils.findUrl(body)

                if (url == null) {
                    val fallbackResult = ProviderManager.callApi("song/url/v1", mapOf("id" to song.id, "level" to quality))
                    val fallbackBody = com.google.gson.JsonParser.parseString(fallbackResult).asJsonObject
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

                DownloadRegistry.register(application, song, filePath)
                _completedSongs.update { it + song.id }
                _tasks.update { it + (song.id to it[song.id]!!.copy(status = DownloadStatus.COMPLETED, progress = 1f)) }

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
}
