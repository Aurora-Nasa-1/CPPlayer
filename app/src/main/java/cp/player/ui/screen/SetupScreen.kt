package cp.player.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import androidx.compose.ui.res.stringResource
import cp.player.R
import cp.player.ui.component.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 初始设置界面。
 *
 * 当没有已加载的 Provider 时显示，引导用户导入模块。
 * 导入成功后显示已加载的 Provider 列表供用户确认/选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importedSuccessfully by remember { mutableStateOf(false) }
    var availableProviders by remember { mutableStateOf(ModuleManager.getAvailableProviders()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val tempFile = File(context.cacheDir, "temp_module.zip")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        ModuleManager.importModule(context, tempFile)
                    } catch (e: Exception) {
                        false
                    }
                }
                isImporting = false
                if (success) {
                    availableProviders = ModuleManager.getAvailableProviders()
                    importedSuccessfully = true
                    Toast.makeText(context, context.getString(R.string.module_import_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.module_import_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.setup_title),
        onBackPressed = null
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.FolderZip,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.setup_welcome),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (isImporting) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.importing_module))
            } else if (importedSuccessfully && availableProviders.isNotEmpty()) {
                // ======================== 导入成功：显示 Provider 列表 ========================
                Text(
                    stringResource(R.string.provider_management),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                availableProviders.forEach { provider ->
                    val isActive = provider.id == ProviderManager.getCurrentProviderId()
                    cp.player.ui.component.UnifiedListItem(
                        onClick = {
                            ProviderManager.switchProvider(provider, context)
                        },
                        headlineContent = { Text(provider.name) },
                        supportingContent = {
                            Text(String.format(stringResource(R.string.provider_info), provider.type.name, provider.version))
                        },
                        trailingContent = {
                            if (isActive) {
                                Icon(
                                    Icons.Default.FolderZip,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isActive)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { launcher.launch("application/zip") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.import_new_module))
                    }
                    Button(
                        onClick = onSetupComplete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.go_back))
                    }
                }
            } else {
                Button(
                    onClick = { launcher.launch("application/zip") },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.import_new_module))
                }
            }
        }
    }
}
