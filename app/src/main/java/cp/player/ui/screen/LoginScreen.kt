package cp.player.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.ui.component.StyledModalBottomSheet
import cp.player.viewmodel.LoginViewModel

/**
 * 登录弹窗（Material 3 Expressive 样式）。
 *
 * 以全屏弹窗形式展示，用户可以随时跳过（关闭）直接使用应用。
 * 包含：
 * - Hero 区域（应用图标 + 欢迎文案）
 * - 提供商选择器（多 Provider 时显示）
 * - 登录方式 FilterChip（扫码/邮箱/手机）
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

    // 弹窗打开时自动获取二维码（如果未登录且没有已恢复的 QR 会话）
    LaunchedEffect(Unit) {
        if (!viewModel.isLogged && viewModel.qrUrl == null) {
            viewModel.fetchQrCode()
        }
    }

    // Provider 切换后刷新账号列表和 QR 码（跳过已恢复的会话）
    LaunchedEffect(viewModel.currentProviderId) {
        allSavedAccounts = viewModel.getAllSavedAccounts()
        if (!viewModel.isLogged && viewModel.qrUrl == null) {
            viewModel.fetchQrCode()
        }
    }

    // 当前提供商的账号（优先显示）
    val currentProviderAccounts = allSavedAccounts.filter { it.providerId == viewModel.currentProviderId }
    val otherProviderAccounts = allSavedAccounts.filter { it.providerId != viewModel.currentProviderId }

    var showLoginForm by remember { mutableStateOf(currentProviderAccounts.isEmpty() || !viewModel.isLogged) }

    LaunchedEffect(currentProviderAccounts.size, viewModel.isLogged) {
        if (currentProviderAccounts.isEmpty() && !viewModel.isLogged) {
            showLoginForm = true
        }
    }

    StyledModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ======================== Hero 区域 ========================
            HeroSection(onDismiss = onDismiss)

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // ======================== 当前提供商账号（优先显示） ========================
            if (currentProviderAccounts.isNotEmpty()) {
                Text(
                    "${viewModel.currentProviderName} 账号",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                // 添加新账号按钮
                OutlinedButton(
                    onClick = {
                        showLoginForm = true; selectedTab = 0
                        viewModel.prepareForNewAccount()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isSwitchingAccount,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加新账号")
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ======================== 其他提供商账号 ========================
            if (otherProviderAccounts.isNotEmpty()) {
                Text(
                    "其他提供商账号",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showLoginForm) {
                // ======================== 登录方式 FilterChip ========================
                LoginMethodChips(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

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
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "当前提供商 (${viewModel.currentProviderName}) 暂无已保存账号，请登录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    when (selectedTab) {
                        0 -> QrLoginContent(viewModel)
                        1 -> EmailLoginContent(email, { email = it }, password, { password = it }, viewModel)
                        2 -> PhoneLoginContent(phone, { phone = it }, captcha, { captcha = it }, viewModel)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 登录状态
                    AnimatedVisibility(
                        visible = viewModel.loginStatus.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            viewModel.loginStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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

// ======================== Hero 区域 ========================

@Composable
private fun HeroSection(onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 关闭按钮
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }

        // Hero 内容
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 应用图标
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "登录以同步你的音乐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ======================== 登录方式 FilterChip ========================

@Composable
private fun LoginMethodChips(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ElevatedFilterChip(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            label = { Text(stringResource(R.string.login_tab_qr)) },
            leadingIcon = if (selectedTab == 0) {
                { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.height(36.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ElevatedFilterChip(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            label = { Text(stringResource(R.string.login_tab_email)) },
            leadingIcon = if (selectedTab == 1) {
                { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.height(36.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ElevatedFilterChip(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            label = { Text(stringResource(R.string.login_tab_phone)) },
            leadingIcon = if (selectedTab == 2) {
                { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.height(36.dp)
        )
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
    ElevatedCard(
        onClick = onSwitch,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            if (account.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 名称 + 标签
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.nickname,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (account.providerName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isCurrentProvider)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            account.providerName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (isCurrentProvider)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 删除按钮
            if (!isActive) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
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
            }

            if (availableProviders.size > 1) {
                FilledTonalButton(
                    onClick = { onShowPickerChange(true) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.switch_provider_label), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showPicker && availableProviders.size > 1) {
        StyledModalBottomSheet(onDismissRequest = { onShowPickerChange(false) }) {
            Text(
                stringResource(R.string.switch_provider_label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableProviders.forEach { provider ->
                    val isCurrent = provider.name == currentProviderName
                    ElevatedCard(
                        onClick = {
                            onProviderSelected(provider)
                            onShowPickerChange(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isCurrent)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal
                                )
                                Text(
                                    "v${provider.version} · ${provider.type.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Current",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

// ======================== 扫码登录 ========================

@Composable
private fun QrLoginContent(viewModel: LoginViewModel) {
    Text(
        stringResource(R.string.scan_qr_login),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(16.dp))

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier.padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
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
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
        onClick = { viewModel.fetchQrCode() },
        shape = MaterialTheme.shapes.small
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
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
        placeholder = { Text("example@mail.com") },
        leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.password_label)) },
        placeholder = { Text("••••••••") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = { viewModel.loginWithEmail(email, password) },
        enabled = !viewModel.isLoading && email.isNotEmpty() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        if (viewModel.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
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
        placeholder = { Text("13800138000") },
        leadingIcon = {
            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            label = { Text(stringResource(R.string.captcha_or_password_label)) },
            leadingIcon = {
                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium
        )
        FilledTonalButton(
            onClick = { viewModel.sendCaptcha(phone) },
            enabled = !viewModel.isLoading && phone.isNotEmpty(),
            modifier = Modifier.align(Alignment.CenterVertically),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.get_code_button), style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = true) },
            enabled = !viewModel.isLoading && phone.isNotEmpty() && captcha.isNotEmpty(),
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(stringResource(R.string.login_with_code))
        }
        OutlinedButton(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = false) },
            enabled = !viewModel.isLoading && phone.isNotEmpty() && captcha.isNotEmpty(),
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(stringResource(R.string.login_with_password))
        }
    }
}
