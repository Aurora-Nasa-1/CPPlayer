package cp.player.ui.screen

import androidx.compose.material3.CircularProgressIndicator as ContainedLoadingIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.model.UserProfile
import cp.player.ui.component.SongItem
import cp.player.ui.component.PlaylistItem
import cp.player.ui.component.AppScaffold
import androidx.compose.material3.ListItemDefaults
import cp.player.util.ImageUtils

@Composable
fun UserProfileScreen(
    userProfile: UserProfile?,
    playlists: List<Playlist>,
    albums: List<Playlist> = emptyList(),
    songs: List<Song>,
    isArtist: Boolean = false,
    isLoading: Boolean = false,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Playlist) -> Unit = onPlaylistClick,
    onSongClick: (Song) -> Unit,
    onMessageClick: (Long, String) -> Unit,
    currentSongId: String? = null,
    onBackPressed: () -> Unit
) {
    if (userProfile != null && userProfile.userId == 0L) {
         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             ContainedLoadingIndicator()
         }
         return
    }

    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AppScaffold(
        title = userProfile?.nickname ?: "Profile",
        onBackPressed = onBackPressed,
        actions = {
            userProfile?.let {
                if (!isArtist) {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                    IconButton(onClick = { onMessageClick(it.userId, it.nickname) }) {
                        Icon(Icons.Default.Email, contentDescription = "Message")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_confirm_title)) },
                text = { Text(stringResource(R.string.logout_confirm_desc)) },
                confirmButton = {
                    TextButton(onClick = { 
                        showLogoutDialog = false
                        cp.player.util.UserPreferences.saveCookie(context, "")
                        onBackPressed()
                    }) {
                        Text(stringResource(R.string.sign_out_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (isLoading || userProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        } else {
            var isSongsExpanded by remember(userProfile.userId) { mutableStateOf(false) }
            var isAlbumsExpanded by remember(userProfile.userId) { mutableStateOf(false) }
            var isPlaylistsExpanded by remember(userProfile.userId) { mutableStateOf(false) }

            val currentUid = userProfile.userId

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(userProfile.avatarUrl, 300),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            userProfile.nickname,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        userProfile.signature?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (isArtist) {
                                UserStatItem(count = 0, label = "Songs")
                                UserStatItem(count = userProfile.follows, label = "Albums")
                                UserStatItem(count = userProfile.followeds, label = "MVs")
                            } else {
                                UserStatItem(count = userProfile.follows, label = "Follows")
                                UserStatItem(count = userProfile.followeds, label = "Followers")

                            }
                        }
                    }
                }

                if (songs.isNotEmpty()) {
                    val displaySongs = if (isSongsExpanded) songs else songs.take(5)

                    item {
                        Text(
                            "Songs (${songs.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                        itemsIndexed(
                            items = displaySongs,
                            key = { _, song -> "user_${userProfile.userId}_song_${song.id}" },
                            contentType = { _, _ -> "song" }
                        ) { index, song ->
                        SongItem(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            index = index,
                            total = displaySongs.size,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }

                    if (songs.size > 5) {
                        item {
                            TextButton(
                                onClick = { isSongsExpanded = !isSongsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isSongsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    val displayAlbums = if (isAlbumsExpanded) albums else albums.take(5)

                    item {
                        Text(
                            "Albums (${albums.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                        itemsIndexed(
                            items = displayAlbums,
                            key = { _, albumItem -> "user_${userProfile.userId}_album_${albumItem.id}" },
                            contentType = { _, _ -> "playlist" }
                        ) { index, album ->
                        PlaylistItem(
                            playlist = album,
                            onClick = { onAlbumClick(album) },
                            index = index,
                            total = displayAlbums.size,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }

                    if (albums.size > 5) {
                        item {
                            TextButton(
                                onClick = { isAlbumsExpanded = !isAlbumsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isAlbumsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    val displayPlaylists = if (isPlaylistsExpanded) playlists else playlists.take(5)

                    item {
                        Text(
                            "Playlists (${playlists.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                        itemsIndexed(
                            items = displayPlaylists,
                            key = { _, playlistItem -> "user_${userProfile.userId}_playlist_${playlistItem.id}" },
                            contentType = { _, _ -> "playlist" }
                        ) { index, playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                            index = index,
                            total = displayPlaylists.size,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }

                    if (playlists.size > 5) {
                        item {
                            TextButton(
                                onClick = { isPlaylistsExpanded = !isPlaylistsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isPlaylistsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserStatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
