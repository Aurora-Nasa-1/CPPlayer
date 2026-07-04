package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.imageLoader
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
import cp.player.ui.component.PlaylistPreview
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
            // 发现按钮
            Surface(onClick = onNavigateToDiscover, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TrendingUp, contentDescription = "发现")
                }
            }
            Spacer(Modifier.width(4.dp))
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

                // FM入口 - 为你推荐 & 私人FM 双板块
                quickAccessItems.add(
                    QuickAccessItem(
                        selectedIcon = { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary) },
                        preview = {
                            FmQuickAccessContent(
                                onRecommendClick = onHeartbeatClick,
                                onFmClick = onPersonalFmClick
                            )
                        },
                        onNavigate = onPersonalFmClick
                    )
                )

                // 用户歌单
                val displayPlaylists = userPlaylists.take(5)
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
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            val item = quickAccessItems[page]
                            val isCurrentPage = page == pagerState.currentPage

                            // 卡片透明度动画
                            val alpha by animateFloatAsState(
                                targetValue = if (isCurrentPage) 1f else 0.7f,
                                animationSpec = tween(durationMillis = 300)
                            )

                            Box(
                                modifier = Modifier.graphicsLayer { this.alpha = alpha }
                            ) {
                                QuickAccessCard(
                                    previewContent = item.preview,
                                    onArrowClick = item.onNavigate,
                                    onHeartbeatClick = onHeartbeatClick
                                )
                            }
                        }

                        // 底部状态指示器（固定高度，避免颠簸）
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickAccessItems.forEachIndexed { index, item ->
                                    val isSelected = index == pagerState.currentPage

                                    // 指示器颜色动画
                                    val indicatorColor by animateColorAsState(
                                        targetValue = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        animationSpec = tween(durationMillis = 300)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 36.dp else 8.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // 选中时显示图标
                                        if (isSelected) {
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
                        songs = recommendedSongs,
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
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // 从第一首歌的封面提取主色调
    val coverUrl = songs.firstOrNull()?.albumArtUrl
    var dominantColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl.resized(200))
                    .allowHardware(false)
                    .size(128)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap(128, 128)
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(
                        palette.getDominantColor(
                            palette.getMutedColor(0xFF6750A4.toInt())
                        )
                    )
                    dominantColor = Color(color)
                }
            } catch (_: Exception) {}
        }
    }

    val baseColor = dominantColor ?: if (isDark)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    val bgSurface = if (isDark) Color(0xFF121218) else Color(0xFFF0ECF8)
    val gap = 2.dp
    val cardHeight = 240.dp

    // 专辑封面 URL 列表（循环使用）
    val allUrls = remember(songs) { songs.map { it.albumArtUrl ?: "" }.filter { it.isNotEmpty() } }
    fun urlAt(index: Int) = allUrls.getOrElse(index % allUrls.size.coerceAtLeast(1)) { allUrls.firstOrNull() ?: "" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = bgSurface,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // ═══ 模糊封面背景 ═══
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl.resized(600),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .blur(48.dp)
                        .graphicsLayer { alpha = 0.55f },
                    contentScale = ContentScale.Crop
                )
            }

            // ═══ 渐变叠加（延伸到底部，自然过渡到背景色）═══
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to baseColor.copy(alpha = if (isDark) 0.85f else 0.7f),
                                0.35f to baseColor.copy(alpha = if (isDark) 0.9f else 0.8f),
                                0.65f to bgSurface.copy(alpha = 0.6f),
                                0.85f to bgSurface.copy(alpha = 0.9f),
                                1.0f to bgSurface
                            )
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                // ═══ 标题行：标题 + 查看全部按钮 ═══
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.daily_mix_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${songs.size} ${stringResource(R.string.daily_mix_subtitle)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    val dailyMixName = stringResource(R.string.daily_mix_title)
                    Surface(
                        onClick = {
                            onViewAll?.invoke(
                                Playlist(
                                    id = -2L,
                                    name = dailyMixName,
                                    trackCount = songs.size,
                                    coverImgUrl = coverUrl
                                )
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.check_all_daily_mix),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }

                // ═══ 专辑墙：随机马赛克布局（仅 1×1 和 2×2），最多 12 块 ═══
                data class MosaicTile(val col: Int, val row: Int, val span: Int, val urlIndex: Int)

                val gridCols = 6
                val gridRows = 4
                val maxBigTiles = 4
                val maxTiles = 12

                // 根据歌曲列表 + 启动时间生成种子，同一会话内稳定，每次启动不同
                val launchTime = remember { System.currentTimeMillis() }
                val seed = remember(songs, launchTime) {
                    songs.take(6).fold(launchTime) { acc, s -> acc * 31 + s.id.hashCode().toLong() }
                }

                val tiles = remember(seed) {
                    val rng = java.util.Random(seed)
                    val occupied = Array(gridRows) { BooleanArray(gridCols) }
                    val result = mutableListOf<MosaicTile>()
                    var urlIdx = 0

                    // 随机打乱候选位置，放置 2×2 块
                    val bigCandidates = mutableListOf<Pair<Int, Int>>()
                    for (r in 0 until gridRows - 1) {
                        for (c in 0 until gridCols - 1) {
                            bigCandidates.add(c to r)
                        }
                    }
                    bigCandidates.shuffle(rng)

                    var bigPlaced = 0
                    for ((c, r) in bigCandidates) {
                        if (bigPlaced >= maxBigTiles) break
                        if (!occupied[r][c] && !occupied[r][c + 1] && !occupied[r + 1][c] && !occupied[r + 1][c + 1]) {
                            occupied[r][c] = true
                            occupied[r][c + 1] = true
                            occupied[r + 1][c] = true
                            occupied[r + 1][c + 1] = true
                            result.add(MosaicTile(c, r, 2, urlIdx++))
                            bigPlaced++
                        }
                    }

                    // 填充剩余空位为 1×1 块
                    for (r in 0 until gridRows) {
                        for (c in 0 until gridCols) {
                            if (!occupied[r][c]) {
                                result.add(MosaicTile(c, r, 1, urlIdx++))
                            }
                        }
                    }

                    result.take(maxTiles)
                }

                val cellSize = 62.dp
                val gapPx: Float
                val cellPx: Float
                LocalDensity.current.run {
                    gapPx = gap.toPx()
                    cellPx = cellSize.toPx()
                }

                val cornerRadius = 6.dp

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val totalWidthPx = constraints.maxWidth.toFloat()
                    val cellW = (totalWidthPx - (gridCols - 1) * gapPx) / gridCols
                    val totalHeightPx = gridRows * cellPx + (gridRows - 1) * gapPx
                    val totalHeightDp = with(LocalDensity.current) { totalHeightPx.toDp() }

                    Box(modifier = Modifier.fillMaxWidth().height(totalHeightDp)) {
                        for (tile in tiles) {
                            val s = tile.span
                            val wPx = s * cellW + (s - 1) * gapPx
                            val hPx = s * cellPx + (s - 1) * gapPx
                            val xPx = tile.col * (cellW + gapPx)
                            val yPx = tile.row * (cellPx + gapPx)

                            AsyncImage(
                                model = urlAt(tile.urlIndex).resized(300),
                                contentDescription = null,
                                modifier = Modifier
                                    .offset(
                                        x = with(LocalDensity.current) { xPx.toDp() },
                                        y = with(LocalDensity.current) { yPx.toDp() }
                                    )
                                    .size(
                                        width = with(LocalDensity.current) { wPx.toDp() },
                                        height = with(LocalDensity.current) { hPx.toDp() }
                                    )
                                    .clip(RoundedCornerShape(cornerRadius)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
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

/**
 * FM快速访问卡片内容 - 为你推荐 & 私人FM 双板块。
 * 两个并排的渐变色小卡片，替代原来的歌曲列表。
 */
@Composable
fun FmQuickAccessContent(
    onRecommendClick: () -> Unit,
    onFmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 为你推荐板块
        Surface(
            onClick = onRecommendClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
                            ) else listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                            )
                        )
                    )
            ) {
                // 装饰圆形
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .offset(x = 40.dp, y = (-15).dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AutoGraph,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.quick_recommend_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.quick_recommend_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 私人FM板块
        Surface(
            onClick = onFmClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            ) else listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            )
                        )
                    )
            ) {
                // 装饰圆形
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .offset(x = 50.dp, y = 40.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.quick_fm_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.quick_fm_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
