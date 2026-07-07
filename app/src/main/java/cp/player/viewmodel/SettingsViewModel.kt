package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import cp.player.util.CacheManager
import cp.player.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {
    var qualityWifi by mutableStateOf(UserPreferences.getQualityWifi(application))
        private set
    var qualityCellular by mutableStateOf(UserPreferences.getQualityCellular(application))
        private set
    var fadeDuration by mutableStateOf(UserPreferences.getFadeDuration(application))
        private set
    var cacheSize by mutableIntStateOf(UserPreferences.getCacheSize(application))
        private set
    var useCellularCache by mutableStateOf(UserPreferences.getUseCellularCache(application))
        private set
    var pureBlackMode by mutableStateOf(UserPreferences.getPureBlackMode(application))
        private set
    var themeMode by mutableIntStateOf(UserPreferences.getThemeMode(application))
        private set
    var followCoverApp by mutableStateOf(UserPreferences.getFollowCoverApp(application))
        private set
    var followCoverMini by mutableStateOf(UserPreferences.getFollowCoverMini(application))
        private set
    var followCoverPlayer by mutableStateOf(UserPreferences.getFollowCoverPlayer(application))
        private set
    var useFluidBackground by mutableStateOf(UserPreferences.getUseFluidBackground(application))
        private set
    var audioFocusMode by mutableIntStateOf(UserPreferences.getAudioFocusMode(application))
        private set
    var allowDucking by mutableStateOf(UserPreferences.getAllowDucking(application))
        private set
    var pauseOnNoisy by mutableStateOf(UserPreferences.getPauseOnNoisy(application))
        private set
    var autoResumeUsbAudio by mutableStateOf(UserPreferences.getAutoResumeUsbAudio(application))
        private set
    var fadeMode by mutableIntStateOf(UserPreferences.getFadeMode(application))
        private set
    var autoAudioFocus by mutableStateOf(UserPreferences.getAutoAudioFocus(application))
        private set
    var downloadDir by mutableStateOf(UserPreferences.getDownloadDir(application))
        private set

    var audioEngine by mutableIntStateOf(UserPreferences.getAudioEngine(application))
        private set
    var dsdOutputMode by mutableIntStateOf(UserPreferences.getDsdOutputMode(application))
        private set
    var dapBitPerfect by mutableStateOf(UserPreferences.getDapBitPerfect(application))
        private set
    var usbExclusive by mutableStateOf(UserPreferences.getUsbExclusive(application))
        private set
    var fontRoundness by mutableIntStateOf(UserPreferences.getFontRoundness(application))
        private set
    var playImmediately by mutableStateOf(UserPreferences.getPlayImmediately(application))
        private set
    var lyricsSource by mutableIntStateOf(UserPreferences.getLyricsSource(application))
        private set
    var amllPlatform by mutableStateOf(UserPreferences.getAmllPlatform(application))
        private set
    var hideNavbarOnScroll by mutableStateOf(UserPreferences.getHideNavbarOnScroll(application))
        private set
    var wavyProgress by mutableStateOf(UserPreferences.getWavyProgress(application))
        private set
    var restoreLastQueue by mutableStateOf(UserPreferences.getRestoreLastQueue(application))
        private set

    fun updateQualityWifi(q: String) {
        qualityWifi = q
        UserPreferences.saveQualityWifi(getApplication(), q)
    }

    fun updateQualityCellular(q: String) {
        qualityCellular = q
        UserPreferences.saveQualityCellular(getApplication(), q)
    }

    fun updateFade(d: Float) {
        fadeDuration = d
        UserPreferences.saveFadeDuration(getApplication(), d)
    }

    fun updateFadeMode(m: Int) {
        fadeMode = m
        UserPreferences.saveFadeMode(getApplication(), m)
    }

    fun updateAutoAudioFocus(a: Boolean) {
        autoAudioFocus = a
        UserPreferences.saveAutoAudioFocus(getApplication(), a)
    }

    fun updateCache(s: Int) {
        cacheSize = s
        UserPreferences.saveCacheSize(getApplication(), s)
    }

    fun updateUseCellular(u: Boolean) {
        useCellularCache = u
        UserPreferences.saveUseCellularCache(getApplication(), u)
    }

    fun updatePureBlackMode(e: Boolean) {
        pureBlackMode = e
        UserPreferences.savePureBlackMode(getApplication(), e)
    }

    fun updateThemeMode(m: Int) {
        themeMode = m
        UserPreferences.saveThemeMode(getApplication(), m)
    }

    fun updateFollowCoverApp(e: Boolean) {
        followCoverApp = e
        UserPreferences.saveFollowCoverApp(getApplication(), e)
    }

    fun updateFollowCoverMini(e: Boolean) {
        followCoverMini = e
        UserPreferences.saveFollowCoverMini(getApplication(), e)
    }

    fun updateFollowCoverPlayer(e: Boolean) {
        followCoverPlayer = e
        UserPreferences.saveFollowCoverPlayer(getApplication(), e)
    }

    fun updateUseFluidBackground(e: Boolean) {
        useFluidBackground = e
        UserPreferences.saveUseFluidBackground(getApplication(), e)
    }

    fun updateAudioFocusMode(m: Int) {
        audioFocusMode = m
        UserPreferences.saveAudioFocusMode(getApplication(), m)
    }

    fun updateAllowDucking(a: Boolean) {
        allowDucking = a
        UserPreferences.saveAllowDucking(getApplication(), a)
    }

    fun updatePauseOnNoisy(p: Boolean) {
        pauseOnNoisy = p
        UserPreferences.savePauseOnNoisy(getApplication(), p)
    }

    fun updateAutoResumeUsbAudio(r: Boolean) {
        autoResumeUsbAudio = r
        UserPreferences.saveAutoResumeUsbAudio(getApplication(), r)
    }

    fun updateDownloadPath(p: String) {
        downloadDir = p
        UserPreferences.saveDownloadDir(getApplication(), p)
    }

    fun updateAudioEngine(engine: Int) {
        audioEngine = engine
        val app = getApplication<Application>()
        UserPreferences.saveAudioEngine(app, engine)
        
        android.widget.Toast.makeText(app, "Audio engine changed. App will restart.", android.widget.Toast.LENGTH_SHORT).show()
        
        // Force kill the process so the service restarts completely
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            kotlin.system.exitProcess(0)
        }, 1000)
    }

    fun updateDsdOutputMode(mode: Int) {
        dsdOutputMode = mode
        UserPreferences.saveDsdOutputMode(getApplication(), mode)
    }

    fun updateDapBitPerfect(enabled: Boolean) {
        dapBitPerfect = enabled
        UserPreferences.saveDapBitPerfect(getApplication(), enabled)
    }

    fun updateUsbExclusive(enabled: Boolean) {
        usbExclusive = enabled
        UserPreferences.saveUsbExclusive(getApplication(), enabled)
    }

    fun updateFontRoundness(mode: Int) {
        fontRoundness = mode
        UserPreferences.saveFontRoundness(getApplication(), mode)
    }

    fun updatePlayImmediately(e: Boolean) {
        playImmediately = e
        UserPreferences.savePlayImmediately(getApplication(), e)
    }

    fun updateLyricsSource(source: Int) {
        lyricsSource = source
        UserPreferences.saveLyricsSource(getApplication(), source)
    }

    fun updateAmllPlatform(platform: String) {
        amllPlatform = platform
        UserPreferences.saveAmllPlatform(getApplication(), platform)
    }

    fun updateHideNavbarOnScroll(enabled: Boolean) {
        hideNavbarOnScroll = enabled
        UserPreferences.saveHideNavbarOnScroll(getApplication(), enabled)
    }

    fun updateWavyProgress(enabled: Boolean) {
        wavyProgress = enabled
        UserPreferences.saveWavyProgress(getApplication(), enabled)
    }

    fun updateRestoreLastQueue(enabled: Boolean) {
        restoreLastQueue = enabled
        UserPreferences.saveRestoreLastQueue(getApplication(), enabled)
        if (!enabled) {
            UserPreferences.clearLastQueue(getApplication())
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 清除 SharedPreferences 数据缓存
            CacheManager.clearAll(getApplication(), providerOnly = false)

            // 2. 清除 Media3 音频流磁盘缓存
            cp.player.service.MusicService.clearAudioCache(getApplication())

            // 3. 清除 StreamProxy 临时文件
            try {
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.listFiles()
                    ?.filter { it.name.startsWith("stream_cache_") }
                    ?.forEach { it.delete() }
            } catch (_: Exception) {}

            // 4. 清除 Coil 图片磁盘缓存
            try {
                val imageCacheDir = getApplication<Application>().cacheDir.resolve("image_cache")
                imageCacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
