package cp.player.util

import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cp.player.model.Song

object MediaItemMapper {

    /**
     * Song → MediaItem 转换。
     *
     * @param song 要转换的歌曲
     * @param localUri 本地文件 URI（如果有），否则构建 cp:// scheme 的远程 URI
     * @param quality 音质参数（用于远程 URI）
     * @param cookie 认证 cookie（用于远程 URI）
     * @return Media3 MediaItem
     */
    fun toMediaItem(
        song: Song,
        localUri: Uri? = null,
        quality: String = "exhigh",
        cookie: String? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUrl?.let { Uri.parse(it) })
            .setExtras(Bundle().apply {
                putString("artistId", song.artistId)
                putLong("duration", song.durationMs)
            })
            .build()

        val mediaUri = localUri ?: Uri.Builder()
            .scheme("cp")
            .authority(song.id)
            .appendQueryParameter("quality", quality)
            .apply { cookie?.let { appendQueryParameter("cookie", it) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * MediaItem → Song 转换。
     *
     * @param mediaItem Media3 MediaItem
     * @return Song 数据对象
     */
    fun toSong(mediaItem: MediaItem): Song {
        return Song(
            id = mediaItem.mediaId,
            name = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
            artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
            album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
            albumArtUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
            artistId = mediaItem.mediaMetadata.extras?.getString("artistId")
        )
    }

    /**
     * 为 local_ 前缀的歌曲 ID 构建 content URI。
     */
    fun buildLocalContentUri(song: Song): Uri? {
        if (song.id.startsWith("local_")) {
            return Uri.parse("content://media/external/audio/media/${song.id.removePrefix("local_")}")
        }
        return null
    }
}
