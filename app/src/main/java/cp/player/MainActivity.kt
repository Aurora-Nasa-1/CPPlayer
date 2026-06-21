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
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val socialViewModel: SocialViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val liveSortViewModel: LiveSortViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    val context = LocalContext.current
                    LaunchedEffect(Unit) { playbackViewModel.initController(context) }
                    AppNavigation(loginViewModel, playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel, useSideNav, intent)
                }
            }
        }
    }
}

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
    val navItems = listOf(Triple("main", "Home", Icons.Filled.Home), Triple("search", "Search", Icons.Filled.Search), Triple("library", "Library", Icons.Filled.LibraryMusic))
    val topLevelRoutes = navItems.map { it.first }
    val isTopLevel = currentDestination?.route in topLevelRoutes
    val hasBottomBar = showNav && isTopLevel // Show bottom bar only on top-level routes
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isPlayerExpanded) {
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
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                ShortNavigationBar(
                    modifier = Modifier
                        .offset { IntOffset(0, -bottomBarOffsetHeightPx.value.toInt()) },
                    windowInsets = WindowInsets.navigationBars
                ) {
                    navItems.forEach { (route, label, icon) ->
                        ShortNavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo("main") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppMainContent(
            playbackViewModel, userViewModel, searchViewModel, socialViewModel, downloadViewModel, settingsViewModel, liveSortViewModel,
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
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (!isPlayerExpanded) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
        ) {
            // MAIN APP NAVIGATION LAYER
            Row(modifier = Modifier.fillMaxSize()) {
                if (hasBottomBar && !isPlayerExpanded && useSideNav) {
                    // M3 Expressive Navigation Rail
                    NavigationRail(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .width(88.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Spacer(Modifier.height(32.dp))
                        navItems.forEach { (route, label, icon) ->
                            val isSelected =
                                currentDestination?.hierarchy?.any { it.route == route } == true
                            NavigationRailItem(
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo("main") {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                }

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
                            SearchScreen(
                                searchResults = searchViewModel.searchResults,
                                searchPlaylists = searchViewModel.searchPlaylists,
                                searchArtists = searchViewModel.searchArtists,
                                favoriteSongs = userViewModel.favoriteSongs,
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
                            LibraryScreen(
                                userPlaylists = userViewModel.userPlaylists,
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onNavigateToSettings = { navController.navigate("settings") },

                                // Downloads Data
                                onPlayLocalSong = { s, u ->
                                    val playlist = if (s.id.startsWith("local_")) {
                                        downloadViewModel.localSongs.value.map {
                                            cp.player.model.Song(
                                                id = it.songId,
                                                name = it.songName,
                                                artist = it.artist,
                                                album = it.album,
                                                albumArtUrl = cp.player.manager.LocalMusicManager.getCoverArt(
                                                    navContext, it.songId, it.filePath
                                                )
                                            )
                                        }
                                    } else {
                                        downloadViewModel.downloadedSongs.value.map { metadata ->
                                            val coverUrl = metadata.localCoverPath
                                                ?: cp.player.util.CoverArtExtractor.getOrExtract(
                                                    navContext, metadata.song.id, metadata.filePath
                                                )
                                                ?: metadata.song.albumArtUrl
                                            metadata.song.copy(albumArtUrl = coverUrl)
                                        }
                                    }
                                    playbackViewModel.playSong(s, playlist)
                                    if (!settingsViewModel.playImmediately) onSetPlayerExpanded(true)
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
                                playbackQueue = playbackViewModel.currentQueue,
                                completedSongs = completedSongs,
                                currentSongId = playbackViewModel.currentSong?.id,
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
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
                                )
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
                                onMessageClick = { u, n -> navController.navigate("chat/$u/$n") },
                                currentSongId = playbackViewModel.currentSong?.id,
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
                                onClearCache = { settingsViewModel.clearCache() },
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("logs") { LogViewerScreen(onBackPressed = { navController.popBackStack() }) }
                    }
                }
            } // Close Main Row

            // PLAYER OVERLAY (Replaces NavHost routing for "player")
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

            downloadViewModel.showCellularDownloadDialog?.let { song ->
                AlertDialog(
                    onDismissRequest = { downloadViewModel.showCellularDownloadDialog = null },
                    title = { Text("Cellular Data Warning") },
                    text = { Text("You are currently on a mobile network.") },
                    confirmButton = {
                        Column {
                            TextButton(onClick = {
                                downloadViewModel.downloadSong(song)
                                downloadViewModel.showCellularDownloadDialog = null
                            }) { Text("Continue") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { downloadViewModel.showCellularDownloadDialog = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    BackHandler(enabled = isPlayerExpanded) {
        onSetPlayerExpanded(false)
    }
}
