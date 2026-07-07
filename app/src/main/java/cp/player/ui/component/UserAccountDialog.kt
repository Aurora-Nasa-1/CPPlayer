package cp.player.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.UserProfile
import cp.player.provider.BackendProvider
import cp.player.util.UserPreferences
import cp.player.viewmodel.LoginViewModel

/**
 * 统一的用户账号弹窗（Material 3 Expressive 样式）。
 *
 * 融合了账号管理（切换、登出）和登录功能（扫码/邮箱/手机）。
 * 在当前提供商下只显示该提供商的账号，切换和添加轻量化处理。
 *
 * ### 布局结构
 * 1. **已登录态**: Hero 头像/名称 → 当前提供商账号列表（快速切换）→ 添加账号（可展开）→ 切换提供商 → 登出
 * 2. **未登录态**: Hero 未登录提示 → 登录表单（默认展开）→ 已保存账号列表
 */
@Composable
fun UserAccountDialog(
    loginViewModel: LoginViewModel,
    userProfile: UserProfile?,
    onDismiss: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onFetchUserData: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLoginForm by remember { mutableStateOf(userProfile == null) }
    var loginTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }

    // 当前提供商的账号列表
    var currentProviderAccounts by remember { mutableStateOf(loginViewModel.getCurrentProviderAccounts()) }

    // 提供商切换后刷新账号列表
    LaunchedEffect(loginViewModel.currentProviderId) {
        currentProviderAccounts = loginViewModel.getCurrentProviderAccounts()
        if (currentProviderAccounts.isEmpty() && !loginViewModel.isLogged) {
            showLoginForm = true
        }
    }

    // Provider 切换对话框
    var showProviderPicker by remember { mutableStateOf(false) }

    // 登录成功后刷新数据
    LaunchedEffect(loginViewModel.isLogged) {
        if (loginViewModel.isLogged && userProfile == null) {
            onFetchUserData()
            showLoginForm = false
        }
    }

    // 打开登录表单时自动获取 QR 码
    LaunchedEffect(showLoginForm, loginTab) {
        if (showLoginForm && loginTab == 0 && loginViewModel.qrCodeBitmap == null && loginViewModel.qrUrl == null) {
            loginViewModel.fetchQrCode()
        }
    }

    StyledModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ======================== Hero 区域 ========================
            AccountHeroSection(
                userProfile = userProfile,
                isLogged = loginViewModel.isLogged
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ======================== 提供商信息（紧凑行） ========================
            ProviderInfoRow(
                providerName = loginViewModel.currentProviderName,
                availableProviders = loginViewModel.availableProviders,
                onShowProviderPicker = { showProviderPicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ======================== 当前提供商的账号列表 ========================
            if (currentProviderAccounts.isNotEmpty()) {
                Text(
                    stringResource(R.string.provider_accounts_label, loginViewModel.currentProviderName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    currentProviderAccounts.forEach { account ->
                        val isActive = account.cookie == loginViewModel.loginCookie && loginViewModel.isLogged
                        AccountListItem(
                            account = account,
                            isActive = isActive,
                            onClick = {
                                if (!isActive) {
                                    loginViewModel.switchAccount(account)
                                    onFetchUserData()
                                }
                            },
                            onRemove = {
                                loginViewModel.removeSavedAccount(account.uid)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ======================== 添加账号（可展开） ========================
            AnimatedVisibility(
                visible = !showLoginForm,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedCard(
                    onClick = {
                        showLoginForm = true
                        loginViewModel.prepareForNewAccount()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.add_another_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ======================== 内联登录表单 ========================
            AnimatedVisibility(
                visible = showLoginForm,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 登录方式选择（Expressive Chip 风格）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ElevatedFilterChip(
                            selected = loginTab == 0,
                            onClick = { loginTab = 0 },
                            label = { Text(stringResource(R.string.login_tab_qr), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (loginTab == 0) {
                                { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.height(32.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ElevatedFilterChip(
                            selected = loginTab == 1,
                            onClick = { loginTab = 1 },
                            label = { Text(stringResource(R.string.login_tab_email), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (loginTab == 1) {
                                { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.height(32.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ElevatedFilterChip(
                            selected = loginTab == 2,
                            onClick = { loginTab = 2 },
                            label = { Text(stringResource(R.string.login_tab_phone), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (loginTab == 2) {
                                { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.height(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 登录表单内容（用 Surface 包裹）
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (loginTab) {
                                0 -> InlineQrLogin(loginViewModel)
                                1 -> InlineEmailLogin(email, { email = it }, password, { password = it }, loginViewModel)
                                2 -> InlinePhoneLogin(phone, { phone = it }, captcha, { captcha = it }, loginViewModel)
                            }
                        }
                    }

                    // 登录状态
                    AnimatedVisibility(
                        visible = loginViewModel.loginStatus != "Initializing..." && loginViewModel.loginStatus != "Already logged in" && loginViewModel.loginStatus.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            loginViewModel.loginStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ======================== 底部操作栏 ========================
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            BottomActionBar(
                providerVersion = loginViewModel.currentProviderVersion,
                isLogged = loginViewModel.isLogged,
                onTapVersion = {
                    tapCount++
                    if (tapCount >= 7) { onNavigateToLogs(); tapCount = 0 }
                },
                onLogout = {
                    loginViewModel.logout()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
    }

    // ======================== 提供商切换对话框 ========================
    if (showProviderPicker) {
        StyledModalBottomSheet(onDismissRequest = { showProviderPicker = false }) {
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
                loginViewModel.availableProviders.forEach { provider ->
                    val isCurrent = provider.id == loginViewModel.currentProviderId
                    ElevatedCard(
                        onClick = {
                            loginViewModel.switchProvider(provider)
                            showProviderPicker = false
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
                                    contentDescription = null,
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

// ======================== Hero 区域 ========================

@Composable
private fun AccountHeroSection(
    userProfile: UserProfile?,
    isLogged: Boolean
) {
    if (userProfile != null && isLogged) {
        // 已登录：头像 + 名称
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景圆环
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            // 头像
            AsyncImage(
                model = userProfile.avatarUrl,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            userProfile.nickname,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "ID: ${userProfile.userId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        // 未登录：图标 + 提示
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.not_logged_in),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "登录以同步你的音乐",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ======================== 提供商信息行 ========================

@Composable
private fun ProviderInfoRow(
    providerName: String,
    availableProviders: List<BackendProvider>,
    onShowProviderPicker: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    providerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (availableProviders.size > 1) {
                TextButton(
                    onClick = onShowProviderPicker,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.switch_provider_label), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ======================== 账号列表项 ========================

@Composable
private fun AccountListItem(
    account: UserPreferences.SavedAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
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
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 名称
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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

            // 删除按钮
            if (!isActive) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
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

// ======================== 内联扫码登录 ========================

@Composable
private fun InlineQrLogin(viewModel: LoginViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            viewModel.qrCodeBitmap != null -> {
                Image(
                    bitmap = viewModel.qrCodeBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(140.dp)
                )
            }
            viewModel.qrUrl != null -> {
                AsyncImage(
                    model = viewModel.qrUrl,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(140.dp)
                )
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { viewModel.fetchQrCode() },
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.refresh_qr), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ======================== 内联邮箱登录 ========================

@Composable
private fun InlineEmailLogin(
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
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
        shape = MaterialTheme.shapes.small
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.password_label)) },
        placeholder = { Text("••••••••") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
        shape = MaterialTheme.shapes.small
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = { viewModel.loginWithEmail(email, password) },
        enabled = !viewModel.isLoading && email.isNotEmpty() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 10.dp),
        shape = MaterialTheme.shapes.small
    ) {
        if (viewModel.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(stringResource(R.string.login_with_email), style = MaterialTheme.typography.labelMedium)
    }
}

// ======================== 内联手机登录 ========================

@Composable
private fun InlinePhoneLogin(
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
            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
        shape = MaterialTheme.shapes.small
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            label = { Text(stringResource(R.string.captcha_or_password_label)) },
            leadingIcon = {
                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.small
        )
        FilledTonalButton(
            onClick = { viewModel.sendCaptcha(phone) },
            enabled = !viewModel.isLoading && phone.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(stringResource(R.string.get_code_button), style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = true) },
            enabled = !viewModel.isLoading && phone.isNotEmpty() && captcha.isNotEmpty(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            shape = MaterialTheme.shapes.small
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(stringResource(R.string.login_with_code), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = false) },
            enabled = !viewModel.isLoading && phone.isNotEmpty() && captcha.isNotEmpty(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(stringResource(R.string.login_with_password), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ======================== 底部操作栏 ========================

@Composable
private fun BottomActionBar(
    providerVersion: String,
    isLogged: Boolean,
    onTapVersion: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 提供商版本信息（7 次点击进入日志）
        Row(
            modifier = Modifier.clickable { onTapVersion() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "v$providerVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            // 登出按钮
            if (isLogged) {
                FilledTonalButton(
                    onClick = onLogout,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.sign_out_button), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            // 关闭按钮
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(stringResource(R.string.close), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
