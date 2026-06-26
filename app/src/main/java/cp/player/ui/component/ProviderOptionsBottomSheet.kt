package cp.player.ui.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cp.player.R
import cp.player.provider.BackendProvider
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.provider.ProviderUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 提供者操作底部面板。
 *
 * 参考 SongOptionsBottomSheet 的样式设计，包含：
 * - 检查更新（需要 updateUrl）
 * - 手动更新（选择 zip 文件）
 * - 删除此提供商（确认对话框）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderOptionsBottomSheet(
    provider: BackendProvider,
    onDismissRequest: () -> Unit,
    onDeleted: () -> Unit,
    onUpdated: () -> Unit,
    onUpdateZipSelected: (android.net.Uri) -> Unit,
    /** 外部预检查的更新信息（从列表页自动检查传入），null 表示未预检查 */
    preCheckedUpdate: ProviderUpdateChecker.UpdateInfo? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 更新检查状态：如果有预检查结果则直接使用
    var updateState by remember {
        mutableStateOf<UpdateCheckState>(
            if (preCheckedUpdate != null) UpdateCheckState.HasUpdate(preCheckedUpdate)
            else UpdateCheckState.Idle
        )
    }
    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }

    val checkUpdateColor = MaterialTheme.colorScheme.primaryContainer
    val checkUpdateOnColor = MaterialTheme.colorScheme.onPrimaryContainer
    val manualUpdateColor = MaterialTheme.colorScheme.secondaryContainer
    val manualUpdateOnColor = MaterialTheme.colorScheme.onSecondaryContainer
    val deleteColor = MaterialTheme.colorScheme.errorContainer
    val deleteOnColor = MaterialTheme.colorScheme.onErrorContainer

    StyledModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头部：提供者图标 + 名称 + 版本
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${provider.type.name} · v${provider.version}",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 检查更新状态提示
            when (val state = updateState) {
                is UpdateCheckState.Checking -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.checking_update_progress), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is UpdateCheckState.HasUpdate -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.new_version_found, state.info.version),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (!state.info.changelog.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    state.info.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                is UpdateCheckState.NoUpdate -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.already_latest_version),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is UpdateCheckState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                UpdateCheckState.Idle -> {}
            }

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 检查更新（需要 updateUrl）
                val updateUrl = provider.updateUrl
                if (updateUrl != null) {
                    PillButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.check_update),
                        icon = Icons.Rounded.SystemUpdate,
                        bgColor = checkUpdateColor,
                        textColor = checkUpdateOnColor,
                        enabled = updateState !is UpdateCheckState.Checking,
                        onClick = {
                            scope.launch {
                                updateState = UpdateCheckState.Checking
                                val result = withContext(Dispatchers.IO) {
                                    ProviderUpdateChecker.checkUpdate(updateUrl, provider.version)
                                }
                                updateState = if (result != null) {
                                    UpdateCheckState.HasUpdate(result)
                                } else {
                                    UpdateCheckState.NoUpdate
                                }
                            }
                        }
                    )
                }

                // 手动更新
                PillButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.manual_update),
                    icon = Icons.Rounded.FolderZip,
                    bgColor = manualUpdateColor,
                    textColor = manualUpdateOnColor,
                    onClick = {
                        onUpdateZipSelected(android.net.Uri.EMPTY)
                        onDismissRequest()
                    }
                )
            }

            // 删除按钮
            PillButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.delete_provider),
                icon = Icons.Rounded.Delete,
                bgColor = deleteColor,
                textColor = deleteOnColor,
                onClick = { showDeleteDialog = true }
            )
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_provider_title)) },
            text = {
                Text(stringResource(R.string.delete_provider_confirm, provider.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val isActive = ProviderManager.currentProvider?.id == provider.id
                        if (isActive) {
                            // 如果是当前活跃的 Provider，先切换到其他 Provider 或清除
                            val others = ModuleManager.getAvailableProviders().filter { it.id != provider.id }
                            if (others.isNotEmpty()) {
                                ProviderManager.switchProvider(others.first(), context)
                            } else {
                                ProviderManager.switchProvider(null, context)
                            }
                        }
                        provider.stopServer()
                        val success = ModuleManager.deleteModule(provider.id)
                        if (success) {
                            android.widget.Toast.makeText(context, context.getString(R.string.provider_deleted, provider.name), android.widget.Toast.LENGTH_SHORT).show()
                            onDeleted()
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.delete_failed), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class HasUpdate(val info: ProviderUpdateChecker.UpdateInfo) : UpdateCheckState()
    data object NoUpdate : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}
