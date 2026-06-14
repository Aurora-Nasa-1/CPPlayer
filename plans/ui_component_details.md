# 流媒体音乐库：Jetpack Compose UI 组件实现细节

根据截图所示的 Material Design 3 (Material You) 风格，我们需要将 UI 拆分为几个核心组件进行实现。

## 1. 整体布局 (Scaffold & Layout)

整个页面应该使用 `Scaffold` 作为基础框架。

```kotlin
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    Scaffold(
        topBar = { LibraryTopBar() },
        bottomBar = { AppBottomNavigation() },
        containerColor = MaterialTheme.colorScheme.background // 柔和的底层背景色
    ) { innerPadding ->
        // 内容区域
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LibraryContent(viewModel)
            
            // 悬浮的 Mini Player，放置在 BottomBar 上方
            MiniPlayer(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}
```

## 2. 顶部过滤器 (Filter Chips)

截图顶部有 "Songs", "Albums" 等过滤器，以及一个类似下拉菜单的按钮和一个设置按钮。

*   **技术选型**：使用 `ScrollableTabRow` (如果希望有滑动指示器) 或者简单的 `LazyRow` + `FilterChip`。这里看起来更像是一组定制的按钮/Chips。
*   **颜色**：选中的 Chip 使用 `primaryContainer` 背景和 `onPrimaryContainer` 文本颜色。未选中的可以只显示文字。

```kotlin
@Composable
fun LibraryTopFilters(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Songs" Button
        Surface(
            shape = RoundedCornerShape(16.dp), // 中等圆角
            color = if (selectedFilter == "Songs") MaterialTheme.colorScheme.primaryContainer 
                    else Color.Transparent,
            modifier = Modifier.clickable { onFilterSelected("Songs") }
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Songs", style = MaterialTheme.typography.labelLarge)
            }
        }
        
        // ... 其他按钮 (下拉箭头, 设置)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { /* TODO */ }) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}
```

## 3. 控制栏 (Shuffle Bar)

*   **Shuffle 按钮**：可以使用 `ExtendedFloatingActionButton` 视觉风格，或者自定义一个 `Surface`。
*   **右侧图标**：`IconButton`，可以包裹在 `Surface` 中以提供微小的色调高度，但这在图中不明显。

```kotlin
@Composable
fun ShuffleControlBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 伪装的 Extended FAB
        Surface(
            shape = CircleShape, // 全圆角
            color = MaterialTheme.colorScheme.secondaryContainer, // 稍微不同于 primary 选中的颜色
            onClick = { /* Shuffle play */ }
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
        
        Row {
            IconButton(onClick = { /* sort */ }) { Icon(Icons.Rounded.Sort, contentDescription = null) }
            IconButton(onClick = { /* view mode */ }) { Icon(Icons.Rounded.GridView, contentDescription = null) }
            // ...
        }
    }
}
```

## 4. 歌曲列表与卡片 (Song List & Card)

这是 UI 的重点。整个列表区域需要给人一种“都在一个大圆角容器中”的感觉，或者每个 Item 本身就是大圆角。
从截图看，似乎是一个带有非常大圆角的统一底色背景，内部包裹了多个 Item。这可以通过 `Surface` 结合 `LazyColumn` 实现。

```kotlin
@Composable
fun LibraryContent(viewModel: LibraryViewModel) {
    // 假设这是通过 Paging 加载的数据
    val songs = viewModel.pagedSongs.collectAsLazyPagingItems()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), // 超大圆角
        color = MaterialTheme.colorScheme.surfaceVariant // 稍深于背景的颜色，形成层次
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { ShuffleControlBar() }
            
            items(songs.itemCount) { index ->
                val song = songs[index]
                if (song != null) {
                    SongListItem(song = song)
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: SongEntity) {
    // 这里不再使用 ListItem，而是自定义 Row 以实现紧凑的圆角卡片感
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* play */ }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图 (带圆角)
        AsyncImage(
            model = song.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)), // 小圆角
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 标题和艺术家
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
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
        
        // 右侧操作菜单 (三个点)
        IconButton(onClick = { /* show context menu */ }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
        }
        
        // 滚动条提示（截图中右侧的深色条，这通常可以通过自定义 Scrollbar 或者让 LazyColumn 开启自带的来实现，但图中更像是一个视觉修饰或字母索引条）
    }
}
```

## 5. 悬浮 Mini Player

这是一个非常鲜艳的组件，利用了动态色彩中高对比度的容器颜色。

```kotlin
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { /* expand player */ },
        shape = CircleShape, // 全圆角
        color = MaterialTheme.colorScheme.tertiaryContainer, // 鲜艳的容器色
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前歌曲封面 (圆形)
            AsyncImage(
                model = "current_song_cover_url",
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZiWEiYUAN",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Kou!",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // 播放控制
            Row {
                IconButton(onClick = { /* previous */ }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                }
                // 播放/暂停按钮可以放在一个实心圆里面
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary, // 更强调的颜色
                    modifier = Modifier.size(40.dp),
                    onClick = { /* toggle play/pause */ }
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow, 
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { /* next */ }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                }
            }
        }
    }
}