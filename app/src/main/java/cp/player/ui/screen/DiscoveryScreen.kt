package cp.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Playlist
import cp.player.model.Song
import cp.player.ui.component.AppScaffold
import cp.player.ui.component.SongItem
import cp.player.util.resized
import cp.player.viewmodel.ToplistEntry

/**
 * 发现页 — 排行榜 + 音乐推荐。
 */
@Composable
fun DiscoveryScreen(
    toplists: List<ToplistEntry>,
    personalizedPlaylists: List<Playlist>,
    personalizedNewSongs: List<Song>,
    highqualityPlaylists: List<Playlist>,
    topSongs: List<Song>,
    isDiscoveryLoading: Boolean,
    onToplistClick: (ToplistEntry) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Song) -> Unit,
    onViewAllTopSongs: () -> Unit,
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    AppScaffold(
        title = stringResource(R.string.discover),
        onBackPressed = onBackPressed,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        isLoading = isDiscoveryLoading && toplists.isEmpty() && personalizedPlaylists.isEmpty()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomContentPadding.calculateBottomPadding() + 80.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            // ==================== 排行榜 ====================
            if (toplists.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.toplists))
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(toplists) { entry ->
                            ToplistCard(entry = entry, onClick = { onToplistClick(entry) })
                        }
                    }
                }
            }

            // ==================== 推荐歌单 ====================
            if (personalizedPlaylists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(title = stringResource(R.string.recommended_playlists))
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = personalizedPlaylists, key = { "rec_pl_${it.id}" }) { playlist ->
                            RecommendedPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }

            // ==================== 推荐新歌 ====================
            if (personalizedNewSongs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(title = stringResource(R.string.recommended_new_songs))
                }
                itemsIndexed(personalizedNewSongs) { index, song ->
                    SongItem(
                        song = song,
                        index = index,
                        total = personalizedNewSongs.size,
                        onClick = { onSongClick(song) },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            // ==================== 新歌速递 ====================
            if (topSongs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(
                        title = stringResource(R.string.new_songs_express),
                        actionText = stringResource(R.string.view_more),
                        onAction = onViewAllTopSongs
                    )
                }
                itemsIndexed(topSongs.take(10)) { index, song ->
                    SongItem(
                        song = song,
                        index = index,
                        total = topSongs.take(10).size,
                        onClick = { onSongClick(song) },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            // ==================== 精品歌单 ====================
            if (highqualityPlaylists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(title = stringResource(R.string.quality_playlists))
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = highqualityPlaylists, key = { "hq_pl_${it.id}" }) { playlist ->
                            RecommendedPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 子组件 ====================

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 排行榜卡片 — 封面 + 名称 + 更新频率。
 */
@Composable
private fun ToplistCard(
    entry: ToplistEntry,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(150.dp)
    ) {
        Column {
            Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
                AsyncImage(
                    model = entry.coverImgUrl?.resized(300),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
                // 渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 40f
                            )
                        )
                )
                // 更新频率标签
                entry.updateFrequency?.let { freq ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = freq,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
