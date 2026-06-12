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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
                seedColor = playbackViewModel.extractedColor
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
    var isLyricsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(intent) {
        if (intent?.action == "ACTION_SHOW_PLAYER") {
            isPlayerExpanded = true
        }
    }

    val showNav = loginViewModel.isLogged
    val navItems = listOf(Triple("main", "Home", Icons.Filled.Home), Triple("search", "Search", Icons.Filled.Search), Triple("library", "Library", Icons.Filled.LibraryMusic))
    val topLevelRoutes = navItems.map { it.first }
    val isTopLevel = currentDestination?.route in topLevelRoutes
    val hasBottomBar = showNav && isTopLevel // Show bottom bar only on top-level routes
    val hasMiniPlayer = loginViewModel.isLogged && playbackViewModel.currentSong != null
    
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
                if (!isPlayerExpanded && !isLyricsExpanded) {
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
            if (hasBottomBar && !useSideNav && !isPlayerExpanded && !isLyricsExpanded) {
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
            isLyricsExpanded = isLyricsExpanded, onSetLyricsExpanded = { isLyricsExpanded = it },
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
    isLyricsExpanded: Boolean,
    onSetLyricsExpanded: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (!isPlayerExpanded && !isLyricsExpanded) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
        ) {
            // MAIN APP NAVIGATION LAYER
            Row(modifier = Modifier.fillMaxSize()) {
                if (hasBottomBar && !isPlayerExpanded && !isLyricsExpanded && useSideNav) {
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
                        startDestination = if (cp.player.provider.ModuleManager.getAvailableProviders().isEmpty()) "setup" else if (loginViewModel.isLogged) "main" else "login",
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
                                    navController.navigate(if (loginViewModel.isLogged) "main" else "login") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                loginViewModel,
                                onLoginSuccess = {
                                    userViewModel.fetchUserData(); navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                                })
                        }
                        composable("main") {
                            LaunchedEffect(Unit) {
                                if (userViewModel.recommendedSongs.isEmpty()) userViewModel.fetchUserData() else {
                                    socialViewModel.fetchUnreadCount(); socialViewModel.fetchContacts()
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
                                versionName = "1.0.0",
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.recommendedSongs
                                    ); onSetPlayerExpanded(true)
                                },
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate(
                                    "playlist/${p.id}"
                                )
                                },
                                onPersonalFmClick = {
                                    playbackViewModel.playPersonalFm(); onSetPlayerExpanded(true)
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
                                        onSetPlayerExpanded(true)
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
                                unreadMessagesCount = socialViewModel.unreadCount,
                                onNavigateToMessages = { navController.navigate("messages") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToLogin = {
                                    loginViewModel.prepareForNewAccount()
                                    navController.navigate("login")
                                },
                                onLogout = {
                                    loginViewModel.logout()
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                    }
                                },
                                onSwitchAccount = { account ->
                                    loginViewModel.switchAccount(account)
                                    userViewModel.fetchUserData()
                                    socialViewModel.fetchUnreadCount()
                                    socialViewModel.fetchContacts()
                                },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                ),
                                actions = { DownloadIndicator(tasks = tasks) { navController.navigate("downloads") } }
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                searchResults = searchViewModel.searchResults,
                                searchPlaylists = searchViewModel.searchPlaylists,
                                favoriteSongs = userViewModel.favoriteSongs,
                                hotSearches = searchViewModel.hotSearches,
                                searchHistory = searchViewModel.searchHistory,
                                suggestions = searchViewModel.searchSuggestions,
                                searchType = searchViewModel.searchType,
                                isLoading = searchViewModel.isLoading,
                                onSearch = { kw, t -> searchViewModel.search(kw, t) },
                                onSuggestionFetch = { searchViewModel.fetchSuggestions(it) },
                                onClearHistory = { searchViewModel.clearHistory() },
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        searchViewModel.searchResults
                                    ); onSetPlayerExpanded(true)
                                },
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("library") {
                            LibraryScreen(
                                userPlaylists = userViewModel.userPlaylists,
                                onPlaylistClick = { p ->
                                    userViewModel.fetchPlaylistSongs(p.id); navController.navigate("playlist/${p.id}")
                                },
                                onNavigateToLiveSort = { navController.navigate("livesort") },
                                onNavigateToDownloads = { navController.navigate("downloads") },
                                onNavigateToCloud = { navController.navigate("cloud") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("cloud") {
                            LaunchedEffect(Unit) { userViewModel.fetchCloudSongs() }
                            CloudMusicScreen(
                                songs = userViewModel.cloudSongs,
                                favoriteSongs = userViewModel.favoriteSongs,
                                isLoading = userViewModel.isLoading,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.cloudSongs
                                    ); onSetPlayerExpanded(true)
                                },
                                onLikeClick = { s ->
                                    userViewModel.toggleLike(
                                        s.id,
                                        !userViewModel.favoriteSongs.contains(s.id)
                                    )
                                },
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("livesort") {
                            LiveSortScreen(
                                liveSortViewModel = liveSortViewModel,
                                playbackViewModel = playbackViewModel,
                                onBackPressed = { navController.popBackStack() })
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
                                favoriteSongs = userViewModel.favoriteSongs,
                                allPlaylists = userViewModel.userPlaylists,
                                isLoading = userViewModel.isLoading,
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        userViewModel.playlistSongs
                                    ); onSetPlayerExpanded(true)
                                },
                                onPlayAllClick = { l ->
                                    if (l.isNotEmpty()) {
                                        playbackViewModel.playSong(
                                            l[0],
                                            l
                                        ); onSetPlayerExpanded(true)
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
                                completedSongs = completedSongs,
                                onBackPressed = { navController.popBackStack() },
                                bottomContentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = bottomBarHeight
                                )
                            )
                        }
                        composable("downloads") {
                            val tasks by downloadViewModel.tasks.collectAsState()
                            val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
                            val localSongs by downloadViewModel.localSongs.collectAsState()
                            DownloadsScreen(
                                onBackPressed = { navController.popBackStack() },
                                onPlayLocalSong = { s, u ->
                                    val playlist = if (s.id.startsWith("local_")) {
                                        localSongs.map {
                                            cp.player.model.Song(
                                                id = it.songId,
                                                name = it.songName,
                                                artist = it.artist,
                                                album = it.album,
                                                albumArtUrl = it.albumArtUrl
                                            )
                                        }
                                    } else {
                                        downloadedSongs.map { it.song }
                                    }
                                    playbackViewModel.playSong(s, playlist)
                                    onSetPlayerExpanded(true)
                                },
                                downloadedSongs = downloadedSongs,
                                localSongs = localSongs,
                                tasks = tasks,
                                onCancelDownload = { downloadViewModel.cancelDownload(it) },
                                onDeleteLocalSong = { playbackViewModel.deleteLocalSong(it) },
                                onRefreshLocalMusic = { downloadViewModel.refreshLocalMusic() },
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
                                onBackPressed = { navController.popBackStack() })
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
                                onSongClick = { s ->
                                    playbackViewModel.playSong(
                                        s,
                                        viewState.songs
                                    ); onSetPlayerExpanded(true)
                                },
                                onMessageClick = { u, n -> navController.navigate("chat/$u/$n") },
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
                                useWavyProgress = settingsViewModel.useWavyProgress,
                                onUseWavyProgressChange = { settingsViewModel.updateUseWavyProgress(it) },
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
            if (loginViewModel.isLogged && playbackViewModel.currentSong != null && !isLyricsExpanded) {
                AppPlayerOverlay(
                    playbackViewModel = playbackViewModel,
                    userViewModel = userViewModel,
                    socialViewModel = socialViewModel,
                    downloadViewModel = downloadViewModel,
                    settingsViewModel = settingsViewModel,
                    navController = navController,
                    isPlayerExpanded = isPlayerExpanded,
                    onSetPlayerExpanded = onSetPlayerExpanded,
                    onSetLyricsExpanded = onSetLyricsExpanded,
                    useSideNav = useSideNav,
                    hasBottomBar = hasBottomBar,
                    bottomBarOffsetHeightPx = bottomBarOffsetHeightPx,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }

            // LYRICS OVERLAY
            AnimatedVisibility(
                visible = isLyricsExpanded,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    LyricsScreen(
                        lyrics = playbackViewModel.currentLyrics,
                        songName = playbackViewModel.currentSong?.name ?: "Lyrics",
                        currentPosition = playbackViewModel.currentPosition,
                        useCoverColor = settingsViewModel.themeMode == 1 && settingsViewModel.followCoverPlayer,
                        coverColor = playbackViewModel.extractedColor,
                        onBackPressed = { onSetLyricsExpanded(false) }
                    )
                }
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

    BackHandler(enabled = isPlayerExpanded && !isLyricsExpanded) {
        onSetPlayerExpanded(false)
    }
    BackHandler(enabled = isLyricsExpanded) {
        onSetLyricsExpanded(false)
    }
}
