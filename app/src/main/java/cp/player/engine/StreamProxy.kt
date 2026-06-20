package cp.player.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object StreamProxy {
    private const val TAG = "StreamProxy"
    private const val CACHE_PREFIX = "stream_cache_"
    private const val CACHE_EXPIRY_MS = 1800000L // 30 mins

    /** Default pre-buffer: ~15 seconds at 128kbps = 240KB. Fallback for unknown bitrate. */
    const val DEFAULT_PRE_BUFFER_BYTES = 240 * 1024L

    /**
     * Streams [uri] to a local cache file.
     *
     * @param preBufferBytes How many bytes to download before signaling readiness.
     *   Should be calculated as `bitrate * targetSeconds / 8`. If 0, signals immediately
     *   when the file is fully downloaded (no progressive playback).
     * @param onPreBufferReady Called ONCE when [preBufferBytes] have been written
     *   to [tempFile]. The caller should begin playing the file at this point —
     *   the download continues in the background.
     * @param onProgress Called on every chunk with cumulative bytes written so far.
     * @return The completed cache file, or null on failure.
     */
    suspend fun streamToCache(
        context: Context,
        uri: Uri,
        preBufferBytes: Long = DEFAULT_PRE_BUFFER_BYTES,
        onPreBufferReady: ((File) -> Unit)? = null,
        onProgress: (Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var dataSource: androidx.media3.datasource.DataSource? = null
        try {
            val cacheDir = context.cacheDir
            var ext = "media"

            val cacheDataSourceFactory = DataSourceFactoryProvider.createCacheDataSourceFactory(context)
            dataSource = cacheDataSourceFactory.createDataSource()

            val dataSpec = DataSpec.Builder().setUri(uri).build()
            dataSource.open(dataSpec)

            val resolvedUri = dataSource.uri
            if (resolvedUri?.path != null) {
                val path = resolvedUri.path!!.lowercase()
                ext = when {
                    path.endsWith(".flac") -> "flac"
                    path.endsWith(".mp3") -> "mp3"
                    path.endsWith(".m4a") -> "m4a"
                    path.endsWith(".wav") -> "wav"
                    else -> "media"
                }
            }

            val tempFile = File(cacheDir, "${CACHE_PREFIX}${System.currentTimeMillis()}.${ext}")
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(128 * 1024)
            var bytesDownloaded = 0L
            var preBufferNotified = preBufferBytes <= 0 // If 0, don't wait for pre-buffer

            while (true) {
                val bytesRead = dataSource.read(buffer, 0, buffer.size)
                if (bytesRead == C.RESULT_END_OF_INPUT || bytesRead < 0) break

                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                onProgress(bytesDownloaded)

                // Signal that enough data is buffered for playback to begin
                if (!preBufferNotified && bytesDownloaded >= preBufferBytes) {
                    outputStream.flush()
                    preBufferNotified = true
                    Log.i(TAG, "Pre-buffer threshold reached ($bytesDownloaded / $preBufferBytes bytes), signaling playback start")
                    onPreBufferReady?.invoke(tempFile)
                }
            }
            outputStream.flush()
            outputStream.close()

            // If file is smaller than preBufferBytes, notify now
            if (!preBufferNotified) {
                Log.i(TAG, "File smaller than pre-buffer threshold ($bytesDownloaded < $preBufferBytes bytes), signaling now")
                onPreBufferReady?.invoke(tempFile)
            }

            cleanupCache(cacheDir, tempFile)
            Log.i(TAG, "Download complete: $bytesDownloaded bytes -> ${tempFile.name}")
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Stream failed", e)
            return@withContext null
        } finally {
            try { dataSource?.close() } catch (e: Exception) {}
        }
    }

    private fun cleanupCache(cacheDir: File, currentFile: File) {
        try {
            cacheDir.listFiles { _, name -> name.startsWith(CACHE_PREFIX) }?.forEach { file ->
                if (file.absolutePath != currentFile.absolutePath &&
                    System.currentTimeMillis() - file.lastModified() > CACHE_EXPIRY_MS) {
                    file.delete()
                }
            }
        } catch (e: Exception) {}
    }
}
