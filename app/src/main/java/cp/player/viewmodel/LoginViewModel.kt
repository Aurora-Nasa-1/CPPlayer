package cp.player.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.net.URLEncoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import cp.player.api.MusicApiService
import cp.player.api.MusicApiServiceFactory
import cp.player.provider.BackendProvider
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 登录 ViewModel。
 *
 * 管理登录流程（扫码/邮箱/手机/游客）以及账号切换。
 * 提供商感知：所有操作都绑定到当前活跃的 [ProviderManager.currentProvider]。
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val api: MusicApiService = MusicApiServiceFactory.instance

    // ======================== UI 状态 ========================

    var qrCodeBitmap by mutableStateOf<Bitmap?>(null)
    var qrUrl by mutableStateOf<String?>(null)
    var loginStatus by mutableStateOf("Initializing...")
    var isLogged by mutableStateOf(false)
    var cookie by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    /** 当前 Provider 的显示名称（用于 LoginScreen 显示） */
    var currentProviderName by mutableStateOf(ProviderManager.getCurrentProviderName())
        private set

    /** 当前 Provider 的 ID */
    var currentProviderId by mutableStateOf(ProviderManager.getCurrentProviderId())
        private set

    /** 当前 Provider 的版本号 */
    var currentProviderVersion by mutableStateOf(ProviderManager.currentProvider?.version ?: "")
        private set

    /** 所有已加载的 Provider 列表 */
    var availableProviders by mutableStateOf(ModuleManager.getAvailableProviders())
        private set

    private var checkJob: Job? = null
    private var fetchJob: Job? = null

    init {
        cookie = UserPreferences.getCookie(application)
        if (cookie != null) {
            isLogged = true
            loginStatus = "Already logged in"
        }
        refreshProviderState()
    }

    // ======================== Provider 管理 ========================

    /**
     * 切换当前 Provider。
     * 切换后刷新 UI 状态、cookie 和 Provider 信息。
     */
    fun switchProvider(provider: BackendProvider) {
        val context = getApplication<Application>()
        ProviderManager.switchProvider(provider, context)
        refreshProviderState()

        // 切换 Provider 后，加载该 Provider 的 cookie
        cookie = UserPreferences.getCookie(context)
        if (cookie != null) {
            isLogged = true
            loginStatus = "已切换到 ${provider.name}"
        } else {
            isLogged = false
            cookie = null
            loginStatus = "请登录 ${provider.name}"
        }

        // 清除旧的 QR 码状态，准备为新提供商获取
        qrCodeBitmap = null
        qrUrl = null
        checkJob?.cancel()
        fetchJob?.cancel()
    }

    /**
     * 刷新 Provider 相关状态。
     */
    private fun refreshProviderState() {
        currentProviderName = ProviderManager.getCurrentProviderName()
        currentProviderId = ProviderManager.getCurrentProviderId()
        currentProviderVersion = ProviderManager.currentProvider?.version ?: ""
        availableProviders = ModuleManager.getAvailableProviders()
    }

    // ======================== 扫码登录 ========================

    fun fetchQrCode() {
        // 取消之前的请求，确保新请求能执行
        fetchJob?.cancel()
        checkJob?.cancel()

        loginStatus = "获取二维码中..."
        qrCodeBitmap = null
        qrUrl = null

        fetchJob = viewModelScope.launch {
            try {
                Log.d("LoginVM", "Fetching QR key...")
                val keyBody = withContext(Dispatchers.IO) { api.getQrKey() }
                val key = keyBody?.get("data")?.asJsonObject?.get("unikey")?.asString
                    ?: keyBody?.get("unikey")?.asString
                    ?: run {
                        Log.e("LoginVM", "Failed to parse unikey from: $keyBody")
                        loginStatus = "Failed to get QR key"
                        return@launch
                    }

                Log.d("LoginVM", "Creating QR image for key: $key")
                val qrBody = withContext(Dispatchers.IO) { api.createQrCode(key) }
                val qrData = qrBody?.get("data")?.asJsonObject
                val qrImg = qrData?.get("qrimg")?.asString
                val qrUrlFromApi = qrData?.get("qrurl")?.asString

                if (qrUrlFromApi != null) {
                    val encodedUrl = URLEncoder.encode(qrUrlFromApi, "UTF-8")
                    qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=$encodedUrl"
                }

                if (!qrImg.isNullOrEmpty()) {
                    Log.d("LoginVM", "Decoding QR image (length: ${qrImg.length})...")
                    val base64Data = qrImg.substringAfter(",")
                    val decodedString: ByteArray = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

                    if (bitmap != null) {
                        qrCodeBitmap = bitmap
                        loginStatus = "Waiting for scan..."
                        startChecking(key)
                        return@launch
                    }
                }

                if (qrUrl != null) {
                    Log.d("LoginVM", "Using fallback QR URL: $qrUrl")
                    loginStatus = "Waiting for scan..."
                    startChecking(key)
                } else {
                    Log.e("LoginVM", "No QR image or URL available")
                    loginStatus = "Failed to generate QR"
                }
            } catch (e: Exception) {
                Log.e("LoginVM", "Error fetching QR code", e)
                loginStatus = "Error: ${e.message}"
            }
        }
    }

    // ======================== 邮箱/手机登录 ========================

    fun loginWithEmail(email: String, pass: String, isMd5: Boolean = false) {
        isLoading = true
        loginStatus = "Logging in..."
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { api.login(email, pass, isMd5) }
                handleLoginResult(body)
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loginWithPhone(phone: String, passOrCaptcha: String, isCaptcha: Boolean = false, isMd5: Boolean = false) {
        isLoading = true
        loginStatus = "Logging in..."
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { api.loginWithPhone(phone, passOrCaptcha, isCaptcha, isMd5) }
                handleLoginResult(body)
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun sendCaptcha(phone: String) {
        isLoading = true
        loginStatus = "Sending captcha..."
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { api.sendCaptcha(phone) }
                if (body?.get("code")?.asInt == 200) {
                    loginStatus = "Captcha sent"
                } else {
                    loginStatus = body?.get("msg")?.asString ?: "Failed to send captcha"
                }
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ======================== 游客登录 ========================

    fun loginAnonymous() {
        isLoading = true
        loginStatus = "Logging in as guest..."
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { api.loginAnonymous() }
                handleLoginResult(body)
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ======================== 登出 ========================

    fun logout() {
        isLoading = true
        loginStatus = "Logging out..."
        val currentCookie = cookie
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.logout() }
            } catch (e: Exception) {
                Log.e("LoginVM", "Logout API error", e)
            } finally {
                if (currentCookie != null) {
                    val acc = UserPreferences.getSavedAccounts(getApplication()).find { it.cookie == currentCookie }
                    if (acc != null) {
                        UserPreferences.removeAccount(getApplication(), acc.uid)
                    }
                }
                cookie = null
                UserPreferences.saveCookie(getApplication(), "")
                isLogged = false
                loginStatus = "Logged out"
                isLoading = false
            }
        }
    }

    // ======================== 账号切换 ========================

    /** 是否正在切换账号（用于 UI 显示加载状态） */
    var isSwitchingAccount by mutableStateOf(false)
        private set

    /**
     * 切换到已保存的账号。
     * 如果账号属于不同的 Provider，会自动切换 Provider。
     * 切换成功后自动关闭登录弹窗。
     */
    fun switchAccount(account: UserPreferences.SavedAccount) {
        val context = getApplication<Application>()
        isSwitchingAccount = true

        // 如果账号属于不同的 Provider，先切换 Provider
        if (account.providerId.isNotEmpty() && account.providerId != currentProviderId) {
            ProviderManager.switchProviderById(account.providerId, context)
            refreshProviderState()
        }

        cookie = account.cookie
        UserPreferences.saveCookie(context, account.cookie)
        isLogged = true
        loginStatus = "已切换到 ${account.nickname}"
        isSwitchingAccount = false
    }

    fun removeSavedAccount(uid: Long, providerId: String? = null) {
        UserPreferences.removeAccount(getApplication(), uid, providerId)
    }

    /**
     * 获取所有已保存的账号（跨提供商）。
     */
    fun getAllSavedAccounts(): List<UserPreferences.SavedAccount> {
        return UserPreferences.getAllSavedAccounts(getApplication())
    }

    /**
     * 获取当前提供商下保存的账号。
     */
    fun getCurrentProviderAccounts(): List<UserPreferences.SavedAccount> {
        return UserPreferences.getSavedAccounts(getApplication())
    }

    /**
     * 当前提供商是否有已保存的账号。
     */
    fun hasCurrentProviderAccounts(): Boolean {
        return UserPreferences.getSavedAccounts(getApplication()).isNotEmpty()
    }

    /**
     * 准备添加新账号。
     * 清除当前登录状态，但保留提供商上下文。
     * 调用后 UI 应切换到扫码 Tab 并自动获取 QR 码。
     */
    fun prepareForNewAccount() {
        isLogged = false
        cookie = null
        UserPreferences.saveCookie(getApplication(), "")
        qrCodeBitmap = null
        qrUrl = null
        checkJob?.cancel()
        fetchJob?.cancel()
        loginStatus = "请登录 $currentProviderName"
        // 自动获取新 QR 码
        fetchQrCode()
    }

    // ======================== 登录结果处理 ========================

    private fun handleLoginResult(body: JsonObject) {
        try {
            if (body.get("code")?.asInt == 200) {
                val rawCookie = if (body.has("cookie") && !body.get("cookie").isJsonNull) body.get("cookie").asString else null
                val newCookie = if (rawCookie.isNullOrEmpty()) "guest_cookie_${System.currentTimeMillis()}" else rawCookie
                onLoginSuccess(newCookie)
            } else {
                loginStatus = body?.get("msg")?.asString ?: "Login failed (${body?.get("code")?.asInt})"
            }
        } catch (e: Exception) {
            loginStatus = "Parse error"
        }
    }

    private fun onLoginSuccess(newCookie: String) {
        cookie = newCookie
        UserPreferences.saveCookie(getApplication(), newCookie)

        viewModelScope.launch {
            try {
                val statusBody = withContext(Dispatchers.IO) {
                    api.getLoginStatus(newCookie)
                }
                val profileJson = statusBody.get("data")?.asJsonObject?.get("profile")?.asJsonObject
                    ?: statusBody.get("profile")?.asJsonObject

                if (profileJson != null) {
                    val uid = profileJson.get("userId")?.asLong ?: 0L
                    val nickname = profileJson.get("nickname")?.asString ?: "User"
                    val avatar = profileJson.get("avatarUrl")?.asString ?: ""

                    UserPreferences.saveAccount(
                        getApplication(),
                        UserPreferences.SavedAccount(
                            uid = uid,
                            nickname = nickname,
                            avatarUrl = avatar,
                            cookie = newCookie,
                            providerId = currentProviderId,
                            providerName = currentProviderName
                        )
                    )
                } else {
                    UserPreferences.saveAccount(
                        getApplication(),
                        UserPreferences.SavedAccount(
                            uid = System.currentTimeMillis() % 100000,
                            nickname = "Guest",
                            avatarUrl = "",
                            cookie = newCookie,
                            providerId = currentProviderId,
                            providerName = currentProviderName
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("LoginVM", "Failed to fetch profile for saved account", e)
                UserPreferences.saveAccount(
                    getApplication(),
                    UserPreferences.SavedAccount(
                        uid = System.currentTimeMillis() % 100000,
                        nickname = "Guest",
                        avatarUrl = "",
                        cookie = newCookie,
                        providerId = currentProviderId,
                        providerName = currentProviderName
                    )
                )
            }

            isLogged = true
            loginStatus = "Login success!"
        }
    }

    // ======================== 扫码状态检查 ========================

    private fun startChecking(key: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            Log.d("LoginVM", "Starting QR status check loop for key: $key")
            while (true) {
                delay(3000)
                try {
                    val body = withContext(Dispatchers.IO) { api.checkQrStatus(key) }
                    val code = body?.get("code")?.asInt ?: 0
                    Log.d("LoginVM", "QR status code: $code")
                    when (code) {
                        800 -> {
                            loginStatus = "QR Code expired"
                            break
                        }
                        801 -> loginStatus = "Waiting for scan..."
                        802 -> loginStatus = "Waiting for confirmation..."
                        803 -> {
                            Log.d("LoginVM", "Login successful")
                            val newCookie = body?.get("cookie")?.asString ?: "guest_cookie_${System.currentTimeMillis()}"
                            onLoginSuccess(newCookie)
                            break
                        }
                        else -> {
                            Log.w("LoginVM", "Unexpected status code: $code")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginVM", "Error checking QR status", e)
                    loginStatus = "Check error: ${e.message}"
                }
            }
        }
    }
}
