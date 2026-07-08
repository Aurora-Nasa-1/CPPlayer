package cp.player.util

import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cp.player.model.Song



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
            artistId = this.mediaMetadata.extras?.getString("artistId")
        )
    }

    /**
     * 为 local_ 或 dsd_ 前缀的歌曲 ID 构建 content URI。
     */
    fun Song.buildLocalContentUri(): Uri? {
        if (this.id.startsWith("local_")) {
            return Uri.parse("content://media/external/audio/media/${this.id.removePrefix("local_")}")
        }
        // DSD 文件使用 file:// URI
        if (this.id.startsWith("dsd_") && this.albumArtUrl?.startsWith("file://") == true) {
            return Uri.parse(this.albumArtUrl)
        }
        return null
    }
