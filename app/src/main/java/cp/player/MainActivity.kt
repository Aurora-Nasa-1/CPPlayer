package cp.player

import cp.player.viewmodel.LiveSortViewModel
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import cp.player.ui.component.*
import cp.player.ui.screen.*
import cp.player.ui.theme.CPPlayerTheme
import cp.player.viewmodel.*
import cp.player.util.DebugLog
import io.sentry.Sentry
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    companion object {
        /**
         * 通过 Manifest intent-filter 启动时暂存的 USB 设备。
         * MusicService 创建 UsbAudioManager 后会检查此字段并注册设备。
         */
        @Volatile
        @JvmStatic
        var pendingUsbDevice: android.hardware.usb.UsbDevice? = null
    }

    private val loginViewModel: LoginViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val socialViewModel: SocialViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val liveSortViewModel: LiveSortViewModel by viewModels()
    private val discoveryViewModel: DiscoveryViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 处理通过 Manifest intent-filter 启动的 USB 设备附加事件
        handleUsbDeviceIntent(intent)

        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val useSideNav = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            val snackbarHostState = remember { SnackbarHostState() }
            
            // Allow ViewModels to show snackbars globally if needed
            // But we will just pass it or show standard error states.
            
            CPPlayerTheme(
                pureBlack = settingsViewModel.pureBlackMode,
                themeMode = settingsViewModel.themeMode,
                followCoverApp = settingsViewModel.followCoverApp,
                seedColor = playbackViewModel.extractedColor,
                fontRoundness = settingsViewModel.fontRoundness
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    // 应用回到前台时重新同步 MediaSession 播放状态
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                playbackViewModel.mediaController?.let { playbackViewModel.syncState(it) }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    AppNavigation(loginViewModel, playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel, discoveryViewModel, useSideNav, intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbDeviceIntent(intent)
    }

    /**
     * 处理 USB 设备附加 intent（来自 Manifest intent-filter）。
     * 将设备暂存到 [pendingUsbDevice]，供 MusicService 后续注册。
     */
    private fun handleUsbDeviceIntent(intent: Intent?) {
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: android.hardware.usb.UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE)
            }
            if (device != null) {
                pendingUsbDevice = device
                DebugLog.i("MainActivity: USB device attached via intent: ${device.productName}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    loginViewModel: LoginViewModel,
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    searchViewModel: SearchViewModel,
    socialViewModel: SocialViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    liveSortViewModel: LiveSortViewModel,
    discoveryViewModel: DiscoveryViewModel,
    useSideNav: Boolean,
    intent: Intent? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(intent) {
        if (intent?.action == "ACTION_SHOW_PLAYER") {
            isPlayerExpanded = true
        }
    }

    val showNav = true // 始终显示导航，不再强制登录
    val navHome = stringResource(R.string.nav_home)
    val navSearch = stringResource(R.string.nav_search)
    val navLibrary = stringResource(R.string.nav_library)
    val navItems = listOf(Triple("main", navHome, Icons.Filled.Home), Triple("search", navSearch, Icons.Filled.Search), Triple("library", navLibrary, Icons.Filled.LibraryMusic))
    val topLevelRoutes = navItems.map { it.first }
    val isTopLevel = currentDestination?.route in topLevelRoutes
    val hasBottomBar = showNav && isTopLevel // 仅在顶层路由显示导航项
    val hasMiniPlayer = playbackViewModel.currentSong != null

    // Calculate total bottom padding needed for screens (Mini player + Navigation Bar)
    val bottomBarHeight = when {
        hasBottomBar && hasMiniPlayer -> 150.dp
        hasBottomBar && !hasMiniPlayer -> 90.dp
        !hasBottomBar && hasMiniPlayer -> 80.dp // Only mini player
        else -> 0.dp
    }

    val density = LocalDensity.current
    val bottomBarOffsetHeightPx = remember { mutableStateOf(0f) }
    val navBarHeightPx = with(density) { 90.dp.toPx() }
    val maxOffset = navBarHeightPx + WindowInsets.navigationBars.getBottom(density)

    // 关闭自动隐藏时重置底栏偏移，恢复底栏显示
    LaunchedEffect(settingsViewModel.hideNavbarOnScroll) {
        if (!settingsViewModel.hideNavbarOnScroll) {
            bottomBarOffsetHeightPx.value = 0f
        }
    }

    val nestedScrollConnection = remember(settingsViewModel.hideNavbarOnScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isPlayerExpanded && settingsViewModel.hideNavbarOnScroll) {
                    bottomBarOffsetHeightPx.value = (bottomBarOffsetHeightPx.value + available.y).coerceIn(-maxOffset, 0f)
                }
                return Offset.Zero
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (hasBottomBar && !useSideNav && !isPlayerExpanded) {
                NavigationBar(
                    modifier = Modifier
                        .offset { IntOffset(0, -bottomBarOffsetHeightPx.value.toInt()) },
                    windowInsets = WindowInsets.navigationBars
                ) {
                    navItems.forEach { (route, label, icon) ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) icon else when (route) {
                                        "main" -> Icons.Outlined.Home
                                        "search" -> Icons.Outlined.Search
                                        "library" -> Icons.Outlined.LibraryMusic
                                        else -> icon
                                    },
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo("main") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppMainContent(
            playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel,
            discoveryViewModel,
            innerPadding, nestedScrollConnection, navController, loginViewModel, useSideNav, hasBottomBar, bottomBarHeight, context,
            currentDestination, bottomBarOffsetHeightPx, navItems,
            isPlayerExpanded = isPlayerExpanded, onSetPlayerExpanded = { isPlayerExpanded = it },
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppMainContent(
    playbackViewModel: PlaybackViewModel,
    userViewModel: UserViewModel,
    searchViewModel: SearchViewModel,
    socialViewModel: SocialViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    liveSortViewModel: LiveSortViewModel,
    discoveryViewModel: DiscoveryViewModel,
    innerPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    navController: androidx.navigation.NavHostController,
    loginViewModel: LoginViewModel,
    useSideNav: Boolean,
    hasBottomBar: Boolean,
    bottomBarHeight: Dp,
    context: android.content.Context,
    currentDestination: androidx.navigation.NavDestination?,
    bottomBarOffsetHeightPx: MutableState<Float>,
    navItems: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>,
    isPlayerExpanded: Boolean,
    onSetPlayerExpanded: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        // 全屏展开时驱动主内容下沉的连续进度值
        val expandProgress by animateFloatAsState(
            targetValue = if (isPlayerExpanded) 1f else 0f,
            animationSpec = tween(400, easing = EaseOutCubic),
            label = "contentSinkProgress"
        )

        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (!isPlayerExpanded) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
        ) {
            // MAIN APP NAVIGATION LAYER — 全屏播放器展开时微量下沉
            Row(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - expandProgress * 0.03f
                    scaleX = scale
                    scaleY = scale
                    translationY = expandProgress * 8f
                }
            ) {
                // 横屏时不再显示 NavigationRail，改为在 mini 播放器右侧显示导航胶囊

                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 0.dp)) {
                    NavHost(
                        navController = navController,
                        startDestination = if (cp.player.provider.ModuleManager.getAvailableProviders().isEmpty()) "setup" else "main",
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300, easing = LinearOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300, easing = FastOutLinearInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300, easing = LinearOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300, easing = FastOutLinearInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        composable("setup") {
                            SetupScreen(
                                onSetupComplete = {
                                    navController.navigate("main") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            LaunchedEffect(Unit) {
                                if (userViewModel.recommendedSongs.isEmpty()) userViewModel.fetchUserData() else {
                                    socialViewModel.fetchUnreadCount(); socialViewModel.fetchContacts()
                                }
                                // 加载发现页数据用于快速访问预览
                                if (discoveryViewModel.toplists.isEmpty()) {
                                    discoveryViewModel.fetchToplist()
                                }
                            }
                            // 登录状态变化时刷新用户数据（登录成功后自动加载）
                            LaunchedEffect(loginViewModel.isLogged) {
                                if (loginViewModel.isLogged && userViewModel.recommendedSongs.isEmpty()) {
                                    userViewModel.fetchUserData()
                                    socialViewModel.fetchUnreadCount()
                                    socialViewModel.fetchContacts()
                                }
                            }
                            val tasks by downloadViewModel.tasks.collectAsState()
                            val completedSongs by downloadViewModel.completedSongs.collectAsState()
                            val coroutineScope = rememberCoroutineScope()
                            MainScreen(
                                recommendedSongs = userViewModel.recommendedSongs,
                                recommendedPlaylists = userViewModel.recommendedPlaylists,
                                userPlaylists = userViewModel.userPlaylists,
                                userProfile = userViewModel.userProfile,
                                loginViewModel = loginViewModel,
                                discoveryViewModel = discoveryViewModel,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.recommendedSongs
                                    ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onPlaylistClick = { p ->
                                    if (p.id == -2L) {
                                        // 每日推荐：使用本地推荐歌曲数据，不调用 API
                                        userViewModel.setLocalPlaylistDetail(p, userViewModel.recommendedSongs)
                                        navController.navigate("playlist/${p.id}")
                                    } else {
                                        userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                    }
                                },
                                onViewAllRecent = { p ->
                                    // 最近播放：使用本地推荐歌曲数据
                                    userViewModel.setLocalPlaylistDetail(p, userViewModel.recommendedSongs)
                                    navController.navigate("playlist/${p.id}")
                                },
                                onPersonalFmClick = {
                                    playbackViewModel.playPersonalFm(); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onHeartbeatClick = {
                                    if (userViewModel.favoriteSongs.isNotEmpty()) {
                                        val pid =
                                            if (userViewModel.likedSongsPlaylistId != 0L) userViewModel.likedSongsPlaylistId else userViewModel.userPlaylists.find {
                                                it.name.contains("喜欢的音乐")
                                            }?.id ?: userViewModel.userPlaylists.firstOrNull()?.id ?: 0L
                                        playbackViewModel.playHeartbeat(
                                            userViewModel.favoriteSongs[0],
                                            pid
                                        )
                                        if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onLiveSortClick = { navController.navigate("livesort") },
                                 onLikeClick = { s ->
                                     val isLiked = !userViewModel.favoriteSongs.contains(s.id)
                                     userViewModel.toggleLike(s.id, isLiked)
                                     coroutineScope.launch {
                                         snackbarHostState.showSnackbar(if (isLiked) "Added to favorites" else "Removed from favorites")
                                     }
                                 },
                                favoriteSongs = userViewModel.favoriteSongs,
                                completedSongs = completedSongs,
                                currentSongId = playbackViewModel.currentSong?.id,
                                unreadMessagesCount = socialViewModel.unreadCount,
                                onNavigateToMessages = { navController.navigate("messages") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToDiscover = { navController.navigate("discover") },
                                onFetchUserData = {
                                    userViewModel.fetchUserData()
                                    socialViewModel.fetchUnreadCount()
                                    socialViewModel.fetchContacts()
                                },
                                onDownloadClick = { s -> downloadViewModel.downloadSong(s) },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )

                            // 仅在有活跃下载任务时显示下载指示器
                            val activeTasks = tasks.values.filter {
                                it.status == cp.player.model.DownloadStatus.DOWNLOADING || it.status == cp.player.model.DownloadStatus.PENDING
                            }
                            if (activeTasks.isNotEmpty()) {
                                DownloadIndicator(tasks = tasks)
                            }
                        }
                        composable("search") {
                            val completedSongs by downloadViewModel.completedSongs.collectAsState()
                            val searchScope = androidx.compose.runtime.rememberCoroutineScope()
                            SearchScreen(
                                searchResults = searchViewModel.searchResults,
                                searchPlaylists = searchViewModel.searchPlaylists,
                                searchArtists = searchViewModel.searchArtists,
                                favoriteSongs = userViewModel.favoriteSongs,
                                subscribedPlaylists = userViewModel.subscribedPlaylists,
                                hotSearches = searchViewModel.hotSearches,
                                searchHistory = searchViewModel.searchHistory,
                                suggestions = searchViewModel.searchSuggestions,
                                searchQuery = searchViewModel.searchQuery,
                                searchType = searchViewModel.searchType,
                                isLoading = searchViewModel.isSearching,
                                onSearch = { kw, t -> searchViewModel.search(kw, t) },
                                onSuggestionFetch = { searchViewModel.fetchSuggestions(it) },
                                onClearHistory = { searchViewModel.clearHistory() },
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        searchViewModel.searchResults
                                    ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onAlbumClick = { album ->
                                    userViewModel.fetchAlbumSongs(album.id); navController.navigate("album/${album.id}")
                                },
                                onArtistClick = { artist ->
                                    userViewModel.fetchOtherUserProfile(artist.id); navController.navigate("user/${artist.id}")
                                },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                onDownloadClick = { s -> downloadViewModel.downloadSong(s) },
                                onPlaylistPlayAllClick = { p ->
                                    searchScope.launch {
                                        val songs = userViewModel.getPlaylistSongs(p.id)
                                        if (songs.isNotEmpty()) {
                                            playbackViewModel.playSong(songs[0], songs)
                                            if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                        }
                                    }
                                },
                                onPlaylistAddToQueueClick = { p ->
                                    searchScope.launch {
                                        val songs = userViewModel.getPlaylistSongs(p.id)
                                        if (songs.isNotEmpty()) playbackViewModel.addSongsToQueue(songs)
                                    }
                                },
                                onPlaylistSubscribeClick = { p ->
                                    userViewModel.subscribePlaylist(p.id)
                                },
                                onPlaylistUnsubscribeClick = { p ->
                                    userViewModel.unsubscribePlaylist(p.id)
                                },
                                completedSongs = completedSongs,
                                currentSongId = playbackViewModel.currentSong?.id,
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("library") {
                            // 当进入 library 页面时，如果云盘数据为空，自动加载
                            LaunchedEffect(Unit) {
                                userViewModel.fetchCloudSongs()
                            }
                            val navContext = LocalContext.current
                            val libraryCoroutineScope = rememberCoroutineScope()
                            LibraryScreen(
                                userPlaylists = userViewModel.userPlaylists,
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onNavigateToSettings = { navController.navigate("settings") },

                                // Downloads Data
                                onPlayLocalSong = { s, u ->
                                    libraryCoroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val playlist = if (s.id.startsWith("local_")) {
                                            downloadViewModel.localSongs.value.map {
                                                cp.player.model.Song(
                                                    id = it.songId,
                                                    name = it.songName,
                                                    artist = it.artist,
                                                    album = it.album,
                                                    albumArtUrl = it.albumArtUrl
                                                )
                                            }
                                        } else {
                                            downloadViewModel.downloadedSongs.value.map { metadata ->
                                                val coverUrl = metadata.localCoverPath ?: metadata.song.albumArtUrl
                                                metadata.song.copy(albumArtUrl = coverUrl)
                                            }
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            playbackViewModel.playSong(s, playlist)
                                            if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                        }
                                    }
                                },
                                downloadedSongs = downloadViewModel.downloadedSongs.collectAsState().value,
                                localSongs = downloadViewModel.localSongs.collectAsState().value,
                                downloadTasks = downloadViewModel.tasks.collectAsState().value,
                                onCancelDownload = { downloadViewModel.cancelDownload(it) },
                                onDeleteLocalSong = { playbackViewModel.deleteLocalSong(it) },
                                onRefreshLocalMusic = { downloadViewModel.refreshLocalMusic() },
                                
                                // Cloud Data
                                cloudSongs = userViewModel.cloudSongs,
                                favoriteSongs = userViewModel.favoriteSongs,
                                isCloudLoading = userViewModel.isCloudLoading,
                                onCloudSongClick = { s ->
                                    playbackViewModel.playSong(s, userViewModel.cloudSongs)
                                    if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onCloudLikeClick = { s ->
                                    userViewModel.toggleLike(s.id, !userViewModel.favoriteSongs.contains(s.id))
                                },
                                onFetchCloudSongs = { userViewModel.fetchCloudSongs() },
                                
                                // Live Sort Data
                                liveSortViewModel = liveSortViewModel,
                                playbackViewModel = playbackViewModel,
                                userViewModel = userViewModel,
                                downloadViewModel = downloadViewModel,

                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("playlist/{playlistId}") { backStackEntry ->
                            val pid =
                                backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                            LaunchedEffect(pid) {
                                if (userViewModel.currentPlaylistMetadata?.id != pid) userViewModel.fetchPlaylistSongs(
                                    pid
                                )
                            }
                            val completedSongs by downloadViewModel.completedSongs.collectAsState()
                            val isPlaylistOwner = (userViewModel.currentPlaylistMetadata?.creatorUserId ?: 0L) == (userViewModel.userProfile?.userId ?: -1L)
                            val isPlaylistFavorite = userViewModel.subscribedPlaylists.contains(pid)
                            PlaylistDetailScreen(
                                playlist = userViewModel.currentPlaylistMetadata
                                    ?: cp.player.model.Playlist(pid, "Loading...", null, 0),
                                songs = userViewModel.playlistSongs,
                                hasMoreSongs = userViewModel.hasMorePlaylistSongs,
                                isFetchingMore = userViewModel.isFetchingMorePlaylistSongs,
                                onLoadMore = {
                                    userViewModel.fetchPlaylistSongs(pid, isLoadMore = true)
                                },
                                favoriteSongs = userViewModel.favoriteSongs,
                                allPlaylists = userViewModel.userPlaylists,
                                isLoading = userViewModel.isPlaylistLoading,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.playlistSongs
                                    ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onPlayAllClick = { l ->
                                    if (l.isNotEmpty()) {
                                        playbackViewModel.playSong(
                                            l[0],
                                            l
                                        ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onQueueAllClick = { l -> l.forEach { playbackViewModel.addToQueue(it) } },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                onAddToPlaylist = { ids, targetPid ->
                                    userViewModel.addSongsToPlaylist(
                                        targetPid,
                                        ids,
                                        null
                                    )
                                },
                                onRemoveFromPlaylist = { ids ->
                                    userViewModel.removeSongsFromPlaylist(
                                        pid,
                                        ids,
                                        null
                                    )
                                },
                                onBatchDownload = { l -> downloadViewModel.batchDownload(l, null) },
                                onFetchSourcePlaylistSongs = { sourcePid ->
                                    userViewModel.getPlaylistSongs(sourcePid)
                                },
                                isOwner = isPlaylistOwner,
                                isFavorite = isPlaylistFavorite,
                                onSubscribeClick = { userViewModel.subscribePlaylist(pid) },
                                onUnsubscribeClick = { userViewModel.unsubscribePlaylist(pid) },
                                playbackQueue = playbackViewModel.currentQueue,
                                completedSongs = completedSongs,
                                currentSongId = playbackViewModel.currentSong?.id,
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                ),
                                useSideNav = useSideNav
                            )
                        }
                        composable("album/{albumId}") { backStackEntry ->
                            val albumId =
                                backStackEntry.arguments?.getString("albumId")?.toLongOrNull() ?: 0L
                            LaunchedEffect(albumId) {
                                if (userViewModel.currentAlbumMetadata?.id != albumId) userViewModel.fetchAlbumSongs(
                                    albumId
                                )
                            }
                            val completedSongs by downloadViewModel.completedSongs.collectAsState()
                            PlaylistDetailScreen(
                                playlist = userViewModel.currentAlbumMetadata
                                    ?: cp.player.model.Playlist(albumId, "Loading...", null, 0),
                                songs = userViewModel.albumSongs,
                                hasMoreSongs = false,
                                isFetchingMore = false,
                                onLoadMore = {},
                                favoriteSongs = userViewModel.favoriteSongs,
                                allPlaylists = userViewModel.userPlaylists,
                                isLoading = userViewModel.isAlbumLoading,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.albumSongs
                                    ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onPlayAllClick = { l ->
                                    if (l.isNotEmpty()) {
                                        playbackViewModel.playSong(
                                            l[0],
                                            l
                                        ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onQueueAllClick = { l -> l.forEach { playbackViewModel.addToQueue(it) } },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                onAddToPlaylist = { ids, targetPid ->
                                    userViewModel.addSongsToPlaylist(
                                        targetPid,
                                        ids,
                                        null
                                    )
                                },
                                onRemoveFromPlaylist = { },
                                onBatchDownload = { l -> downloadViewModel.batchDownload(l, null) },
                                onFetchSourcePlaylistSongs = { sourcePid ->
                                    userViewModel.getPlaylistSongs(sourcePid)
                                },
                                playbackQueue = playbackViewModel.currentQueue,
                                completedSongs = completedSongs,
                                currentSongId = playbackViewModel.currentSong?.id,
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                ),
                                useSideNav = useSideNav
                            )
                        }
                        composable("messages") {
                            LaunchedEffect(Unit) { socialViewModel.fetchContacts() }; ContactListScreen(
                            contacts = socialViewModel.contacts,
                            onContactClick = { c -> navController.navigate("chat/${c.userId}/${c.nickname}") },
                            onAvatarClick = { uid ->
                                userViewModel.fetchOtherUserProfile(uid); navController.navigate("user/$uid")
                            },
                            onBackPressed = { navController.popBackStack() })
                        }
                        composable("chat/{userId}/{nickname}") { backStackEntry ->
                            val uid =
                                backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                            val name = backStackEntry.arguments?.getString("nickname") ?: ""
                            LaunchedEffect(uid) {
                                socialViewModel.fetchMessages(uid); socialViewModel.markMessageAsRead(
                                uid
                            )
                            }
                            ChatScreen(
                                recipientUid = uid,
                                recipientName = name,
                                messages = socialViewModel.chatMessages,
                                onSendMessage = { t -> socialViewModel.sendMessage(uid, t) },
                                onAvatarClick = { u ->
                                    userViewModel.fetchOtherUserProfile(u); navController.navigate("user/$u")
                                },
                                onBackPressed = { navController.popBackStack() },
                                miniPlayerHeight = bottomBarHeight)
                        }
                        composable("user/{userId}") { backStackEntry ->
                            val uid =
                                backStackEntry.arguments?.getString("userId")?.toLongOrNull() ?: 0L
                            LaunchedEffect(uid) { userViewModel.fetchOtherUserProfile(uid) }
                            val viewState = userViewModel.otherUserViewState
                            val completedSongs by downloadViewModel.completedSongs.collectAsState()
                            UserProfileScreen(
                                userProfile = viewState.profile,
                                playlists = viewState.playlists,
                                albums = viewState.albums,
                                songs = viewState.songs,
                                isArtist = viewState.isArtist,
                                isLoading = viewState.isLoading,
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onAlbumClick = { a ->
                                    userViewModel.fetchAlbumSongs(a.id); navController.navigate("album/${a.id}")
                                },
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        viewState.songs
                                    ); if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                onDownloadClick = { s -> downloadViewModel.downloadSong(s) },
                                onPlayAllClick = { songs ->
                                    if (songs.isNotEmpty()) {
                                        playbackViewModel.playSong(songs[0], songs)
                                        if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onAddToQueueAllClick = { songs ->
                                    if (songs.isNotEmpty()) playbackViewModel.addSongsToQueue(songs)
                                },
                                onPlaylistSubscribeClick = { p ->
                                    userViewModel.subscribePlaylist(p.id)
                                },
                                onPlaylistUnsubscribeClick = { p ->
                                    userViewModel.unsubscribePlaylist(p.id)
                                },
                                onMessageClick = { u, n -> navController.navigate("chat/$u/$n") },
                                currentSongId = playbackViewModel.currentSong?.id,
                                favoriteSongs = userViewModel.favoriteSongs,
                                subscribedPlaylists = userViewModel.subscribedPlaylists,
                                completedSongs = completedSongs,
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                ),
                                onBackPressed = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                currentQualityWifi = settingsViewModel.qualityWifi,
                                onQualityWifiChange = settingsViewModel::updateQualityWifi,
                                currentQualityCellular = settingsViewModel.qualityCellular,
                                onQualityCellularChange = settingsViewModel::updateQualityCellular,
                                downloadQuality = downloadViewModel.downloadQuality,
                                onDownloadQualityChange = downloadViewModel::updateDownloadQuality,
                                fadeDuration = settingsViewModel.fadeDuration,
                                onFadeChange = settingsViewModel::updateFade,
                                fadeMode = settingsViewModel.fadeMode,
                                onFadeModeChange = settingsViewModel::updateFadeMode,
                                autoAudioFocus = settingsViewModel.autoAudioFocus,
                                onAutoAudioFocusChange = settingsViewModel::updateAutoAudioFocus,
                                cacheSize = settingsViewModel.cacheSize,
                                onCacheSizeChange = { settingsViewModel.updateCache(it) },
                                useCellularCache = settingsViewModel.useCellularCache,
                                onUseCellularCacheChange = { settingsViewModel.updateUseCellular(it) },
                                allowCellularDownload = downloadViewModel.allowCellularDownload,
                                onAllowCellularDownloadChange = {
                                    downloadViewModel.updateAllowCellularDownload(
                                        it
                                    )
                                },
                                pureBlackMode = settingsViewModel.pureBlackMode,
                                onPureBlackModeChange = { settingsViewModel.updatePureBlackMode(it) },
                                themeMode = settingsViewModel.themeMode,
                                onThemeModeChange = { settingsViewModel.updateThemeMode(it) },
                                followCoverApp = settingsViewModel.followCoverApp,
                                onFollowCoverAppChange = { settingsViewModel.updateFollowCoverApp(it) },
                                followCoverMini = settingsViewModel.followCoverMini,
                                onFollowCoverMiniChange = { settingsViewModel.updateFollowCoverMini(it) },
                                followCoverPlayer = settingsViewModel.followCoverPlayer,
                                onFollowCoverPlayerChange = {
                                    settingsViewModel.updateFollowCoverPlayer(
                                        it
                                    )
                                },
                                useFluidBackground = settingsViewModel.useFluidBackground,
                                onUseFluidBackgroundChange = {
                                    settingsViewModel.updateUseFluidBackground(
                                        it
                                    )
                                },
                                audioFocusMode = settingsViewModel.audioFocusMode,
                                onAudioFocusModeChange = { settingsViewModel.updateAudioFocusMode(it) },
                                allowDucking = settingsViewModel.allowDucking,
                                onAllowDuckingChange = { settingsViewModel.updateAllowDucking(it) },
                                pauseOnNoisy = settingsViewModel.pauseOnNoisy,
                                onPauseOnNoisyChange = { settingsViewModel.updatePauseOnNoisy(it) },
                                autoResumeUsbAudio = settingsViewModel.autoResumeUsbAudio,
                                onAutoResumeUsbAudioChange = { settingsViewModel.updateAutoResumeUsbAudio(it) },
                                downloadDir = settingsViewModel.downloadDir,
                                onDownloadDirChange = { settingsViewModel.updateDownloadPath(it) },
                                audioEngine = settingsViewModel.audioEngine,
                                onAudioEngineChange = { settingsViewModel.updateAudioEngine(it) },
                                dsdOutputMode = settingsViewModel.dsdOutputMode,
                                onDsdOutputModeChange = { settingsViewModel.updateDsdOutputMode(it) },
                                dapBitPerfect = settingsViewModel.dapBitPerfect,
                                onDapBitPerfectChange = { settingsViewModel.updateDapBitPerfect(it) },
                                usbExclusive = settingsViewModel.usbExclusive,
                                onUsbExclusiveChange = { settingsViewModel.updateUsbExclusive(it) },
                                fontRoundness = settingsViewModel.fontRoundness,
                                onFontRoundnessChange = { settingsViewModel.updateFontRoundness(it) },
                                playImmediately = settingsViewModel.playImmediately,
                                onPlayImmediatelyChange = { settingsViewModel.updatePlayImmediately(it) },
                                lyricsSource = settingsViewModel.lyricsSource,
                                onLyricsSourceChange = { settingsViewModel.updateLyricsSource(it) },
                                amllPlatform = settingsViewModel.amllPlatform,
                                onAmllPlatformChange = { settingsViewModel.updateAmllPlatform(it) },
                                hideNavbarOnScroll = settingsViewModel.hideNavbarOnScroll,
                                onHideNavbarOnScrollChange = { settingsViewModel.updateHideNavbarOnScroll(it) },
                                wavyProgress = settingsViewModel.wavyProgress,
                                onWavyProgressChange = { settingsViewModel.updateWavyProgress(it) },
                                restoreLastQueue = settingsViewModel.restoreLastQueue,
                                onRestoreLastQueueChange = { settingsViewModel.updateRestoreLastQueue(it) },
                                onClearCache = { settingsViewModel.clearCache() },
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                ),
                                isPlayerExpanded = isPlayerExpanded,
                                useSideNav = useSideNav
                            )
                        }
                        composable("discover") {
                            LaunchedEffect(Unit) {
                                discoveryViewModel.fetchDiscoveryData()
                                discoveryViewModel.fetchToplist()
                            }
                            DiscoveryScreen(
                                toplists = discoveryViewModel.toplists,
                                personalizedPlaylists = discoveryViewModel.personalizedPlaylists,
                                personalizedNewSongs = discoveryViewModel.personalizedNewSongs,
                                highqualityPlaylists = discoveryViewModel.highqualityPlaylists,
                                topSongs = discoveryViewModel.topSongs,
                                isDiscoveryLoading = discoveryViewModel.isDiscoveryLoading,
                                onToplistClick = { entry ->
                                    discoveryViewModel.fetchRankingDetail(entry.id)
                                    navController.navigate("ranking/${entry.id}")
                                },
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id)
                                    navController.navigate("playlist/${p.id}")
                                },
                                onSongClick = { s ->
                                    playbackViewModel.playSong(s, discoveryViewModel.personalizedNewSongs)
                                    if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onViewAllTopSongs = {
                                    discoveryViewModel.fetchTopSongs()
                                },
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("ranking/{playlistId}") { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                            RankingDetailScreen(
                                playlist = discoveryViewModel.rankingDetailMetadata,
                                songs = discoveryViewModel.rankingDetailSongs,
                                isLoading = discoveryViewModel.isRankingDetailLoading,
                                favoriteSongs = userViewModel.favoriteSongs,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(s, discoveryViewModel.rankingDetailSongs)
                                    if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                },
                                onPlayAllClick = { songs ->
                                    if (songs.isNotEmpty()) {
                                        playbackViewModel.playSong(songs[0], songs)
                                        if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onShufflePlayClick = { songs ->
                                    if (songs.isNotEmpty()) {
                                        val shuffled = songs.shuffled()
                                        playbackViewModel.playSong(shuffled[0], shuffled)
                                        if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
                                    }
                                },
                                onLikeClick = { s ->
                                    val isLiked = !userViewModel.favoriteSongs.contains(s.id)
                                    userViewModel.toggleLike(s.id, isLiked)
                                },
                                onBackPressed = { navController.popBackStack() }
                            )
                        }
                        composable("logs") { LogViewerScreen(onBackPressed = { navController.popBackStack() }) }
                    }
                }

            } // Close Main Row

            // 竖屏播放器覆盖层（保持原有 scrim + 全屏展开逻辑）
            if (playbackViewModel.currentSong != null) {
                AppPlayerOverlay(
                    playbackViewModel = playbackViewModel,
                    userViewModel = userViewModel,
                    socialViewModel = socialViewModel,
                    downloadViewModel = downloadViewModel,
                    settingsViewModel = settingsViewModel,
                    navController = navController,
                    isPlayerExpanded = isPlayerExpanded,
                    onSetPlayerExpanded = onSetPlayerExpanded,
                    useSideNav = useSideNav,
                    hasBottomBar = hasBottomBar,
                    bottomBarOffsetHeightPx = bottomBarOffsetHeightPx,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }

            // 横屏导航胶囊 — 始终可见，不依赖 mini 播放器
            if (useSideNav && !isPlayerExpanded) {
                NavigationCapsule(
                    navItems = navItems,
                    currentRoute = currentDestination?.route,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("main") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }

            downloadViewModel.showCellularDownloadDialog?.let { song ->
                AlertDialog(
                    onDismissRequest = { downloadViewModel.showCellularDownloadDialog = null },
                    title = { Text(stringResource(R.string.cellular_warning_title)) },
                    text = { Text(stringResource(R.string.cellular_warning_message)) },
                    confirmButton = {
                        Column {
                            TextButton(onClick = {
                                downloadViewModel.downloadSong(song)
                                downloadViewModel.showCellularDownloadDialog = null
                            }) { Text(stringResource(R.string.continue_action)) }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadViewModel.showCellularDownloadDialog = null }) { Text(stringResource(R.string.cancel)) }
                    }
                )
            }
        }
    }

    BackHandler(enabled = isPlayerExpanded) {
        onSetPlayerExpanded(false)
    }
}
