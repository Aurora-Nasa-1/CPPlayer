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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
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
                onPlay = {
                    // Enable AutoMix/Crossfade (fadeMode = 0) with a nice duration for seamless transition
                    cp.player.util.UserPreferences.saveFadeMode(liveSortViewModel.getApplication(), 0)
                    cp.player.util.UserPreferences.saveFadeDuration(liveSortViewModel.getApplication(), 6f)

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
    currentQueue: List<cp.player.model.Song>, 
    localSongs: List<Pair<cp.player.model.Song, android.net.Uri>>,
    onStart: (List<cp.player.model.Song>) -> Unit
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
                text = "Select Tracks to Mix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = {
                    selectedSongs = if (selectedSongs.size == downloadedQueue.size) emptySet() else downloadedQueue.toSet()
                },
                enabled = downloadedQueue.isNotEmpty()
            ) {
                Text(if (selectedSongs.size == downloadedQueue.size) "Deselect All" else "Select All")
            }
        }

        Text(
            text = if (downloadedQueue.isNotEmpty()) "Filter your current queue. Only downloaded or local tracks can be analyzed and seamlessly connected." else "No downloaded or local tracks found in the current queue. Please download some tracks first.",
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
            Text("Analyze & Mix ${selectedSongs.size} Tracks", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            text = "Analyzing Features",
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
            text = "Song ${state.progress} of ${state.total}",
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
            text = "Optimizing Flow",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Finding the perfect sequence...",
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
            contentDescription = "Error",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Analysis Failed",
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
            Text("Reset & Try Again")
        }
    }
}

@Composable
private fun CompletedState(
    state: LiveSortState.Completed,
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
                            text = "LiveSort Flow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.sortedSongs.size} tracks seamlessly connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
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
                    LegendItem("Ideal Curve", Color.Gray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem("Actual Mix", MaterialTheme.colorScheme.primary)
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
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Seamless Mix", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.sortedSongs, key = { _, item -> item.song.id }) { index, item ->
                val isDragged = draggedIndex == index
                val isDropTarget = dropIndex == index && draggedIndex != index
                
                SongListItem(
                    songWithEmotion = item,
                    index = index,
                    isDragged = isDragged,
                    isDropTarget = isDropTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedIndex = index },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val itemHeight = 72.dp.toPx() // Approximate item height including padding
                                    val totalOffset = dragAmount.y
                                    
                                    val newDropIndex = (index + (totalOffset / itemHeight).roundToInt())
                                        .coerceIn(0, state.sortedSongs.lastIndex)
                                        
                                    if (newDropIndex != dropIndex) {
                                        dropIndex = newDropIndex
                                        // Auto-scroll logic could be added here
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
                        }
                )
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
                
                // Curve smoothing logic could be added here, using simple lines for now
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

@Composable
private fun SongListItem(
    songWithEmotion: SongWithEmotion,
    index: Int,
    isDragged: Boolean,
    isDropTarget: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isDragged) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val elevation = if (isDragged) 8.dp else 0.dp

    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp),
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Column {
            if (isDropTarget) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index & Icon
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Title & Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = songWithEmotion.song.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = songWithEmotion.song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Metrics
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${songWithEmotion.bpm.roundToInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${(songWithEmotion.energy * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = " ENG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Drag Handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
