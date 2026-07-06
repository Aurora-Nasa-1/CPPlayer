package cp.player.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.ui.component.StyledModalBottomSheet
import cp.player.viewmodel.LoginViewModel

/**
 * 登录弹窗。
 *
 * 以全屏弹窗形式展示，用户可以随时跳过（关闭）直接使用应用。
 * 包含：
 * - 提供商选择器（多 Provider 时显示）
 * - 登录方式 Tab（扫码/邮箱/手机，无游客模式）
 * - 跨提供商的已保存账号列表
 * - 添加新账号按钮
 *
 * @param showDialog 是否显示弹窗
 * @param onDismiss 关闭弹窗（跳过登录）
 * @param onLoginSuccess 登录成功回调
 */
@Composable
fun LoginDialog(
    viewModel: LoginViewModel,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit = {}
) {
    if (!showDialog) return

    var selectedTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var allSavedAccounts by remember { mutableStateOf(viewModel.getAllSavedAccounts()) }
    var showProviderPicker by remember { mutableStateOf(false) }

    // 登录成功后自动关闭弹窗
    LaunchedEffect(viewModel.isLogged) {
        if (viewModel.isLogged) {
            onLoginSuccess()
            onDismiss()
        }
    }

    // 弹窗打开时自动获取二维码
    LaunchedEffect(Unit) {
        if (!viewModel.isLogged) {
            viewModel.fetchQrCode()
        }
    }

    // Provider 切换后刷新账号列表和 QR 码
    LaunchedEffect(viewModel.currentProviderId) {
        allSavedAccounts = viewModel.getAllSavedAccounts()
        if (!viewModel.isLogged) {
            viewModel.fetchQrCode()
        }
    }

    // 当前提供商的账号（优先显示）
    val currentProviderAccounts = allSavedAccounts.filter { it.providerId == viewModel.currentProviderId }
    val otherProviderAccounts = allSavedAccounts.filter { it.providerId != viewModel.currentProviderId }

    StyledModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ======================== 标题栏 ========================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ======================== 提供商选择器 ========================
                ProviderSelector(
                    currentProviderName = viewModel.currentProviderName,
                    availableProviders = viewModel.availableProviders,
                    showPicker = showProviderPicker,
                    onShowPickerChange = { showProviderPicker = it },
                    onProviderSelected = { provider ->
                        viewModel.switchProvider(provider)
                        showProviderPicker = false
                        allSavedAccounts = viewModel.getAllSavedAccounts()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ======================== 当前提供商账号（优先显示） ========================
                if (currentProviderAccounts.isNotEmpty()) {
                    Text(
                        "${viewModel.currentProviderName} 账号",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(currentProviderAccounts.size) { index ->
                            val account = currentProviderAccounts[index]
                            val isActive = account.cookie == viewModel.cookie && viewModel.isLogged

                            AccountItem(
                                account = account,
                                isActive = isActive,
                                isCurrentProvider = true,
                                onSwitch = { viewModel.switchAccount(account) },
                                onRemove = {
                                    viewModel.removeSavedAccount(account.uid)
                                    allSavedAccounts = viewModel.getAllSavedAccounts()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 添加新账号按钮
                    OutlinedButton(
                        onClick = {
                            selectedTab = 0
                            viewModel.prepareForNewAccount()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isSwitchingAccount
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加新账号")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ======================== 其他提供商账号 ========================
                if (otherProviderAccounts.isNotEmpty()) {
                    Text(
                        "其他提供商账号",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(otherProviderAccounts.size) { index ->
                            val account = otherProviderAccounts[index]

                            AccountItem(
                                account = account,
                                isActive = false,
                                isCurrentProvider = false,
                                onSwitch = { viewModel.switchAccount(account) },
                                onRemove = {
                                    viewModel.removeSavedAccount(account.uid, account.providerId)
                                    allSavedAccounts = viewModel.getAllSavedAccounts()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ======================== 登录方式 Tab ========================
                @OptIn(ExperimentalMaterial3Api::class)
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.login_tab_qr)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.login_tab_email)) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.login_tab_phone)) })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ======================== 登录表单（可滚动区域） ========================
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 提供商无账号时的提示
                    if (currentProviderAccounts.isEmpty() && !viewModel.isLogged) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "当前提供商 (${viewModel.currentProviderName}) 暂无已保存账号，请登录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    when (selectedTab) {
                        0 -> QrLoginContent(viewModel)
                        1 -> EmailLoginContent(email, { email = it }, password, { password = it }, viewModel)
                        2 -> PhoneLoginContent(phone, { phone = it }, captcha, { captcha = it }, viewModel)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        viewModel.loginStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 提供商版本信息
                    if (viewModel.currentProviderVersion.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "v${viewModel.currentProviderVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ======================== 账号列表项 ========================

@Composable
private fun AccountItem(
    account: cp.player.util.UserPreferences.SavedAccount,
    isActive: Boolean,
    isCurrentProvider: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    cp.player.ui.component.UnifiedListItem(
        onClick = onSwitch,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.nickname, style = MaterialTheme.typography.bodyMedium)
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = {
            if (account.providerName.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (isCurrentProvider)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        account.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        color = if (isCurrentProvider)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            if (account.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp).padding(4.dp))
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

// ======================== 提供商选择器组件 ========================

@Composable
private fun ProviderSelector(
    currentProviderName: String,
    availableProviders: List<cp.player.provider.BackendProvider>,
    showPicker: Boolean,
    onShowPickerChange: (Boolean) -> Unit,
    onProviderSelected: (cp.player.provider.BackendProvider) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.current_provider_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    currentProviderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            if (availableProviders.size > 1) {
                FilledTonalButton(
                    onClick = { onShowPickerChange(true) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.switch_provider_label), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showPicker && availableProviders.size > 1) {
        StyledModalBottomSheet(onDismissRequest = { onShowPickerChange(false) }) {
            Text(
                stringResource(R.string.switch_provider_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                availableProviders.forEach { provider ->
                    val isCurrent = provider.name == currentProviderName
                    cp.player.ui.component.UnifiedListItem(
                        onClick = {
                            onProviderSelected(provider)
                            onShowPickerChange(false)
                        },
                        headlineContent = { Text(provider.name) },
                        supportingContent = { Text("v${provider.version} · ${provider.type.name}") },
                        trailingContent = {
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isCurrent)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

// ======================== 扫码登录 ========================

@Composable
private fun QrLoginContent(viewModel: LoginViewModel) {
    Text(stringResource(R.string.scan_qr_login), style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(12.dp))

    when {
        viewModel.qrCodeBitmap != null -> {
            Image(
                bitmap = viewModel.qrCodeBitmap!!.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.size(180.dp)
            )
        }
        viewModel.qrUrl != null -> {
            AsyncImage(
                model = viewModel.qrUrl,
                contentDescription = "QR Code",
                modifier = Modifier.size(180.dp)
            )
        }
        else -> {
            ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(onClick = { viewModel.fetchQrCode() }) {
        Text(stringResource(R.string.refresh_qr))
    }
}

// ======================== 邮箱登录 ========================

@Composable
private fun EmailLoginContent(
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    viewModel: LoginViewModel
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.email_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { viewModel.loginWithEmail(email, password) },
        enabled = !viewModel.isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.login_with_email))
    }
}

// ======================== 手机登录 ========================

@Composable
private fun PhoneLoginContent(
    phone: String, onPhoneChange: (String) -> Unit,
    captcha: String, onCaptchaChange: (String) -> Unit,
    viewModel: LoginViewModel
) {
    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text(stringResource(R.string.phone_number_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            label = { Text(stringResource(R.string.captcha_or_password_label)) },
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { viewModel.sendCaptcha(phone) },
            enabled = !viewModel.isLoading,
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(stringResource(R.string.get_code_button))
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = true) },
            enabled = !viewModel.isLoading,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.login_with_code))
        }
        OutlinedButton(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = false) },
            enabled = !viewModel.isLoading,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.login_with_password))
        }
    }
}
