package cp.player.ui.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.model.Comment
import cp.player.ui.theme.createCustomColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorCommentBottomSheet(
    parentComment: Comment,
    replies: List<Comment>,
    totalCount: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onLikeClick: (Comment) -> Unit,
    onReplyClick: (Comment) -> Unit,
    onPostComment: (String) -> Unit,
    onAvatarClick: (Long) -> Unit,
    useCoverColor: Boolean = false,
    coverColor: Int? = null,
    onDismiss: () -> Unit
) {
    val floorColorScheme = MaterialTheme.colorScheme

    var commentText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= replies.size && hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.85f),
        containerColor = floorColorScheme.surface
    ) {
        MaterialTheme(colorScheme = floorColorScheme) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.view_replies, totalCount),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        CommentItem(
                            comment = parentComment,
                            onLikeClick = { onLikeClick(parentComment) },
                            onReplyClick = { onReplyClick(parentComment) },
                            onAvatarClick = { onAvatarClick(parentComment.userId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All Replies",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    itemsIndexed(replies, key = { _, it -> it.id }) { index, comment ->
                        CommentItem(
                            comment = comment,
                            onLikeClick = { onLikeClick(comment) },
                            onReplyClick = { onReplyClick(comment) },
                            onAvatarClick = { onAvatarClick(comment.userId) }
                        )
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.add_comment)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onPostComment(commentText)
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
        }
    }
}
