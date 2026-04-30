package cp.player.model

import com.google.gson.annotations.SerializedName

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

data class DownloadTask(
    val song: Song,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val downloadId: Long = -1,
    val localUri: String? = null
)
