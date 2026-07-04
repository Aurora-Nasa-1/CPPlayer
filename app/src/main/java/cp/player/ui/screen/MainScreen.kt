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
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.util.resized
import cp.player.model.Song
import cp.player.model.Playlist
import cp.player.model.UserProfile
import cp.player.ui.component.UserAccountDialog
import cp.player.ui.component.SongItem
import cp.player.ui.component.SongCard
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.QuickAccessCard
import cp.player.ui.component.SongPreviewList
import cp.player.ui.component.PlaylistPreview
import cp.player.ui.component.DiscoveryPreview
import cp.player.viewmodel.DiscoveryViewModel

/**
 * 快速访问项数据类。
 */
data class QuickAccessItem(
    val selectedIcon: @Composable () -> Unit,
    val preview: @Composable () -> Unit,
    val onNavigate: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    recommendedSongs: List<Song>,
    recommendedPlaylists: List<Playlist> = emptyList(),
    userPlaylists: List<Playlist>,
    userProfile: UserProfile?,
    loginViewModel: cp.player.viewmodel.LoginViewModel,
    discoveryViewModel: DiscoveryViewModel,
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
    currentSongId: String? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiscover: () -> Unit = {},
    onFetchUserData: () -> Unit = {},
    onDownloadClick: ((Song) -> Unit)? = null,
    onViewAllRecent: ((Playlist) -> Unit)? = null,
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
            loginViewModel = loginViewModel,
            userProfile = userProfile,
            onDismiss = { showAccountDialog = false },
            onNavigateToLogs = {
                showAccountDialog = false
                onNavigateToLogs()
            },
            onFetchUserData = onFetchUserData
        )
    }

    // 根据时间选择问候语
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        in 18..22 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_late_night)
    }

    AppScaffold(
        title = {
            Text(greeting, fontWeight = FontWeight.Bold)
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        actions = {
            actions()
            Surface(onClick = onNavigateToMessages, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Email, contentDescription = stringResource(R.string.messages))
                }
            }
            Spacer(Modifier.width(4.dp))
            Surface(onClick = onNavigateToSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
            Spacer(Modifier.width(4.dp))
            Surface(onClick = { showAccountDialog = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.size(32.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {
                        if (userProfile?.avatarUrl != null) {
                            AsyncImage(model = userProfile.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.padding(4.dp))
                        }
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 快速访问区域 - 分页卡片
            item {
                // 准备快速访问项数据
                val quickAccessItems = mutableListOf<QuickAccessItem>()

                // 每日推荐
                if (recommendedSongs.isNotEmpty()) {
                    quickAccessItems.add(
                        QuickAccessItem(
                            selectedIcon = { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) },
                            preview = {
                                SongPreviewList(
                                    songs = recommendedSongs.take(3),
                                    onSongClick = onSongClick,
                                    onArrowClick = onPersonalFmClick
                                )
                            },
                            onNavigate = onPersonalFmClick
                        )
                    )
                }

                // 为你推荐
                if (recommendedSongs.isNotEmpty()) {
                    quickAccessItems.add(
                        QuickAccessItem(
                            selectedIcon = { Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary) },
                            preview = {
                                SongPreviewList(
                                    songs = recommendedSongs.take(3),
                                    onSongClick = onSongClick,
                                    onArrowClick = onHeartbeatClick
                                )
                            },
                            onNavigate = onHeartbeatClick
                        )
                    )
                }

                // 发现
                if (discoveryViewModel.toplists.isNotEmpty()) {
                    quickAccessItems.add(
                        QuickAccessItem(
                            selectedIcon = { Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.tertiary) },
                            preview = {
                                DiscoveryPreview(
                                    toplists = discoveryViewModel.toplists.take(3),
                                    onToplistClick = { playlistId ->
                                        // 这里可以导航到榜单详情
                                    },
                                    onArrowClick = onNavigateToDiscover
                                )
                            },
                            onNavigate = onNavigateToDiscover
                        )
                    )
                }

                // 用户歌单
                val displayPlaylists = userPlaylists.take(3)
                displayPlaylists.forEach { p ->
                    quickAccessItems.add(
                        QuickAccessItem(
                            selectedIcon = {
                                AsyncImage(
                                    model = (p.coverImgUrl ?: "").resized(150),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            preview = {
                                PlaylistPreview(
                                    playlist = p,
                                    onClick = { onPlaylistClick(p) },
                                    onArrowClick = { onPlaylistClick(p) }
                                )
                            },
                            onNavigate = { onPlaylistClick(p) }
                        )
                    )
                }

                if (quickAccessItems.isNotEmpty()) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState { quickAccessItems.size }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 卡片区域（HorizontalPager 实现滑动）
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            pageSpacing = 24.dp
                        ) { page ->
                            val item = quickAccessItems[page]
                            val isCurrentPage = page == pagerState.currentPage

                            // 卡片动画：当前页放大，其他页缩小
                            val scale by animateFloatAsState(
                                targetValue = if (isCurrentPage) 1f else 0.85f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )

                            // 卡片透明度动画
                            val alpha by animateFloatAsState(
                                targetValue = if (isCurrentPage) 1f else 0.5f,
                                animationSpec = tween(durationMillis = 300)
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                            ) {
                                QuickAccessCard(
                                    previewContent = item.preview,
                                    onArrowClick = item.onNavigate
                                )
                            }
                        }

                        // 底部状态指示器动画
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickAccessItems.forEachIndexed { index, item ->
                                val isSelected = index == pagerState.currentPage

                                // 指示器动画
                                val indicatorSize by animateDpAsState(
                                    targetValue = if (isSelected) 32.dp else 8.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )

                                val indicatorColor by animateColorAsState(
                                    targetValue = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    animationSpec = tween(durationMillis = 300)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(indicatorSize)
                                        .clip(CircleShape)
                                        .background(indicatorColor)
                                ) {
                                    // 选中时显示图标
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            item.selectedIcon()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 每日推荐卡片
            if (recommendedSongs.isNotEmpty()) {
                item {
                    DailyMixCard(
                        songs = recommendedSongs.take(5),
                        onSongClick = onSongClick,
                        onViewAll = { playlist -> onPlaylistClick(playlist) }
                    )
                }
            }

            // 推荐歌单区域
            if (recommendedPlaylists.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recommended_playlists),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(items = recommendedPlaylists, key = { "rec_pl_${it.id}" }) { p ->
                            RecommendedPlaylistCard(
                                playlist = p,
                                onClick = { onPlaylistClick(p) }
                            )
                        }
                    }
                }
            }

            // 最近播放区域
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.recently_played),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val recentPlayedName = stringResource(R.string.recently_played)
                    if (onViewAllRecent != null && recommendedSongs.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                onViewAllRecent(
                                    Playlist(
                                        id = -1L,
                                        name = recentPlayedName,
                                        trackCount = recommendedSongs.size
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.view_all),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

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
                        index = rowIndex,
                        total = totalRows,
                        isFavorite = favoriteSongs.contains(s.id),
                        isDownloaded = completedSongs.contains(s.id),
                        isCurrentlyPlaying = s.id == currentSongId,
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
            isDownloaded = completedSongs.contains(song.id),
            onDismissRequest = { selectedSongForOptions = null },
            onPlayClick = {
                onSongClick(song)
                selectedSongForOptions = null
            },
            onFavoriteClick = {
                onLikeClick(song)
                selectedSongForOptions = null
            },
            onDownloadClick = onDownloadClick?.let { dl -> { dl(song) } }
        )
    }
}

@Composable
fun RecommendedPlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.size(160.dp)) {
            AsyncImage(
                model = (playlist.coverImgUrl ?: "").resized(400),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop
            )
            // 底部渐变叠加层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f)
                            ),
                            startY = 200f
                        )
                    )
            )
            // 歌单名称浮在底部
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun DailyMixCard(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onViewAll: ((Playlist) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 暗色模式下使用更亮的背景色
    val cardColor = if (androidx.compose.foundation.isSystemInDarkTheme())
        MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor
    ) {
        Column {
            // 头部：渐变背景 + 重叠封面
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.extraLarge
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
                            text = stringResource(R.string.daily_mix_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.daily_mix_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    // 重叠封面集群
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
                                    model = (song.albumArtUrl ?: "").resized(300),
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

            // 歌曲列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                songs.forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 序号
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        AsyncImage(
                            model = (song.albumArtUrl ?: "").resized(150),
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

            // 底部操作按钮
            val dailyMixName = stringResource(R.string.daily_mix_title)
            FilledTonalButton(
                onClick = {
                    onViewAll?.invoke(
                        Playlist(
                            id = -2L,
                            name = dailyMixName,
                            trackCount = songs.size
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.check_all_daily_mix))
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
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}
