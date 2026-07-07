package cp.player.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.model.UserProfile
import cp.player.provider.BackendProvider
import cp.player.util.UserPreferences
import cp.player.viewmodel.LoginViewModel

/**
 * 统一的用户账号弹窗。
 *
 * 融合了账号管理（切换、登出）和登录功能（扫码/邮箱/手机）。
 * 在当前提供商下只显示该提供商的账号，切换和添加轻量化处理。
 *
 * ### 布局结构
 * 1. **已登录态**: 当前用户头像/名称 → 当前提供商账号列表（快速切换）→ 添加账号（可展开）→ 切换提供商 → 登出
 * 2. **未登录态**: 登录表单（默认展开）→ 已保存账号列表
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
        // 如果切换到的提供商没有账号，自动展开登录表单
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ======================== 当前用户信息 ========================
                if (userProfile != null && loginViewModel.isLogged) {
                    // 已登录：显示头像 + 名称
                    Surface(
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        AsyncImage(
                            model = userProfile.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(userProfile.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ID: ${userProfile.userId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 未登录
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.not_logged_in), style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ======================== 提供商信息（紧凑行） ========================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            loginViewModel.currentProviderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (loginViewModel.availableProviders.size > 1) {
                        TextButton(
                            onClick = { showProviderPicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.switch_provider_label), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // ======================== 当前提供商的账号列表 ========================
                if (currentProviderAccounts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.provider_accounts_label, loginViewModel.currentProviderName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    currentProviderAccounts.forEach { account ->
                        val isActive = account.cookie == loginViewModel.loginCookie && loginViewModel.isLogged
                        cp.player.ui.component.UnifiedListItem(
                            onClick = {
                                if (!isActive) {
                                    loginViewModel.switchAccount(account)
                                    onFetchUserData()
                                }
                            },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(account.nickname, style = MaterialTheme.typography.bodyMedium)
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                if (account.avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = account.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(28.dp))
                                }
                            },
                            trailingContent = {
                                if (!isActive) {
                                    IconButton(
                                        onClick = {
                                            loginViewModel.removeSavedAccount(account.uid)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ======================== 添加账号（可展开） ========================
                if (!showLoginForm) {
                    cp.player.ui.component.UnifiedListItem(
                        onClick = {
                            showLoginForm = true
                            loginViewModel.prepareForNewAccount()
                        },
                        headlineContent = {
                            Text(
                                stringResource(R.string.add_another_account),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                // ======================== 内联登录表单 ========================
                AnimatedVisibility(
                    visible = showLoginForm,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // 登录方式选择（紧凑 Chip 风格）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilterChip(
                                selected = loginTab == 0,
                                onClick = { loginTab = 0 },
                                label = { Text(stringResource(R.string.login_tab_qr), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = loginTab == 1,
                                onClick = { loginTab = 1 },
                                label = { Text(stringResource(R.string.login_tab_email), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = loginTab == 2,
                                onClick = { loginTab = 2 },
                                label = { Text(stringResource(R.string.login_tab_phone), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 登录表单内容
                        when (loginTab) {
                            0 -> InlineQrLogin(loginViewModel)
                            1 -> InlineEmailLogin(email, { email = it }, password, { password = it }, loginViewModel)
                            2 -> InlinePhoneLogin(phone, { phone = it }, captcha, { captcha = it }, loginViewModel)
                        }

                        // 登录状态
                        if (loginViewModel.loginStatus != "Initializing..." && loginViewModel.loginStatus != "Already logged in") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                loginViewModel.loginStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ======================== 底部操作栏 ========================
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 提供商版本信息（7 次点击进入日志）
                    Row(
                        modifier = Modifier.clickable {
                            tapCount++
                            if (tapCount >= 7) { onNavigateToLogs(); tapCount = 0 }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "v${loginViewModel.currentProviderVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        // 登出按钮
                        if (loginViewModel.isLogged) {
                            TextButton(
                                onClick = {
                                    loginViewModel.logout()
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.sign_out_button), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        // 关闭按钮
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.close), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // ======================== 提供商切换对话框 ========================
    if (showProviderPicker) {
        StyledModalBottomSheet(onDismissRequest = { showProviderPicker = false }) {
            Text(
                stringResource(R.string.switch_provider_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                loginViewModel.availableProviders.forEach { provider ->
                    val isCurrent = provider.id == loginViewModel.currentProviderId
                    cp.player.ui.component.UnifiedListItem(
                        onClick = {
                            loginViewModel.switchProvider(provider)
                            showProviderPicker = false
                        },
                        headlineContent = { Text(provider.name) },
                        supportingContent = { Text("v${provider.version} · ${provider.type.name}") },
                        trailingContent = {
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
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
                ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = { viewModel.fetchQrCode() }) {
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { viewModel.loginWithEmail(email, password) },
        enabled = !viewModel.isLoading,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = captcha,
            onValueChange = onCaptchaChange,
            label = { Text(stringResource(R.string.captcha_or_password_label)) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall
        )
        FilledTonalButton(
            onClick = { viewModel.sendCaptcha(phone) },
            enabled = !viewModel.isLoading,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.get_code_button), style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = true) },
            enabled = !viewModel.isLoading,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text(stringResource(R.string.login_with_code), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = false) },
            enabled = !viewModel.isLoading,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            Text(stringResource(R.string.login_with_password), style = MaterialTheme.typography.labelMedium)
        }
    }
}
