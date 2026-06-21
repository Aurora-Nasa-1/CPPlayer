package cp.player.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
import cp.player.R
import cp.player.model.Song
import cp.player.ui.component.SongItem
import cp.player.viewmodel.LiveSortState
import cp.player.viewmodel.LiveSortViewModel
import cp.player.viewmodel.PlaybackViewModel
import cp.player.viewmodel.SongWithEmotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSortContent(
    liveSortViewModel: LiveSortViewModel,
    playbackViewModel: PlaybackViewModel,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val sortState by liveSortViewModel.sortState.collectAsState()
    val currentQueue = playbackViewModel.currentQueue
    val localSongs = playbackViewModel.localSongs

    // Reset state if currentQueue changes completely (e.g. user switched playlist)
    LaunchedEffect(currentQueue) {
        if (sortState !is LiveSortState.Idle) {
            val isSameQueue = sortState is LiveSortState.Completed &&
                              (sortState as LiveSortState.Completed).sortedSongs.map { it.song } == currentQueue
            if (!isSameQueue) {
                liveSortViewModel.reset()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomContentPadding.calculateBottomPadding())
    ) {
        when (val state = sortState) {
            is LiveSortState.Idle -> IdleState(
                currentQueue = currentQueue,
                localSongs = localSongs,
                onStart = { selectedSongs ->
                    val processableSongs = selectedSongs.mapNotNull { song ->
                        val uri = localSongs.find { it.first.id == song.id }?.second
                            ?: if (song.id.startsWith("local_")) {
                                android.net.Uri.parse("content://media/external/audio/media/${song.id.removePrefix("local_")}")
                            } else null

                        if (uri != null) {
                            song to uri.toString()
                        } else {
                            null
                        }
                    }
                    liveSortViewModel.processPlaylist(processableSongs)
                }
            )

            is LiveSortState.Analyzing -> AnalyzingState(state)

            is LiveSortState.Sorting -> SortingState()

            is LiveSortState.Error -> ErrorState(state.message, onRetry = {
                liveSortViewModel.processPlaylist(emptyList())
            })

            is LiveSortState.Completed -> CompletedState(
                state = state,
                playbackViewModel = playbackViewModel,
                liveSortViewModel = liveSortViewModel,
                onPlay = {
                    // 构建 per-song fade override map
                    val overrides = state.sortedSongs.associate {
                        it.song.id to (it.fadeInDuration to it.fadeOutDuration)
                    }
                    // 设置到 MusicService companion object
                    cp.player.service.MusicService.livesortFadeOverrides = overrides
                    // 同步到 PlaybackViewModel
                    playbackViewModel.livesortFadeOverrides = overrides

                    val songs = state.sortedSongs.map { it.song }
                    if (songs.isNotEmpty()) {
                        playbackViewModel.playSong(songs.first(), songs)
                    }
                },
                onReorder = { from, to ->
                    liveSortViewModel.reorderCompletedList(from, to)
                }
            )
        }
    }
}

@Composable
private fun IdleState(
    currentQueue: List<Song>,
    localSongs: List<Pair<Song, android.net.Uri>>,
    onStart: (List<Song>) -> Unit
) {
    val downloadedQueue = remember(currentQueue, localSongs) {
        currentQueue.filter { song ->
            localSongs.any { it.first.id == song.id } || song.id.startsWith("local_")
        }
    }

    var selectedSongs by remember(downloadedQueue) { mutableStateOf(downloadedQueue.toSet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_tracks_to_mix),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = {
                    selectedSongs = if (selectedSongs.size == downloadedQueue.size) emptySet() else downloadedQueue.toSet()
                },
                enabled = downloadedQueue.isNotEmpty()
            ) {
                Text(if (selectedSongs.size == downloadedQueue.size) stringResource(R.string.deselect_all) else stringResource(R.string.select_all))
            }
        }

        Text(
            text = if (downloadedQueue.isNotEmpty()) stringResource(R.string.livesort_filter_hint) else stringResource(R.string.livesort_no_tracks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = if (downloadedQueue.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        // List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(downloadedQueue) { song ->
                val isSelected = selectedSongs.contains(song)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    onClick = {
                        selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedSongs = if (checked) selectedSongs + song else selectedSongs - song
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(text = song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Button
        Button(
            onClick = { onStart(selectedSongs.toList()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = selectedSongs.isNotEmpty()
        ) {
            Icon(Icons.Default.AutoGraph, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.analyze_and_mix, selectedSongs.size), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AnalyzingState(state: LiveSortState.Analyzing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val progress = if (state.total > 0) state.progress.toFloat() / state.total else 0f

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(140.dp)
            )
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.analyzing_features),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.currentSong,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.livesort_song_progress, state.progress, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SortingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.optimizing_flow),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.livesort_finding_sequence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoGraph,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.analysis_failed),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.reset_and_try_again))
        }
    }
}

@Composable
private fun CompletedState(
    state: LiveSortState.Completed,
    playbackViewModel: PlaybackViewModel,
    liveSortViewModel: LiveSortViewModel,
    onPlay: () -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top section: Chart and Summary
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.livesort_flow),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.livesort_tracks_connected, state.sortedSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 分析失败提示
                if (state.failedSongs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.livesort_n_failed, state.failedSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Emotion Chart
                EmotionChart(
                    idealCurve = state.idealCurve,
                    actualCurve = state.actualCurve,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(stringResource(R.string.ideal_curve), Color.Gray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(stringResource(R.string.actual_mix), MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Play Button
        Button(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.start_seamless_mix), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks List — 使用 SongItem 组件，与本地歌曲列表一致
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(state.sortedSongs, key = { _, item -> item.song.id }) { index, item ->
                val isDragged = draggedIndex == index
                val isDropTarget = dropIndex == index && draggedIndex != index
                val isCurrentlyPlaying = item.song.id == playbackViewModel.currentSong?.id

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                ) {
                    // Drop target indicator
                    if (isDropTarget) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.TopCenter)
                        )
                    }

                    SongItem(
                        song = item.song,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        onClick = {
                            // 从该位置播放队列
                            val songs = state.sortedSongs.map { it.song }
                            val fromIndex = index
                            val queueFromHere = songs.subList(fromIndex, songs.size)

                            // 设置 per-song fade overrides
                            val overrides = state.sortedSongs.associate {
                                it.song.id to (it.fadeInDuration to it.fadeOutDuration)
                            }
                            cp.player.service.MusicService.livesortFadeOverrides = overrides
                            playbackViewModel.livesortFadeOverrides = overrides

                            if (queueFromHere.isNotEmpty()) {
                                playbackViewModel.playSong(queueFromHere.first(), queueFromHere)
                            }
                        },
                        index = index,
                        total = state.sortedSongs.size,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedIndex = index },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val itemHeight = 72.dp.toPx()
                                        val totalOffset = dragAmount.y

                                        val newDropIndex = (index + (totalOffset / itemHeight).roundToInt())
                                            .coerceIn(0, state.sortedSongs.lastIndex)

                                        if (newDropIndex != dropIndex) {
                                            dropIndex = newDropIndex
                                        }
                                    },
                                    onDragEnd = {
                                        val from = draggedIndex
                                        val to = dropIndex
                                        if (from != null && to != null && from != to) {
                                            onReorder(from, to)
                                        }
                                        draggedIndex = null
                                        dropIndex = null
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dropIndex = null
                                    }
                                )
                            },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // BPM + Energy
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${item.bpm.roundToInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = " ${stringResource(R.string.bpm_label)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${(item.energy * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = " ${stringResource(R.string.energy_label)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Drag Handle
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmotionChart(
    idealCurve: List<Double>,
    actualCurve: List<Double>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Animate path drawing
    val transition = rememberInfiniteTransition(label = "ChartAnim")
    val animProgress by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Canvas(modifier = modifier) {
        if (idealCurve.isEmpty() || actualCurve.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        val maxVal = 100.0 // Emotion scores are 0..100

        fun xCoord(index: Int, size: Int): Float {
            return if (size <= 1) width / 2 else (index.toFloat() / (size - 1)) * width
        }

        fun yCoord(value: Double): Float {
            return height - ((value.toFloat() / maxVal.toFloat()) * height)
        }

        // Draw Ideal Curve
        val idealPath = Path().apply {
            idealCurve.forEachIndexed { index, value ->
                val x = xCoord(index, idealCurve.size)
                val y = yCoord(value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = idealPath,
            color = Color.Gray.copy(alpha = 0.4f),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw Actual Curve
        val actualPath = Path().apply {
            actualCurve.forEachIndexed { index, value ->
                val x = xCoord(index, actualCurve.size)
                val y = yCoord(value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = actualPath,
            brush = Brush.linearGradient(
                colors = listOf(secondaryColor, primaryColor),
                start = Offset(0f, 0f),
                end = Offset(width, 0f)
            ),
            style = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            alpha = animProgress
        )

        // Draw Nodes
        actualCurve.forEachIndexed { index, value ->
            val x = xCoord(index, actualCurve.size)
            val y = yCoord(value)
            drawCircle(
                color = surfaceColor,
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = primaryColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
