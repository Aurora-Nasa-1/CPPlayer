package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences

open class BaseViewModel(application: Application) : AndroidViewModel(application) {
    var isLoading by mutableStateOf(false)
    val cookie: String? get() = UserPreferences.getCookie(getApplication())

    protected fun callApi(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val currentCookie = cookie
        val finalParams = if (!currentCookie.isNullOrEmpty() && !params.containsKey("cookie")) {
            params + ("cookie" to currentCookie)
        } else {
            params
        }
        val result = ProviderManager.callApi(method, finalParams)
        return try {
            JsonParser.parseString(result).asJsonObject
        } catch (e: Exception) {
            JsonObject().apply { addProperty("code", 500); addProperty("msg", "Parse error") }
        }
    }
}
