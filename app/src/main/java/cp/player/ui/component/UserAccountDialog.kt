package cp.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cp.player.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import cp.player.model.UserProfile

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalContext
import cp.player.util.UserPreferences

@Composable
fun UserAccountDialog(
    userProfile: UserProfile?,
    versionName: String,
    onDismiss: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onSwitchAccount: (UserPreferences.SavedAccount) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var tapCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val savedAccounts = remember { UserPreferences.getSavedAccounts(context) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current User Header
                if (userProfile != null) {
                    Surface(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        AsyncImage(
                            model = userProfile.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = userProfile.nickname,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "ID: ${userProfile.userId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out_button))
                    }
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.not_logged_in),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Account Switcher List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        stringResource(R.string.saved_accounts_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val otherAccounts = savedAccounts.filter { it.uid != userProfile?.userId }
                    
                    if (otherAccounts.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(otherAccounts) { account ->
                                cp.player.ui.component.UnifiedListItem(
    onClick = { onSwitchAccount(account) },
                                    headlineContent = { Text(account.nickname) },
                                    leadingContent = {
                                        if (account.avatarUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = account.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        ,
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                    
                    cp.player.ui.component.UnifiedListItem(
    onClick = { onNavigateToLogin() },
                        headlineContent = { Text(stringResource(R.string.add_another_account)) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            ,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // App Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                tapCount++
                                if (tapCount >= 7) {
                                    onNavigateToLogs()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
