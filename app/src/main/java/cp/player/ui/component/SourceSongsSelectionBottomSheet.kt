package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.model.Playlist
import cp.player.model.Song
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSongsSelectionBottomSheet(
    sourceName: String,
    initialSongs: List<Song>? = null,
    fetchSongs: (suspend () -> List<Song>)? = null,
    onDismissRequest: () -> Unit,
    onAddSelected: (List<String>) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>?>(initialSongs) }
    var isLoading by remember { mutableStateOf(initialSongs == null) }
    var selectedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (initialSongs == null && fetchSongs != null) {
            isLoading = true
            try {
                songs = fetchSongs()
            } catch (e: Exception) {
                songs = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Songs",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "From $sourceName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (songs?.isNotEmpty() == true) {
                    val allSelected = selectedSongs.size == songs!!.size
                    TextButton(onClick = {
                        selectedSongs = if (allSelected) emptySet() else songs!!.map { it.id }.toSet()
                    }) {
                        Text(if (allSelected) "Deselect All" else "Select All")
                    }
                }
            }
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (songs.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(songs!!, key = { _, s -> s.id }) { index, s ->
                        val isSelected = selectedSongs.contains(s.id)
                        SongItem(
                            song = s,
                            index = index,
                            total = songs!!.size,
                            onClick = {
                                selectedSongs = if (isSelected) selectedSongs - s.id else selectedSongs + s.id
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedSongs = if (checked) selectedSongs + s.id else selectedSongs - s.id
                                    }
                                )
                            },
                            trailingContent = {}, // Hide options menu in selection mode
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        }
    }
    
    // Add Button overlay
    if (selectedSongs.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ExtendedFloatingActionButton(
                onClick = { onAddSelected(selectedSongs.toList()) },
                modifier = Modifier.padding(bottom = 32.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("Add ${selectedSongs.size} song(s)") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
