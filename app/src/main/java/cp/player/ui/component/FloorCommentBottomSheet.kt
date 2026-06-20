package cp.player.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.model.Comment
import cp.player.ui.theme.createCustomColorScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // 根据封面颜色生成自定义配色方案
    val isDark = isSystemInDarkTheme()
    val floorColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isDark)
    } else {
        MaterialTheme.colorScheme
    }

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
        containerColor = floorColorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        MaterialTheme(colorScheme = floorColorScheme) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题
                Text(
                    text = stringResource(R.string.all_replies),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.comments_count, totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                )

                // 回复列表
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 父评论
                        item {
                            CommentItem(
                                comment = parentComment,
                                onLikeClick = { onLikeClick(parentComment) },
                                onReplyClick = { onReplyClick(parentComment) },
                                onAvatarClick = { onAvatarClick(parentComment.userId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(6.dp),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.primary
                                ) {}
                                Text(
                                    text = stringResource(R.string.all_replies),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 回复列表
                        itemsIndexed(replies, key = { _, it -> it.id }) { _, comment ->
                            CommentItem(
                                comment = comment,
                                onLikeClick = { onLikeClick(comment) },
                                onReplyClick = { onReplyClick(comment) },
                                onAvatarClick = { onAvatarClick(comment.userId) }
                            )
                        }

                        // 加载指示器
                        if (isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }

                // 底部输入栏
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = floorColorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.ime)
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.add_comment),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = floorColorScheme.surfaceVariant,
                                unfocusedIndicatorColor = floorColorScheme.surfaceVariant,
                                focusedContainerColor = floorColorScheme.surfaceVariant,
                                unfocusedContainerColor = floorColorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.large,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        FilledTonalIconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    onPostComment(commentText)
                                    commentText = ""
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.send)
                            )
                        }
                    }
                }
            }
        }
    }
}
