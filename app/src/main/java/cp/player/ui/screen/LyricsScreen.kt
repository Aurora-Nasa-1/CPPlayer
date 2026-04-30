package cp.player.ui.screen

import cp.player.model.LyricLine
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import cp.player.viewmodel.PlaybackViewModel
import cp.player.ui.component.LyricContent
import cp.player.ui.component.CommonBackButton
import cp.player.ui.theme.createCustomColorScheme
import cp.player.ui.component.AppScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    lyrics: List<LyricLine>,
    songName: String,
    currentPosition: Long,
    useCoverColor: Boolean = false,
    coverColor: Int? = null,
    onBackPressed: () -> Unit
) {
    val lyricsColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme())
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = lyricsColorScheme) {
        val bgBrush = if (useCoverColor && coverColor != null) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(coverColor).copy(alpha = 0.6f),
                    Color.Black
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A1A1A),
                    Color.Black
                )
            )
        }

        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            val isDarkTheme = isSystemInDarkTheme()
            val luminance = coverColor?.let { androidx.core.graphics.ColorUtils.calculateLuminance(it) } ?: 0.0
            val isAppearanceLightStatusBars = !isDarkTheme && (luminance > 0.5)

            SideEffect {
                val window = (view.context as android.app.Activity).window
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isAppearanceLightStatusBars
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
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    CommonBackButton(
                        onClick = onBackPressed,
                        containerColor = Color.White.copy(alpha = 0.12f),
                        iconColor = Color.White
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LyricContent(
                        lyrics = lyrics,
                        currentPosition = currentPosition
                    )
                }
            }
        }
    }
}
