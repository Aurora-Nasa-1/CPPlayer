package cp.player.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Song
import cp.player.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockedToolbar(
    song: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClick: () -> Unit,
    navItems: List<Triple<String, String, ImageVector>>,
    currentRoute: String?,
    onNavItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Docked Toolbar from MD3E usually floats and can be expanded or combined with playback
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(80.dp)
            .fillMaxWidth(), // Fully Rounded for MD3E Docked Toolbar
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left part: Song Info (if available)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onClick() }
                    .padding(4.dp)
            ) {
                if (song != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 100),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = onSkipPrevious,
                                modifier = Modifier.requiredSize(32.dp), shape = MaterialTheme.shapes.small,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(18.dp))
                            }

                            FilledTonalIconButton(
                                onClick = onPlayPause,
                                modifier = Modifier.requiredSize(44.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onSkipNext,
                                modifier = Modifier.requiredSize(32.dp), shape = MaterialTheme.shapes.small,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    // Placeholder or Brand
                    Text(
                        "CNMDPlayer",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Vertical Divider
            VerticalDivider(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Right part: Navigation Items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                navItems.forEach { (route, _, icon) ->
                    val isSelected = currentRoute == route
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onNavItemClick(route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
