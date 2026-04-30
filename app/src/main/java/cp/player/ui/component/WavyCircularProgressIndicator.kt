package cp.player.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 普通的波浪形加载指示器 (原生 MD3 Expressive)
 * 适用于：按钮内、工具栏、底部播放栏等小型局部场景。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    amplitude: Float = 0.25f,
    waveSpeed: Dp = 32.dp
) {
    if (progress != null) {
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            amplitude = { amplitude },
            waveSpeed = waveSpeed
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            amplitude = amplitude,
            waveSpeed = waveSpeed
        )
    }
}

/**
 * 容器化加载指示器 (Contained Loading Indicator)
 * 适用于：页面中心加载、全屏加载、大卡片加载等需要视觉重心的场景。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    androidx.compose.material3.ContainedLoadingIndicator(
        modifier = modifier,
        containerColor = containerColor,
        indicatorColor = indicatorColor
    )
}
