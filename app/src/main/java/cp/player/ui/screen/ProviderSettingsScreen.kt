package cp.player.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import cp.player.manager.ProviderSettingsManager
import cp.player.model.ProviderSettingItem
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    providerId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val provider = remember { ModuleManager.getAvailableProviders().find { it.id == providerId } }

    var schema by remember { mutableStateOf<List<ProviderSettingItem>?>(null) }
    var currentValues by remember { mutableStateOf<MutableMap<String, Any>>(mutableMapOf()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider) {
        if (provider == null) {
            errorMessage = "Provider not found"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            val response = withContext(Dispatchers.IO) {
                provider.callApi("settings/schema", emptyMap())
            }

            val jsonObj = Gson().fromJson(response, JsonObject::class.java)
            val code = jsonObj.get("code")?.asInt ?: -1

            if (code == -1 || code == 404) {
                errorMessage = "该提供商不支持自定义设置"
            } else if (code == 200) {
                val dataArray = jsonObj.getAsJsonArray("data")
                val itemType = object : TypeToken<List<ProviderSettingItem>>() {}.type
                schema = Gson().fromJson(dataArray, itemType)

                // Get locally saved settings
                val savedSettings = ProviderSettingsManager.getSettings(context, provider.id)
                currentValues = savedSettings.toMutableMap()
            } else {
                errorMessage = jsonObj.get("msg")?.asString ?: "无法获取设置"
            }
        } catch (e: Exception) {
            errorMessage = "错误: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider?.name?.plus(" 设置") ?: "设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (schema != null) {
                        TextButton(
                            onClick = {
                                if (provider != null) {
                                    scope.launch {
                                        isSaving = true
                                        ProviderSettingsManager.saveSettings(context, provider, currentValues)
                                        isSaving = false
                                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("保存")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (schema != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(schema!!) { item ->
                        SettingItemRenderer(
                            item = item,
                            currentValue = currentValues[item.key] ?: item.defaultValue,
                            onValueChanged = { newValue ->
                                currentValues = currentValues.toMutableMap().apply {
                                    if (newValue == null) remove(item.key) else put(item.key, newValue)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItemRenderer(
    item: ProviderSettingItem,
    currentValue: Any?,
    onValueChanged: (Any?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = item.name, style = MaterialTheme.typography.titleMedium)
        if (!item.description.isNullOrEmpty()) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (item.type) {
            "switch" -> {
                val checked = (currentValue as? Boolean) ?: (item.defaultValue as? Boolean) ?: false
                Switch(
                    checked = checked,
                    onCheckedChange = { onValueChanged(it) }
                )
            }
            "input" -> {
                val text = (currentValue as? String) ?: (item.defaultValue as? String) ?: ""
                OutlinedTextField(
                    value = text,
                    onValueChange = { onValueChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            "select" -> {
                var expanded by remember { mutableStateOf(false) }
                val currentOption = item.options?.find { it.value == currentValue }
                    ?: item.options?.find { it.value == item.defaultValue }
                val label = currentOption?.label ?: currentValue?.toString() ?: "未选择"

                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(label)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        item.options?.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onValueChanged(option.value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                Text("不支持的设置类型: ${item.type}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
