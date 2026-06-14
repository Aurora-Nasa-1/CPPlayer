package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PlayArrow
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.util.ImageUtils
import cp.player.model.Song
import cp.player.model.Playlist
import cp.player.model.UserProfile
import cp.player.ui.component.UserAccountDialog
import cp.player.ui.component.SongItem
import cp.player.ui.component.SongCard
import cp.player.ui.component.AppScaffold

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    recommendedSongs: List<Song>,
    recommendedPlaylists: List<Playlist> = emptyList(),
    userPlaylists: List<Playlist>,
    userProfile: UserProfile?,
    versionName: String,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPersonalFmClick: () -> Unit,
    onHeartbeatClick: () -> Unit,
    onLiveSortClick: () -> Unit,
    onLikeClick: (Song) -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    unreadMessagesCount: Int = 0,
    favoriteSongs: List<String>,
    completedSongs: Set<String> = emptySet(),
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (cp.player.util.UserPreferences.SavedAccount) -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp),
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) break
            c = c.baseContext
        }
        c as? ComponentActivity
    }
    val windowSizeClass = if (activity != null) calculateWindowSizeClass(activity) else null
    val widthClass = windowSizeClass?.widthSizeClass ?: WindowWidthSizeClass.Compact
    val isLandscape = widthClass != WindowWidthSizeClass.Compact

    var showAccountDialog by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }

    if (showAccountDialog) {
        UserAccountDialog(
            userProfile = userProfile,
            versionName = versionName,
            onDismiss = { showAccountDialog = false },
            onNavigateToLogs = {
                showAccountDialog = false
                onNavigateToLogs()
            },
            onNavigateToLogin = {
                showAccountDialog = false
                onNavigateToLogin()
            },
            onSwitchAccount = { account ->
                showAccountDialog = false
                onSwitchAccount(account)
            },
            onLogout = {
                showAccountDialog = false
                onLogout()
            }
        )
    }

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }

    AppScaffold(
        title = {
            Text(greeting, fontWeight = FontWeight.Bold)
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            actions()
            IconButton(onClick = onNavigateToMessages) { Icon(Icons.Default.Email, null) }
            IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, null) }
            IconButton(onClick = { showAccountDialog = true }) {
                Surface(modifier = Modifier.size(32.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {
                    if (userProfile?.avatarUrl != null) {
                        AsyncImage(model = userProfile.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp, 
                bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 80.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp) // Large M3 expressive spacing
        ) {
            // "Favorites" Section - Circular items
            item {
                Column {
                    Text("快速访问", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        item {
                            FavoriteCircleItem(
                                title = "私人漫游",
                                icon = { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = onPersonalFmClick
                            )
                        }
                        item {
                            FavoriteCircleItem(
                                title = "心动模式",
                                icon = { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = onHeartbeatClick
                            )
                        }
                        val displayPlaylists = userPlaylists.take(5)
                        items(displayPlaylists) { p ->
                            FavoriteCircleItem(
                                title = p.name,
                                icon = { AsyncImage(model = ImageUtils.getResizedImageUrl(p.coverImgUrl ?: "", 150), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) },
                                onClick = { onPlaylistClick(p) }
                            )
                        }
                    }
                }
            }

            if (recommendedPlaylists.isNotEmpty()) {
                item { Text("推荐歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 16.dp)) {
                        items(items = recommendedPlaylists, key = { "rec_pl_${it.id}" }) { p ->
                            Column(modifier = Modifier.width(160.dp).clickable { onPlaylistClick(p) }) {
                                AsyncImage(
                                    model = ImageUtils.getResizedImageUrl(p.coverImgUrl ?: "", 400),
                                    contentDescription = null,
                                    modifier = Modifier.size(160.dp).clip(MaterialTheme.shapes.extraLarge),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (recommendedSongs.isNotEmpty()) {
                item {
                    DailyMixCard(
                        songs = recommendedSongs.take(5),
                        onSongClick = onSongClick
                    )
                }
            }

            item { Text(stringResource(R.string.recently_played), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

            item {
                val columns = if (widthClass != WindowWidthSizeClass.Compact) 2 else 1
                val items = recommendedSongs.drop(10).take(if (columns > 1) 10 else 5)

                cp.player.ui.component.IndexedVerticalGrid(
                    items = items,
                    columns = columns,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) { _, s, rowIndex, totalRows ->
                    SongItem(
                        song = s,
                        isFavorite = favoriteSongs.contains(s.id),
                        isDownloaded = completedSongs.contains(s.id),
                        onOptionsClick = { selectedSongForOptions = s },
                        onClick = { onSongClick(s) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    selectedSongForOptions?.let { song ->
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
            }
        )
    }
}

@Composable
fun FavoriteCircleItem(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                icon()
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title, 
                style = MaterialTheme.typography.labelLarge, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DailyMixCard(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            // Header with distinct background and overlapping images
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DAILY MIX\nBased on History",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    
                    // Overlapping album art cluster
                    if (songs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(80.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            songs.take(3).reversed().forEachIndexed { index, song ->
                                val offset = (index * 20).dp
                                val size = 64.dp
                                AsyncImage(
                                    model = ImageUtils.getResizedImageUrl(song.albumArtUrl ?: "", 300),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(size)
                                        .offset(x = -offset)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // List of songs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                songs.forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(song.albumArtUrl ?: "", 150),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Footer action
            TextButton(
                onClick = { if (songs.isNotEmpty()) onSongClick(songs.first()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Text("Check all of Daily Mix ->")
            }
        }
    }
}

@Composable
fun ExpressiveListCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, // Large 32dp+ rounded corners
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
