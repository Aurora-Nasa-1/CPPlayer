package cp.player.ui.component

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R

/**
 * 歌词显示组件。
 *
 * 直接接收 [SyncedLyrics] 格式，无需内部转换。
 * 由 [cp.player.lyrics.LyricsManager] 统一提供数据。
 */
@Composable
fun LyricContent(
    syncedLyrics: SyncedLyrics?,
    currentPosition: Long,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(top = 100.dp, bottom = 600.dp, start = 24.dp, end = 24.dp)
) {
    if (syncedLyrics == null || syncedLyrics.lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_lyrics),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // 使用 rememberUpdatedState 确保 lambda 始终读取最新的 currentPosition
    val latestPosition by rememberUpdatedState(currentPosition)
    val currentPositionProvider = remember {
        { latestPosition.toInt() }
    }

    val currentTextStyle = MaterialTheme.typography.headlineMedium
    val normalStyle = remember(currentTextStyle) {
        currentTextStyle.copy(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            textMotion = TextMotion.Animated,
        )
    }

    val accompanimentStyle = remember(currentTextStyle) {
        currentTextStyle.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        )
    }

    val phoneticTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    KaraokeLyricsView(
        listState = listState,
        lyrics = syncedLyrics,
        currentPosition = currentPositionProvider,
        onLineClicked = { /* 点击歌词行可扩展为跳转 */ },
        onLinePressed = { /* 长按歌词行可扩展为分享 */ },
        normalLineTextStyle = normalStyle,
        accompanimentLineTextStyle = accompanimentStyle,
        phoneticTextStyle = phoneticTextStyle,
        textColor = MaterialTheme.colorScheme.onSurface,
        showTranslation = showTranslation,
        showPhonetic = showPhonetic,
        useBlurEffect = false,
        modifier = modifier.fillMaxSize(),
        offset = contentPadding.calculateTopPadding()
    )
}

/**
 * 从 SyncedLyrics 中提取当前歌词行文本。
 * 用于扩展封面模式下显示当前歌词。
 */
fun SyncedLyrics?.getCurrentLineText(positionMs: Long): String? {
    this ?: return null
    val pos = positionMs.toInt()
    var active: com.mocharealm.accompanist.lyrics.core.model.ISyncedLine? = null
    for (line in lines) {
        if (line.start <= pos) active = line else break
    }
    return when (active) {
        is com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine ->
            active.syllables.joinToString("") { it.content }
        is com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine ->
            active.content
        else -> null
    }
}
