package cp.player.ui.screen

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.R
import cp.player.viewmodel.LoginViewModel

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cp.player.util.UserPreferences

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var savedAccounts by remember { mutableStateOf(UserPreferences.getSavedAccounts(context)) }
    
    LaunchedEffect(viewModel.isLogged) {
        if (viewModel.isLogged) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && !viewModel.isLogged && viewModel.qrCodeBitmap == null) {
            viewModel.fetchQrCode()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        @OptIn(ExperimentalMaterial3Api::class)
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.login_tab_qr)) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.login_tab_email)) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.login_tab_phone)) })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(stringResource(R.string.login_tab_guest)) })
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        when (selectedTab) {
            0 -> {
                Text(stringResource(R.string.scan_qr_login), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(32.dp))

                when {
                    viewModel.qrCodeBitmap != null -> {
                        Image(
                            bitmap = viewModel.qrCodeBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(256.dp)
                        )
                    }
                    viewModel.qrUrl != null -> {
                        AsyncImage(
                            model = viewModel.qrUrl,
                            contentDescription = "QR Code",
                            modifier = Modifier.size(256.dp)
                        )
                    }
                    else -> {
                        ContainedLoadingIndicator()
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { viewModel.fetchQrCode() }) {
                    Text(stringResource(R.string.refresh_qr))
                }
            }
            1 -> {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.loginWithEmail(email, password) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.login_with_email))
                }
            }
            2 -> {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_number_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captcha,
                        onValueChange = { captcha = it },
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
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = true) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.login_with_code))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.loginWithPhone(phone, captcha, isCaptcha = false) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.login_with_password))
                }
            }
            3 -> {
                Text(stringResource(R.string.guest_login_desc))
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.loginAnonymous() },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.guest_login_button))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(viewModel.loginStatus)
        
        if (savedAccounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.saved_accounts_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(savedAccounts.size) { index ->
                    val account = savedAccounts[index]
                    cp.player.ui.component.UnifiedListItem(
    onClick = { viewModel.switchAccount(account) },
                        headlineContent = { Text(account.nickname) },
                        supportingContent = { Text("UID: ${account.uid}") },
                        leadingContent = {
                            if (account.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = account.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                viewModel.removeSavedAccount(account.uid)
                                savedAccounts = UserPreferences.getSavedAccounts(context)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        },
                        modifier = Modifier
                            ,
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }
        }
    }
}
