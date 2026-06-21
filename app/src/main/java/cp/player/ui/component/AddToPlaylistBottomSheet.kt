package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    playlists: List<Playlist>,
    onDismissRequest: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    excludePlaylistId: Long? = null,
    title: String = stringResource(R.string.add_to_playlist)
) {
    val ownedPlaylists = playlists.filter { !it.subscribed && it.id != excludePlaylistId }

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(ownedPlaylists, key = { _, p -> p.id }) { index, p ->
                    PlaylistItem(
                        playlist = p,
                        onClick = { onPlaylistSelected(p) },
                        index = index,
                        total = ownedPlaylists.size,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }
}
