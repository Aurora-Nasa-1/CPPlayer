package cp.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import cp.player.R
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import cp.player.ui.component.ExpressiveShapes
import cp.player.ui.component.AppScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LogViewerScreen(onBackPressed: () -> Unit) {
    val logs by LogManager.logs.collectAsState()
    var systemLogs by remember { mutableStateOf("") }
    var showSystemLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(showSystemLogs) {
        if (showSystemLogs) {
            systemLogs = "Loading system logs..."
            withContext(Dispatchers.IO) {
                systemLogs = try {
                    val process = Runtime.getRuntime().exec("logcat -d -v time")
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "Failed to fetch system logs: ${e.message}"
                }
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.app_logs),
        onBackPressed = onBackPressed,
        actions = {
            IconButton(onClick = {
                showSystemLogs = !showSystemLogs
            }) {
                Icon(if (showSystemLogs) Icons.AutoMirrored.Filled.ViewList else Icons.Default.Terminal, contentDescription = "Toggle System Logs")
            }
            IconButton(onClick = {
                val text = if (showSystemLogs) systemLogs else logs.joinToString("\n") { "${it.time} [${it.level}] ${it.message}" }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = { LogManager.clear() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear")
            }
        }
    ) { _ ->
        SelectionContainer {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (showSystemLogs) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = systemLogs,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(logs) { index, entry ->
                            LogEntryItem(entry, ExpressiveShapes.calculateShape(index, logs.size))
                        }
                        if (logs.isEmpty()) {
                            item {
                                Text("No app logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogManager.LogEntry, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp)) {
    val color = when (entry.level) {
        "E" -> Color.Red
        "W" -> Color.Yellow
        "I" -> Color.Cyan
        else -> Color.LightGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row {
            Text(
                text = entry.time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "[${entry.level}]",
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        if (entry.throwable != null) {
            Text(
                text = entry.throwable,
                color = Color.Red.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
