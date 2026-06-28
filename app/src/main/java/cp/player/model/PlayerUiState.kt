package cp.player.model

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics

/**
 * PlayerScreen 的完整 UI 状态。
 *
 * 聚合了 PlayerScreen 所需的所有显示状态，
 * 将原来 60+ 个独立参数压缩为结构化数据类。
 */
@Immutable
data class PlayerUiState(
    // 核心播放状态
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    // 播放模式
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleMode: Boolean = false,
    // 音质
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val codecName: String = "",
    // 用户状态
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    // 歌词（统一使用 SyncedLyrics 格式，由 LyricsManager 提供）
    val syncedLyrics: SyncedLyrics? = null,
    val lyricsInfo: LyricsInfo? = null,
    // 队列
    val queue: List<Song> = emptyList(),
    // 主题
    val useCoverColor: Boolean = false,
    val useFluidBackground: Boolean = false,
    val coverColor: Int? = null,
    val pureBlack: Boolean = false,
    // 定时器
    val sleepTimerRemaining: Long = 0L,
    // 评论
    val hotComments: List<Comment> = emptyList(),
    val newestComments: List<Comment> = emptyList(),
    val commentTotal: Int = 0,
    val isCommentsLoading: Boolean = false,
    val hasMoreComments: Boolean = true,
    val commentSortType: Int = 1,
    // 楼层评论
    val floorComments: List<Comment> = emptyList(),
    val floorCommentTotal: Int = 0,
    val floorHasMore: Boolean = false,
    val activeParentComment: Comment? = null,
    // 歌单
    val allPlaylists: List<Playlist> = emptyList(),
)

/**
 * PlayerScreen 的回调集合。
 *
 * 聚合了 PlayerScreen 所需的所有用户操作回调，
 * 将原来 25+ 个独立 lambda 参数压缩为结构化数据类。
 */
@Immutable
data class PlayerCallbacks(
    val onPlayPause: () -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSkipPrevious: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onRepeatClick: () -> Unit = {},
    val onShuffleClick: () -> Unit = {},
    val onLikeClick: () -> Unit = {},
    val onArtistClick: (String) -> Unit = {},
    val onDownloadClick: () -> Unit = {},
    val onCommentClick: () -> Unit = {},
    val onLyricClick: () -> Unit = {},
    val onDislikeClick: () -> Unit = {},
    val onAddToPlaylist: (String, Long) -> Unit = { _, _ -> },
    val onPlayAtQueueIndex: (Int) -> Unit = {},
    val onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    val onRemoveQueueItem: (Int) -> Unit = {},
    val onClearQueue: () -> Unit = {},
    val onLoadMoreComments: () -> Unit = {},
    val onLikeComment: (Comment) -> Unit = {},
    val onReplyComment: (Comment) -> Unit = {},
    val onPostComment: (String) -> Unit = {},
    val onAvatarClick: (Long) -> Unit = {},
    val onCommentSortChange: (Int) -> Unit = {},
    val onViewFloorClick: (Comment) -> Unit = {},
    val onLoadMoreFloor: (Comment) -> Unit = {},
    val onDismissFloor: () -> Unit = {},
    val onSetSleepTimer: (Int) -> Unit = {},
    val onBackPressed: () -> Unit = {}
)
