package cp.player.ui.screen

import cp.player.R
import cp.player.ui.component.LyricContent
import cp.player.ui.component.CoverThemeWrapper
import cp.player.ui.component.AppScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    songName: String,
    currentPosition: Long,
    syncedLyrics: SyncedLyrics?,
    useCoverColor: Boolean = false,
    coverColor: Int? = null,
    onBackPressed: () -> Unit,
    onSeek: (Long) -> Unit = {}
) {
    CoverThemeWrapper(useCoverColor = useCoverColor, coverColor = coverColor) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 0.6f),
                    Color.Black
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1A1A1A), Color.Black)
            )
        }

        val view = LocalView.current
        if (!view.isInEditMode) {
            val isDarkTheme = isSystemInDarkTheme()
            val luminance = coverColor?.let { ColorUtils.calculateLuminance(it) } ?: 0.0
            val isAppearanceLightStatusBars = !isDarkTheme && (luminance > 0.5)

            SideEffect {
                val window = (view.context as android.app.Activity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            AppScaffold(
                title = {
                    Text(
                        songName,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                        color = Color.White
                    )
                },
                onBackPressed = onBackPressed,
                topBarContainerColor = Color.Transparent
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LyricContent(
                        syncedLyrics = syncedLyrics,
                        currentPosition = currentPosition,
                        onSeek = onSeek
                    )
                }
            }
        }
    }
}
