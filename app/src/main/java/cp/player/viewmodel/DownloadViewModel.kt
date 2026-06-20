package cp.player.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import cp.player.manager.CPDownloadManager
import cp.player.manager.DownloadRegistry
import cp.player.manager.DownloadedSongMetadata
import androidx.compose.runtime.*
import cp.player.model.Song
import cp.player.model.DownloadTask
import cp.player.model.DownloadStatus
import cp.player.util.UserPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : BaseViewModel(application) {
    private val downloadManager = CPDownloadManager(application)
    val tasks: StateFlow<Map<String, DownloadTask>> = downloadManager.tasks
    val completedSongs: StateFlow<Set<String>> = downloadManager.completedSongs
    val downloadedSongs = DownloadRegistry.downloadedSongsFlow
    val localSongs: StateFlow<List<cp.player.model.LocalSongMetadata>> = downloadManager.localSongs

    fun refreshLocalMusic() {
        downloadManager.scanLocalMusic()
    }

    var downloadQuality by mutableStateOf(UserPreferences.getDownloadQuality(application))
    var isFirstDownload by mutableStateOf(UserPreferences.isFirstDownload(application))
    var allowCellularDownload by mutableStateOf(UserPreferences.getAllowCellularDownload(application))
    var showCellularDownloadDialog by mutableStateOf<Song?>(null)

    fun downloadSong(song: Song, quality: String = downloadQuality) {
        downloadManager.downloadSong(song, cookie, quality)
    }

    fun cancelDownload(songId: String) {
        downloadManager.cancelDownload(songId)
    }

    /**
     * 检查歌曲是否已下载（兼容 provider 前缀 key 和裸 songId）。
     */
    fun isSongDownloaded(songId: String): Boolean {
        val providerId = cp.player.provider.ProviderManager.currentProvider?.id ?: "default"
        return DownloadRegistry.isDownloaded(songId, providerId)
    }

    fun updateDownloadQuality(q: String) {
        downloadQuality = q
        UserPreferences.saveDownloadQuality(getApplication(), q)
    }
    fun updateAllowCellularDownload(a: Boolean) { allowCellularDownload = a }
    fun batchDownload(songs: List<Song>, cookie: String?) { songs.forEach { downloadSong(it) } }
}
