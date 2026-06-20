package cp.player.lyrics

import android.content.Context
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import cp.player.model.LyricLine
import cp.player.model.LyricsInfo
import cp.player.repository.PlaybackRepository
import cp.player.service.AmllLyricService
import cp.player.service.LyricService
import cp.player.util.DebugLog
import cp.player.util.LyricUtils
import cp.player.util.SyncedLyricsConverter
import cp.player.util.UserPreferences
import io.github.proify.lyricon.lyric.model.RichLyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌词系统唯一数据源。
 *
 * 职责：
 * - 根据用户设置选择歌词来源（Provider API / AMLL TTML / 混合）
 * - 获取、解析、缓存歌词
 * - 统一输出 [LyricsState]，UI 层和 Service 层消费
 *
 * 使用方式：
 * ```
 * LyricsManager.fetch(songId, context)  // 触发获取
 * LyricsManager.state.collect { ... }   // 观察状态
 * ```
 */
object LyricsManager {

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null
    private var debounceJob: Job? = null
    private var currentSongId: String? = null

    private val playbackRepository = PlaybackRepository()

    /**
     * 获取歌词。重复调用同一 songId 时跳过（已有结果）。
     * 不同 songId 时取消旧请求，立即清除旧状态。
     */
    fun fetch(songId: String, context: Context) {
        DebugLog.i("LyricsManager: fetch($songId) called, currentSongId=$currentSongId, state=${_state.value::class.simpleName}")

        // 同一首歌已有成功结果，跳过（避免重复网络请求）
        if (songId == currentSongId && _state.value is LyricsState.Success) {
            DebugLog.i("LyricsManager: skip fetch, already have lyrics for $songId")
            return
        }

        // 取消旧的 debounce 和 fetch
        debounceJob?.cancel()
        fetchJob?.cancel()
        currentSongId = songId

        // 立即清除旧歌词，避免 UI 显示上一首歌的歌词
        _state.value = LyricsState.Loading(songId)
        DebugLog.i("LyricsManager: state → Loading($songId)")

        // debounce 150ms：等所有重复调用结束后再执行
        debounceJob = scope.launch {
            delay(150)
            DebugLog.i("LyricsManager: debounce expired, starting fetch for $songId")
            fetchJob = scope.launch {
                try {
                    val result = fetchInternal(songId, context)
                    ensureActive()
                    if (currentSongId == songId) {
                        _state.value = result
                        val info = (result as? LyricsState.Success)?.lyricsInfo
                        DebugLog.i("LyricsManager: state → Success($songId), source=${info?.source}, lines=${(result as? LyricsState.Success)?.syncedLyrics?.lines?.size}")
                    } else {
                        DebugLog.i("LyricsManager: discarding result for $songId, currentSongId=$currentSongId")
                    }
                } catch (_: CancellationException) {
                    DebugLog.i("LyricsManager: fetch($songId) cancelled")
                } catch (e: Exception) {
                    DebugLog.e("LyricsManager: error for $songId", e)
                    if (currentSongId == songId) {
                        _state.value = LyricsState.Error(songId, e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    /**
     * 内部获取逻辑，根据用户设置选择歌词来源。
     */
    private suspend fun fetchInternal(songId: String, context: Context): LyricsState {
        val lyricsSource = UserPreferences.getLyricsSource(context)
        val amllPlatformSetting = UserPreferences.getAmllPlatform(context)
        val platform = if (amllPlatformSetting == "auto") {
            val provider = cp.player.provider.ProviderManager.currentProvider
            AmllLyricService.detectPlatform(provider?.id, provider?.name)
        } else {
            amllPlatformSetting
        }

        DebugLog.i("LyricsManager: lyricsSource=$lyricsSource, platform=$platform (setting=$amllPlatformSetting)")

        return when (lyricsSource) {
            1 -> fetchAmllFirst(songId, platform, context)  // AMLL 优先
            2 -> fetchAmllOnly(songId, platform)             // 仅 AMLL
            else -> fetchProviderOnly(songId, context)        // Provider API（默认）
        }
    }

    /**
     * AMLL 优先：先尝试 AMLL，失败则回退到 Provider API。
     */
    private suspend fun fetchAmllFirst(
        songId: String,
        platform: String?,
        context: Context
    ): LyricsState {
        // 尝试 AMLL
        if (platform == null) {
            DebugLog.w("LyricsManager: AMLL 平台未识别，跳过 AMLL，直接使用 Provider API")
        } else {
            val amllResult = withContext(Dispatchers.IO) {
                AmllLyricService.fetchTtmlLyrics(songId, platform)
            }
            if (amllResult != null) {
                return buildAmllSuccess(songId, amllResult)
            }
            DebugLog.i("LyricsManager: AMLL 未找到 $platform/$songId，回退到 Provider API")
        }

        // 回退到 Provider API
        return fetchProviderOnly(songId, context)
    }

    /**
     * 仅 AMLL：只从 AMLL 获取，失败返回空。
     */
    private suspend fun fetchAmllOnly(
        songId: String,
        platform: String?
    ): LyricsState {
        if (platform == null) {
            DebugLog.w("LyricsManager: AMLL 平台未识别，无法获取歌词")
            return LyricsState.Success(
                songId = songId,
                syncedLyrics = null,
                lyricsInfo = LyricsInfo(source = "AMLL (平台未识别)", format = "N/A"),
                richLyricLines = emptyList()
            )
        }

        val amllResult = withContext(Dispatchers.IO) {
            AmllLyricService.fetchTtmlLyrics(songId, platform)
        }

        return if (amllResult != null) {
            buildAmllSuccess(songId, amllResult)
        } else {
            DebugLog.i("LyricsManager: AMLL 未找到 $platform/$songId")
            LyricsState.Success(
                songId = songId,
                syncedLyrics = null,
                lyricsInfo = LyricsInfo(source = "AMLL (未找到)", format = "N/A"),
                richLyricLines = emptyList()
            )
        }
    }

    /**
     * Provider API：从后端获取 LRC/YRC 歌词。
     *
     * @param duration 歌曲时长(ms)，用于 LRC 最后一行的 endTime 计算。
     *                 传 0 时自动使用 lastLine.time + 5000。
     */
    private suspend fun fetchProviderOnly(
        songId: String,
        context: Context,
        duration: Long = 0L
    ): LyricsState {
        return try {
            val cookie = UserPreferences.getCookie(context)
            val lines = withContext(Dispatchers.IO) {
                playbackRepository.fetchLyrics(songId, duration, cookie)
            }
            currentCoroutineContext().ensureActive()
            buildProviderSuccess(songId, lines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsState.Error(songId, "Provider API failed: ${e.message}")
        }
    }

    /**
     * 构建 AMLL 成功状态。
     */
    private fun buildAmllSuccess(songId: String, syncedLyrics: SyncedLyrics): LyricsState.Success {
        val hasKaraoke = syncedLyrics.lines.any { it is KaraokeLine }
        val hasTranslation = syncedLyrics.lines.any {
            (it as? KaraokeLine)?.translation?.isNotEmpty() == true ||
                (it as? SyncedLine)?.translation?.isNotEmpty() == true
        }
        val hasPhonetic = syncedLyrics.lines.any {
            (it as? KaraokeLine)?.phonetic?.isNotEmpty() == true
        }
        val format = if (hasKaraoke) "TTML (Karaoke)" else "TTML"

        return LyricsState.Success(
            songId = songId,
            syncedLyrics = syncedLyrics,
            lyricsInfo = LyricsInfo(
                source = "AMLL TTML",
                format = format,
                hasWordLevel = hasKaraoke,
                hasTranslation = hasTranslation,
                hasPhonetic = hasPhonetic
            ),
            richLyricLines = LyricUtils.syncedLyricsToRichLyricLines(syncedLyrics)
        )
    }

    /**
     * 构建 Provider API 成功状态。
     * 将 List<LyricLine> 转换为 SyncedLyrics + RichLyricLine。
     */
    private fun buildProviderSuccess(songId: String, lines: List<LyricLine>): LyricsState.Success {
        val syncedLyrics = if (lines.isNotEmpty()) {
            SyncedLyricsConverter.convert(lines)
        } else {
            null
        }

        val hasWords = lines.any { it.words != null && it.words.isNotEmpty() }
        val hasTranslation = lines.any { !it.translation.isNullOrEmpty() }
        val hasPhonetic = lines.any { !it.romanization.isNullOrEmpty() }
        val format = when {
            hasWords -> "YRC (逐字)"
            else -> "LRC"
        }

        val richLyricLines = if (syncedLyrics != null) {
            LyricUtils.syncedLyricsToRichLyricLines(syncedLyrics)
        } else {
            LyricUtils.toRichLyricLines(lines)
        }

        return LyricsState.Success(
            songId = songId,
            syncedLyrics = syncedLyrics,
            lyricsInfo = LyricsInfo(
                source = "Provider API",
                format = format,
                hasWordLevel = hasWords,
                hasTranslation = hasTranslation,
                hasPhonetic = hasPhonetic
            ),
            richLyricLines = richLyricLines
        )
    }
}
