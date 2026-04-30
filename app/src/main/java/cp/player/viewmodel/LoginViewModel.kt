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
import cp.player.util.UserPreferences
import cp.player.provider.ProviderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    var qrCodeBitmap by mutableStateOf<Bitmap?>(null)
    var qrUrl by mutableStateOf<String?>(null)
    var loginStatus by mutableStateOf("Initializing...")
    var isLogged by mutableStateOf(false)
    var cookie by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    private var checkJob: Job? = null
    private var fetchJob: Job? = null

    init {
        cookie = UserPreferences.getCookie(application)
        if (cookie != null) {
            isLogged = true
            loginStatus = "Already logged in"
        }
    }

    fun fetchQrCode() {
        if (fetchJob?.isActive == true) return

        loginStatus = "Fetching QR Code..."
        qrCodeBitmap = null
        qrUrl = null

        fetchJob = viewModelScope.launch {
            try {
                Log.d("LoginVM", "Fetching QR key...")
                val keyResult = withContext(Dispatchers.IO) { ProviderManager.callApi("login/qr/key", emptyMap()) }
                val keyBody = JsonParser.parseString(keyResult).asJsonObject
                val key = keyBody?.get("data")?.asJsonObject?.get("unikey")?.asString
                    ?: keyBody?.get("unikey")?.asString
                    ?: run {
                        Log.e("LoginVM", "Failed to parse unikey from: $keyBody")
                        loginStatus = "Failed to get QR key"
                        return@launch
                    }

                Log.d("LoginVM", "Creating QR image for key: $key")
                val qrResult = withContext(Dispatchers.IO) { ProviderManager.callApi("login/qr/create", mapOf("key" to key, "qrimg" to "true")) }
                val qrBody = JsonParser.parseString(qrResult).asJsonObject
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

    fun loginWithEmail(email: String, pass: String, isMd5: Boolean = false) {
        isLoading = true
        loginStatus = "Logging in..."
        viewModelScope.launch {
            try {
                val params = mutableMapOf("email" to email)
                if (isMd5) params["md5_password"] = pass else params["password"] = pass
                
                val result = withContext(Dispatchers.IO) { ProviderManager.callApi("login", params) }
                handleLoginResult(result)
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
                val params = mutableMapOf("phone" to phone)
                when {
                    isCaptcha -> params["captcha"] = passOrCaptcha
                    isMd5 -> params["md5_password"] = passOrCaptcha
                    else -> params["password"] = passOrCaptcha
                }
                
                val result = withContext(Dispatchers.IO) { ProviderManager.callApi("login/cellphone", params) }
                handleLoginResult(result)
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
                val result = withContext(Dispatchers.IO) { ProviderManager.callApi("captcha/sent", mapOf("phone" to phone)) }
                val body = JsonParser.parseString(result).asJsonObject
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

    fun logout() {
        isLoading = true
        loginStatus = "Logging out..."
        val currentCookie = cookie
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ProviderManager.callApi("logout", emptyMap()) }
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
    
    fun prepareForNewAccount() {
        // Clear active session locally without logging out from API
        // This allows LoginScreen to stay open without auto-navigating away
        isLogged = false
        cookie = null
        UserPreferences.saveCookie(getApplication(), "")
        loginStatus = "Please log in"
    }

    fun loginAnonymous() {
        isLoading = true
        loginStatus = "Logging in as guest..."
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ProviderManager.callApi("register/anonimous", emptyMap()) }
                handleLoginResult(result)
            } catch (e: Exception) {
                loginStatus = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun handleLoginResult(jsonResult: String) {
        try {
            val body = JsonParser.parseString(jsonResult).asJsonObject
            if (body?.get("code")?.asInt == 200) {
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
        
        // Fetch user info to save account
        viewModelScope.launch {
            try {
                val statusRes = withContext(Dispatchers.IO) { 
                    ProviderManager.callApi("login/status", mapOf("cookie" to newCookie)) 
                }
                val statusBody = JsonParser.parseString(statusRes).asJsonObject
                val profileJson = statusBody.get("data")?.asJsonObject?.get("profile")?.asJsonObject
                    ?: statusBody.get("profile")?.asJsonObject
                    
                if (profileJson != null) {
                    val uid = profileJson.get("userId")?.asLong ?: 0L
                    val nickname = profileJson.get("nickname")?.asString ?: "User"
                    val avatar = profileJson.get("avatarUrl")?.asString ?: ""
                    
                    UserPreferences.saveAccount(
                        getApplication(),
                        UserPreferences.SavedAccount(uid, nickname, avatar, newCookie)
                    )
                } else {
                    // If it's a guest login and has no profile
                    UserPreferences.saveAccount(
                        getApplication(),
                        UserPreferences.SavedAccount(
                            uid = System.currentTimeMillis() % 100000, 
                            nickname = "Guest", 
                            avatarUrl = "", 
                            cookie = newCookie
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("LoginVM", "Failed to fetch profile for saved account", e)
                // Save fallback guest account if network fails
                UserPreferences.saveAccount(
                    getApplication(),
                    UserPreferences.SavedAccount(
                        uid = System.currentTimeMillis() % 100000, 
                        nickname = "Guest", 
                        avatarUrl = "", 
                        cookie = newCookie
                    )
                )
            }
            
            isLogged = true
            loginStatus = "Login success!"
        }
    }
    
    fun switchAccount(account: UserPreferences.SavedAccount) {
        cookie = account.cookie
        UserPreferences.saveCookie(getApplication(), account.cookie)
        isLogged = true
        loginStatus = "Switched to ${account.nickname}"
    }

    fun removeSavedAccount(uid: Long) {
        UserPreferences.removeAccount(getApplication(), uid)
        // Note: You can trigger a UI refresh by exposing savedAccounts as a state if needed
    }

    private fun startChecking(key: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            Log.d("LoginVM", "Starting QR status check loop for key: $key")
            while (true) {
                delay(3000)
                try {
                    val statusResult = withContext(Dispatchers.IO) { ProviderManager.callApi("login/qr/check", mapOf("key" to key)) }
                    val body = JsonParser.parseString(statusResult).asJsonObject
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
