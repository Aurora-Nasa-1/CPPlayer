package cp.player.util

import android.content.Context
import android.os.Bundle
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cp.player.model.Song
import java.io.File



    /**
     * Song → MediaItem 转换。
     *
     * @param localUri 本地文件 URI（如果有），否则构建 cp:// scheme 的远程 URI
     * @param quality 音质参数（用于远程 URI）
     * @param cookie 认证 cookie（用于远程 URI）
     * @return Media3 MediaItem
     */
    fun Song.toMediaItem(
        localUri: Uri? = null,
        quality: String = "exhigh",
        cookie: String? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.name)
            .setArtist(this.artist)
            .setAlbumTitle(this.album)
            .setArtworkUri(this.albumArtUrl?.let { Uri.parse(it) })
            .setExtras(Bundle().apply {
                putString("artistId", this@toMediaItem.artistId)
                putLong("duration", this@toMediaItem.durationMs)
            })
            .build()

        val mediaUri = localUri ?: Uri.Builder()
            .scheme("cp")
            .authority(this.id)
            .appendQueryParameter("quality", quality)
            .apply { cookie?.let { appendQueryParameter("cookie", it) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * MediaItem → Song 转换。
     *
     * @return Song 数据对象
     */
    fun MediaItem.toSong(): Song {
        return Song(
            id = this.mediaId,
            name = this.mediaMetadata.title?.toString() ?: "Unknown",
            artist = this.mediaMetadata.artist?.toString() ?: "Unknown",
            album = this.mediaMetadata.albumTitle?.toString() ?: "Unknown",
            albumArtUrl = this.mediaMetadata.artworkUri?.toString(),
            artistId = this.mediaMetadata.extras?.getString("artistId"),
            durationMs = this.mediaMetadata.extras?.getLong("duration") ?: 0L
        )
    }

    /**
     * 为 local_ 或 dsd_ 前缀的歌曲 ID 构建 content:// URI。
     * DSD 文件通过 FileProvider 提供 content:// URI（Android 10+ 禁止 file:// URI）。
     */
    fun Song.buildLocalContentUri(context: Context? = null): Uri? {
        if (this.id.startsWith("local_")) {
            return Uri.parse("content://media/external/audio/media/${this.id.removePrefix("local_")}")
        }
        // DSD 文件通过 FileProvider 获取 content:// URI
        if (this.id.startsWith("dsd_")) {
            val path = this.albumArtUrl?.removePrefix("file://")
            if (!path.isNullOrBlank() && context != null) {
                val file = File(path)
                if (file.exists()) {
                    return try {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } catch (e: Exception) {
                        android.util.Log.w("MediaItemMapper", "FileProvider failed for DSD file: $path", e)
                        null
                    }
                }
            }
        }
        return null
    }
