# 流媒体音乐库：数据层架构规划 (Paging 3 + Room)

针对流媒体平台的特点（数据量大、需离线访问、状态同步要求高），我们需要将数据层改造成基于 **单一信任源 (Single Source of Truth, SSOT)** 的架构。

## 1. 架构概览

我们将采用 **Paging 3 + Room + Retrofit (RemoteMediator)** 的经典离线优先架构。

*   **UI Layer (Compose)**：观察 `Flow<PagingData<Song>>`，负责界面的响应式更新。
*   **Data Layer (Repository)**：负责协调本地数据库和远端网络请求，暴露统一的数据流给 ViewModel。
*   **Local Source (Room)**：作为**单一信任源**。UI 永远只显示 Room 中的数据。
*   **Remote Source (Retrofit/Backend)**：负责从服务器获取数据分页。
*   **RemoteMediator**：Paging 3 的核心组件。当 Room 中的数据耗尽时，它会被触发去请求远端 API，并将结果存入 Room，随后 Room 的变动会自动通知 UI。

## 2. 核心组件设计

### 2.1 实体模型 (Entity & Model)

需要区分网络响应模型 (DTO) 和本地数据库实体 (Entity)。这里重点看本地实体，因为它是 UI 展示的基石。

```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumInfo: String,
    val coverUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    
    // --- 流媒体特有状态 ---
    val isLiked: Boolean = false,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val localFilePath: String? = null,
    
    // --- Paging 排序依赖 ---
    val addedAt: Long // 用于库中按添加时间排序
)

enum class DownloadState {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, ERROR
}
```

### 2.2 Room 数据库层 (DAO)

需要提供给 Paging 3 一个 `PagingSource` 工厂。

```kotlin
@Dao
interface SongDao {
    // 返回 PagingSource 供 Paging 3 使用，而不是普通的 List
    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun getSongsPagingSource(): PagingSource<Int, SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun clearAll()
    
    // 其他更新状态的方法 (如点赞、更新下载状态)
    @Query("UPDATE songs SET downloadState = :state WHERE id = :songId")
    suspend fun updateDownloadState(songId: String, state: DownloadState)
}
```

### 2.3 远端请求协调器 (RemoteMediator)

这是实现离线缓存分页的关键。由于需要支持分页，我们还需要一个额外的表来存储网络分页的 Key（`RemoteKeys`）。

```kotlin
@OptIn(ExperimentalPagingApi::class)
class LibraryRemoteMediator(
    private val api: MusicApi,
    private val db: MusicDatabase
) : RemoteMediator<Int, SongEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, SongEntity>
    ): MediatorResult {
        // 1. 根据 loadType (REFRESH, PREPEND, APPEND) 决定请求的 pageKey
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                // 查询本地记录的最后一个 RemoteKey，决定下一页
                val remoteKey = getRemoteKeyForLastItem(state)
                remoteKey?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            // 2. 请求网络数据
            val response = api.getLibrarySongs(page = page, pageSize = state.config.pageSize)
            val songs = response.data
            val endOfPaginationReached = songs.isEmpty()

            db.withTransaction {
                // 3. 如果是刷新，清空旧数据和 Key
                if (loadType == LoadType.REFRESH) {
                    db.remoteKeysDao().clearRemoteKeys()
                    db.songDao().clearAll()
                }

                // 4. 计算新的 Keys 并存入本地
                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = songs.map { RemoteKey(id = it.id, prevKey = prevKey, nextKey = nextKey) }
                
                db.remoteKeysDao().insertAll(keys)
                
                // 5. 将网络数据转换为实体并存入 Room
                val entities = songs.map { it.toEntity() }
                db.songDao().insertAll(entities)
            }
            
            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
```

### 2.4 仓库层 (Repository) 与 ViewModel

Repository 负责组装 `Pager`。

```kotlin
class LibraryRepository(
    private val api: MusicApi,
    private val db: MusicDatabase
) {
    @OptIn(ExperimentalPagingApi::class)
    fun getLibrarySongs(): Flow<PagingData<SongEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = true // 支持占位符，UI 体验更好
            ),
            remoteMediator = LibraryRemoteMediator(api, db),
            pagingSourceFactory = { db.songDao().getSongsPagingSource() }
        ).flow
    }
}
```

ViewModel 暴露给 UI。

```kotlin
class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    // cachedIn 确保在配置更改（如旋转屏幕）时不会重新请求
    val pagedSongs: Flow<PagingData<SongEntity>> = repository.getLibrarySongs()
        .cachedIn(viewModelScope)
}
```

## 3. UI 层的配合 (Compose)

UI 层需要使用 `collectAsLazyPagingItems()` 来消费数据，并根据状态显示加载中或错误提示。

```kotlin
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val lazyPagingItems = viewModel.pagedSongs.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id } // 强烈建议提供稳定的 key
        ) { index ->
            val song = lazyPagingItems[index]
            if (song != null) {
                // 渲染歌曲卡片 (带有大圆角等 MD3 设计)
                SongCard(song = song)
            } else {
                // 渲染占位符 (Shimmer 效果)
                SongCardPlaceholder()
            }
        }
        
        // 处理加载状态 (底部 Loading 或 Error 重试按钮)
        when (val appendState = lazyPagingItems.loadState.append) {
            is LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item { RetryButton(onClick = { lazyPagingItems.retry() }) }
            else -> {}
        }
    }
}
```

## 4. 关键挑战与解决方案

1.  **数据陈旧问题 (Stale Data)**：
    *   *问题*：Room 中缓存的数据可能与远端不一致（例如，歌曲在其他设备上被删除了）。
    *   *解决*：可以利用 `RemoteMediator` 的 `LoadType.REFRESH` 进行强制同步，或者引入长连接（如 WebSocket/SSE）来接收后端的数据变更推送，然后局部更新 Room。

2.  **播放列表传递给播放器引擎**：
    *   *问题*：当点击播放时，如果直接传递 `List<Song>` 给 Media3/ExoPlayer，对于分页数据是不行的，因为我们没有完整列表。
    *   *解决*：不传递完整列表，而是传递一个特定的“上下文 ID”（例如 `LibraryPlaylistId`）和一个起始 `songId` 给播放服务。播放服务需要拥有自己的能力去查询 Repository（或后端）以获取当前曲目之后的歌曲来进行连续播放。

3.  **局部状态更新（如点击喜欢或下载）**：
    *   *问题*：如何在不刷新整个列表的情况下更新单曲状态？
    *   *解决*：这就是 SSOT (单一信任源) 架构的优势所在。当用户点击“喜欢”时，ViewModel 直接调用 `Dao.updateIsLiked(songId, true)`。Room 数据库发生变化后，会自动发射一个新的 `PagingSource` 实例，由于 Compose 和 Paging 3 的 Diff 机制，只有那一条发生改变的 Item 所在的 UI 会发生重绘，无需手动刷新列表。

---
这个架构能够完美支撑海量流媒体音乐库的展示，同时提供了离线访问的能力和优秀的加载体验。