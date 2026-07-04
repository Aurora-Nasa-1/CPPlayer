package cp.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cp.player.R
import androidx.compose.ui.platform.LocalContext
import cp.player.manager.LocalMusicManager

import cp.player.model.Song
import cp.player.ui.component.SongItem
import cp.player.viewmodel.PlaybackViewModel

@Composable
fun CloudMusicContent(
    songs: List<Song>,
    favoriteSongs: List<String>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onLikeClick: (Song) -> Unit,
    onDownloadClick: ((Song) -> Unit)? = null,
    downloadedSongIds: Set<String> = emptySet(),
    playbackViewModel: PlaybackViewModel? = null,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var showBindSheet by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && songs.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.no_cloud_songs),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 16.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongItem(
                        song = song,
                        isFavorite = favoriteSongs.contains(song.id),
                        isCurrentlyPlaying = song.id == playbackViewModel?.currentSong?.id,
                        onClick = { onSongClick(song) },
                        onOptionsClick = { selectedSongForOptions = song },
                        index = index,
                        total = songs.size,
                        containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    selectedSongForOptions?.let { song ->
        // 云盘歌曲的更多选项
        cp.player.ui.component.SongOptionsBottomSheet(
            song = song,
            isFavorite = favoriteSongs.contains(song.id),
            onDismissRequest = { selectedSongForOptions = null },
            onPlayClick = {
                onSongClick(song)
                selectedSongForOptions = null
            },
            onFavoriteClick = {
                onLikeClick(song)
                selectedSongForOptions = null
            },
            onAddToQueueClick = {
                playbackViewModel?.addToQueue(song)
                selectedSongForOptions = null
            },
            onNextClick = {
                playbackViewModel?.insertNext(song)
                selectedSongForOptions = null
            },
            onDownloadClick = onDownloadClick?.let { dl -> { dl(song) } },
            onBindCloudClick = {
                showBindSheet = song
                selectedSongForOptions = null
            },
            isDownloaded = downloadedSongIds.contains(song.id),
            showFavorite = true,
            showShare = false,
            showPlaylist = false,
            showInfo = false,
            showBindCloud = true
        )
    }

    showBindSheet?.let { song ->
        cp.player.ui.component.BindCloudSongSheet(
            songName = song.name,
            artistName = song.artist,
            onSongSelected = { cloudSong ->
                LocalMusicManager.bind(context, song.id, cloudSong)
                showBindSheet = null
            },
            onDismissRequest = { showBindSheet = null }
        )
    }
}
