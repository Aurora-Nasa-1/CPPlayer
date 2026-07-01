package cp.player.usecase

import android.app.Application
import android.os.Environment
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.toBitmap
import com.materialkolor.ktx.themeColor
import cp.player.manager.DownloadRegistry
import cp.player.model.Song
import cp.player.util.DebugLog
import cp.player.util.resized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaMetadataUseCase(private val application: Application) {

    suspend fun extractColorFromUrl(url: String?): Int? = withContext(Dispatchers.IO) {
        if (url.isNullOrEmpty()) return@withContext null
        try {
            val resizedUrl = url.resized(300)
            val loader = SingletonImageLoader.get(application)
            val request = ImageRequest.Builder(application)
                .data(resizedUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()

                // Fallback using Palette in case MCU fails
                var fallbackColor = 0
                val palette = Palette.from(bitmap).generate()
                fallbackColor = palette.getDominantColor(palette.getVibrantColor(palette.getMutedColor(0)))

                // Use official Material Color Utilities for Monet extraction
                try {
                    val imageBitmap = bitmap.asImageBitmap()
                    val themeColor = imageBitmap.themeColor(fallback = androidx.compose.ui.graphics.Color(fallbackColor))
                    return@withContext themeColor.toArgb()
                } catch (e: Exception) {
                    DebugLog.e("Material Color Utilities extraction failed: ${e.message}")
                }

                if (fallbackColor != 0) return@withContext fallbackColor
            }
        } catch (e: Exception) {
            DebugLog.e("Palette extraction failed: ${e.message}")
        }
        return@withContext null
    }

    suspend fun refreshLocalSongs() = withContext(Dispatchers.IO) {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val cpMusicDir = File(musicDir, "CPPlayer")
        val idRegex = Regex("\\[(\\d+)\\]\\.(mp3|flac)$")

        if (cpMusicDir.exists()) {
            cpMusicDir.listFiles { _, name -> name.endsWith(".mp3") || name.endsWith(".flac") }?.forEach { file ->
                val match = idRegex.find(file.name)
                val songId = match?.groupValues?.get(1)

                if (songId != null) {
                    val baseName = file.nameWithoutExtension
                    val nameArtist = baseName.substringBeforeLast(" [").split(" - ")
                    val song = Song(
                        id = songId,
                        name = nameArtist.getOrNull(0) ?: baseName,
                        artist = nameArtist.getOrNull(1) ?: "Unknown",
                        album = "Local Storage"
                    )
                    if (DownloadRegistry.getMetadata(songId) == null) {
                        DownloadRegistry.register(application, song, file.absolutePath)
                    }
                }
            }
        }
    }
}
