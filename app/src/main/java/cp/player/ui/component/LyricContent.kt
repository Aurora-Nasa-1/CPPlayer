package cp.player.ui.component

import cp.player.model.LyricLine
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import cp.player.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LyricContent(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    contentPadding: PaddingValues = PaddingValues(top = 100.dp, bottom = 600.dp, start = 24.dp, end = 24.dp)
) {
    val listState = rememberLazyListState()

    val activeIndex = remember(currentPosition, lyrics) {
        val index = lyrics.indexOfLast { it.time <= currentPosition }
        if (index == -1) 0 else index
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex >= 0) {
            // Move active lyric to the upper part of the screen (around 1/5 from top for better visibility)
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -(screenHeightPx / 5.0).toInt()
            )
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface

    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_lyrics), color = textColor.copy(alpha = 0.5f))
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.4f,
                    animationSpec = tween(500)
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    if (line.words != null && isActive) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start
                        ) {
                            line.words.forEach { word ->
                                val isWordActive = currentPosition >= word.beginTime
                                val wordAlpha by animateFloatAsState(
                                    targetValue = if (isWordActive) 1f else 0.3f,
                                    animationSpec = tween(200)
                                )
                                Text(
                                    text = word.text,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        lineHeight = 36.sp,
                                        fontSize = 28.sp
                                    ),
                                    color = textColor.copy(alpha = wordAlpha),
                                    textAlign = textAlign
                                )
                            }
                        }
                    } else {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                lineHeight = 36.sp,
                                fontSize = 28.sp
                            ),
                            color = textColor,
                            textAlign = textAlign
                        )
                    }
                    if (!line.romanization.isNullOrEmpty()) {
                        Text(
                            text = line.romanization,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 20.sp,
                                fontSize = 14.sp
                            ),
                            color = textColor.copy(alpha = 0.6f),
                            textAlign = textAlign,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (!line.translation.isNullOrEmpty()) {
                        Text(
                            text = line.translation,
                            style = MaterialTheme.typography.titleMedium.copy(
                                lineHeight = 28.sp,
                                fontSize = 20.sp
                            ),
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = textAlign,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
