package cp.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.Message
import cp.player.util.resized
import cp.player.ui.component.AppScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    recipientUid: Long,
    recipientName: String,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onBackPressed: () -> Unit,
    miniPlayerHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isKeyboardOpen = WindowInsets.isImeVisible

    // 发送消息后自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 键盘弹出时隐藏 miniPlayer 的额外高度，避免输入栏跳动
    val effectiveMiniPlayerHeight = if (isKeyboardOpen) 0.dp else miniPlayerHeight

    AppScaffold(
        title = recipientName,
        onBackPressed = onBackPressed,
        bottomBar = {
            // Scaffold 的 bottomBar 已自动处理 IME 上移，无需额外 windowInsetsPadding
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.padding(bottom = effectiveMiniPlayerHeight),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.type_message),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.large,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 4
                    )
                    FilledTonalIconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                onSendMessage(textState)
                                textState = ""
                            }
                        },
                        enabled = textState.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            reverseLayout = false,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 12.dp + effectiveMiniPlayerHeight
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = messages,
                key = { it.hashCode() }
            ) { message ->
                MessageBubble(
                    message = message,
                    onAvatarClick = { onAvatarClick(message.fromUserId) }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onAvatarClick: () -> Unit
) {
    val isMe = message.isMe
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleShape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    val timeStr = remember(message.time) {
        messageTimeFormatter.format(Instant.ofEpochMilli(message.time))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!isMe) {
                AsyncImage(
                    model = message.fromAvatarUrl.resized(100),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(horizontalAlignment = alignment) {
                Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    modifier = Modifier.clip(bubbleShape)
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                )
            }

            if (isMe) {
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = message.fromAvatarUrl.resized(100),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
