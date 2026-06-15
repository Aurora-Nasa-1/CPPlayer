package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.model.Comment

@Composable
fun CommentPage(
    hotComments: List<Comment>,
    newestComments: List<Comment>,
    totalCount: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    currentSort: Int = 1,
    onLoadMore: () -> Unit,
    onLikeClick: (Comment) -> Unit,
    onReplyClick: (Comment) -> Unit,
    onPostComment: (String) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onSortChange: (Int) -> Unit = {},
    onViewFloorClick: (Comment) -> Unit = {}
) {
    var commentText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= (hotComments.size + newestComments.size) - 5) {
                    if (hasMore && !isLoading) {
                        onLoadMore()
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.comments_count, totalCount),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (hotComments.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.hot_comments),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(hotComments, key = { _, c -> "hot_${c.id}" }) { _, comment ->
                    CommentItem(
                        comment = comment,
                        onLikeClick = { onLikeClick(comment) },
                        onReplyClick = { onReplyClick(comment) },
                        onAvatarClick = { onAvatarClick(comment.userId) },
                        onViewFloorClick = { onViewFloorClick(comment) }
                    )
                }
            }

            if (newestComments.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.newest_comments),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(newestComments, key = { _, c -> "new_${c.id}" }) { _, comment ->
                    CommentItem(
                        comment = comment,
                        onLikeClick = { onLikeClick(comment) },
                        onReplyClick = { onReplyClick(comment) },
                        onAvatarClick = { onAvatarClick(comment.userId) },
                        onViewFloorClick = { onViewFloorClick(comment) }
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text(stringResource(R.string.add_comment)) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.large
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (commentText.isNotBlank()) {
                        onPostComment(commentText)
                        commentText = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
