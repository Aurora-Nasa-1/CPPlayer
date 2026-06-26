package cp.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.ui.component.AppScaffold
import cp.player.model.Contact
import cp.player.util.resized
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ContactListScreen(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onBackPressed: () -> Unit
) {
    AppScaffold(
        title = stringResource(R.string.messages),
        onBackPressed = onBackPressed
    ) { _ ->
        if (contacts.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.no_messages_yet),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = contacts,
                    key = { it.userId },
                    contentType = { "contact" }
                ) { contact ->
                    ContactItem(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onAvatarClick = { onAvatarClick(contact.userId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val timeStr = contact.lastMessageTime?.let {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""

    cp.player.ui.component.UnifiedListItem(
        onClick = { onClick() },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contact.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (timeStr.isNotEmpty()) {
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        supportingContent = {
            Text(
                contact.lastMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            AsyncImage(
                model = contact.avatarUrl.resized(120),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onAvatarClick() },
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            if (contact.unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(
                        contact.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}
