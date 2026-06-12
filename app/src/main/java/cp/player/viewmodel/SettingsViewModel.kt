package cp.player.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import cp.player.util.UserPreferences

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
    var useWavyProgress by mutableStateOf(UserPreferences.getUseWavyProgress(application))
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

    fun updateUseWavyProgress(e: Boolean) {
        useWavyProgress = e
        UserPreferences.saveUseWavyProgress(getApplication(), e)
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

    fun clearCache() { /* API */ }
}
