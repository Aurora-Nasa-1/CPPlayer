package cp.player.engine

import android.util.Log
import cp.player.util.DebugLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

sealed class AudioEvent {
    /** @param path 引擎发送的路径（可能为空），用于过滤旧曲目的状态事件 */
    data class StateChanged(val state: String, val path: String? = null) : AudioEvent()
    data class Progress(
        val positionSecs: Double,
        val durationSecs: Double,
        val bufferLevel: Double
    ) : AudioEvent()
    data class TrackEnded(val path: String) : AudioEvent()
    data class CrossfadeStarted(val fromPath: String, val toPath: String) : AudioEvent()
    data class Error(val message: String) : AudioEvent()
    data class NextTrackReady(val path: String) : AudioEvent()
    data class FormatChanged(
        val sampleRate: Int,
        val bitrate: Int,
        val bitDepth: Int = 0,
        val channels: Int = 0,
        val codecName: String = ""
    ) : AudioEvent()
}

object RustEngine {
    private const val TAG = "RustEngine"
    private var isInitialized = false

    /**
     * 专用引擎线程调度器。
     * 所有 JNI 调用通过此调度器序列化执行，避免锁竞争和主线程阻塞。
     */
    val engineDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "RustEngine-JNI") }
            .asCoroutineDispatcher()

    private val _engineState = MutableStateFlow("Idle")
    val engineState: StateFlow<String> = _engineState.asStateFlow()

    private val _engineProgress = MutableStateFlow(AudioEvent.Progress(0.0, 0.0, 0.0))
    val engineProgress: StateFlow<AudioEvent.Progress> = _engineProgress.asStateFlow()

    private val _audioEvents = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 64)
    val audioEvents: SharedFlow<AudioEvent> = _audioEvents.asSharedFlow()

    private var pollJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        try {
            System.loadLibrary("rust_lib_flick_player")
            isInitialized = true
            Log.i(TAG, "Rust library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load rust library: ${e.message}")
        }
    }

    fun initEngine(context: android.content.Context): Boolean {
        DebugLog.i("RustEngine: initEngine() called. isInitialized: $isInitialized")
        if (!isInitialized) return false

        // Initialize Android Context for Rust
        val contextSuccess = nativeInitRustAndroidContext(context)
        DebugLog.i("RustEngine: nativeInitRustAndroidContext() returned: $contextSuccess")

        val success = nativeInit()
        DebugLog.i("RustEngine: nativeInit() returned: $success")
        if (success) {
            startPolling()
        }
        return success
    }

    fun play(path: String): Boolean {
        DebugLog.i("RustEngine: play() called with path: $path, isInitialized: $isInitialized")
        if (!isInitialized) {
            DebugLog.e("RustEngine: Cannot play, RustEngine is not initialized")
            return false
        }
        val result = nativePlay(path)
        DebugLog.i("RustEngine: nativePlay result: $result")
        return result
    }

    fun pause(): Boolean {
        if (!isInitialized) return false
        return nativePause()
    }

    fun resume(): Boolean {
        if (!isInitialized) return false
        return nativeResume()
    }

    fun stop(): Boolean {
        if (!isInitialized) return false
        return nativeStop()
    }

    fun seek(positionSecs: Double): Boolean {
        if (!isInitialized) return false
        return nativeSeek(positionSecs)
    }

    fun setVolume(volume: Float): Boolean {
        if (!isInitialized) return false
        return nativeSetVolume(volume)
    }

    fun setEqualizer(enabled: Boolean, freqs: FloatArray, gains: FloatArray, qs: FloatArray): Boolean {
        if (!isInitialized) return false
        return nativeSetEqualizer(enabled, freqs, gains, qs)
    }

    fun setFx(
        enabled: Boolean,
        balance: Float,
        tempo: Float,
        damp: Float,
        filterHz: Float,
        delayMs: Float,
        size: Float,
        mix: Float,
        feedback: Float,
        width: Float
    ): Boolean {
        if (!isInitialized) return false
        return nativeSetFx(
            enabled, balance, tempo, damp, filterHz, delayMs, size, mix, feedback, width
        )
    }

    fun getState(): String {
        if (!isInitialized) return "Uninitialized"
        return nativeGetState()
    }

    fun getProgress(): AudioEvent.Progress? {
        if (!isInitialized) return null
        val jsonStr = nativeGetProgress() ?: return null
        if (jsonStr == "null" || jsonStr == "" || jsonStr == "{}") return null
        try {
            val json = JSONObject(jsonStr)
            return AudioEvent.Progress(
                positionSecs = json.optDouble("position_secs", 0.0),
                durationSecs = json.optDouble("duration_secs", 0.0),
                bufferLevel = json.optDouble("buffer_level", 0.0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse progress JSON: $jsonStr", e)
        }
        return null
    }

    // ═══════════════════════════════════════════════
    // Suspend 版本 — 通过 engineDispatcher 序列化执行，不阻塞调用线程
    // ═══════════════════════════════════════════════

    /** suspend 版 [play]，在引擎专用线程执行 */
    suspend fun playAsync(path: String): Boolean = withContext(engineDispatcher) { play(path) }

    /** suspend 版 [pause]，在引擎专用线程执行 */
    suspend fun pauseAsync(): Boolean = withContext(engineDispatcher) { pause() }

    /** suspend 版 [resume]，在引擎专用线程执行 */
    suspend fun resumeAsync(): Boolean = withContext(engineDispatcher) { resume() }

    /** suspend 版 [stop]，在引擎专用线程执行 */
    suspend fun stopAsync(): Boolean = withContext(engineDispatcher) { stop() }

    /** suspend 版 [seek]，在引擎专用线程执行 */
    suspend fun seekAsync(positionSecs: Double): Boolean = withContext(engineDispatcher) { seek(positionSecs) }

    /** suspend 版 [setVolume]，在引擎专用线程执行 */
    suspend fun setVolumeAsync(volume: Float): Boolean = withContext(engineDispatcher) { setVolume(volume) }

    /** suspend 版 [stopEngine]，在引擎专用线程执行 */
    suspend fun stopEngineAsync() = withContext(engineDispatcher) { stopEngine() }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val eventStr = nativePollEvent()
                    if (eventStr != null && eventStr != "null") {
                        handleEventJson(eventStr)
                    } else {
                        // No event, delay a bit
                        delay(50)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in poll loop", e)
                    delay(1000)
                }
            }
        }
    }

    private fun handleEventJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type")
            when (type) {
                "StateChanged" -> {
                    val state = json.optString("state", "Idle")
                    val path = json.optString("path", "").ifEmpty { null }
                    _engineState.value = state
                    _audioEvents.tryEmit(AudioEvent.StateChanged(state, path))
                }
                "Progress" -> {
                    val progress = AudioEvent.Progress(
                        positionSecs = json.optDouble("position_secs", 0.0),
                        durationSecs = json.optDouble("duration_secs", 0.0),
                        bufferLevel = json.optDouble("buffer_level", 0.0)
                    )
                    _engineProgress.value = progress
                    _audioEvents.tryEmit(progress)
                }
                "TrackEnded" -> {
                    _audioEvents.tryEmit(AudioEvent.TrackEnded(json.optString("path")))
                }
                "CrossfadeStarted" -> {
                    _audioEvents.tryEmit(
                        AudioEvent.CrossfadeStarted(
                            fromPath = json.optString("from_path"),
                            toPath = json.optString("to_path")
                        )
                    )
                }
                "Error" -> {
                    _audioEvents.tryEmit(AudioEvent.Error(json.optString("message")))
                }
                "NextTrackReady" -> {
                    _audioEvents.tryEmit(AudioEvent.NextTrackReady(json.optString("path")))
                }
                "FormatChanged" -> {
                    val sampleRate = json.optInt("sample_rate", 0)
                    val bitrate = json.optInt("bitrate", 0)
                    val bitDepth = json.optInt("bit_depth", 0)
                    val channels = json.optInt("channels", 0)
                    val codecName = json.optString("codec_name", "")
                    _audioEvents.tryEmit(AudioEvent.FormatChanged(sampleRate, bitrate, bitDepth, channels, codecName))
                }
                else -> {
                    Log.w(TAG, "Unknown event type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event JSON: $jsonStr", e)
        }
    }

    fun stopEngine() {
        Log.i(TAG, "stopEngine: Stopping Rust polling and engine")
        pollJob?.cancel()
        pollJob = null
        val success = stop()
        Log.i(TAG, "stopEngine: nativeStop returned: $success")
    }

    // JNI external methods
    fun setRustDirectUsbPlaybackFormat(
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
        isDop: Boolean,
        isNativeDsd: Boolean
    ): Boolean {
        if (!isInitialized) return false
        return nativeSetRustDirectUsbPlaybackFormat(sampleRate, bitDepth, channels, isDop, isNativeDsd)
    }

    fun hasRustDirectUsbHardwareVolume(): Boolean {
        if (!isInitialized) return false
        return nativeHasRustDirectUsbHardwareVolume()
    }

    fun isRustDirectUsbSessionActive(): Boolean {
        if (!isInitialized) return false
        return nativeIsRustDirectUsbSessionActive()
    }

    fun getRustDirectUsbHardwareVolume(): Double {
        if (!isInitialized) return 0.0
        return nativeGetRustDirectUsbHardwareVolume()
    }

    fun setRustDirectUsbHardwareVolume(volume: Double): Boolean {
        if (!isInitialized) return false
        return nativeSetRustDirectUsbHardwareVolume(volume)
    }

    fun setRustDirectUsbHardwareMute(muted: Boolean): Boolean {
        if (!isInitialized) return false
        return nativeSetRustDirectUsbHardwareMute(muted)
    }

    private external fun nativeInit(): Boolean
    private external fun nativeInitRustAndroidContext(context: android.content.Context): Boolean
    private external fun nativePlay(path: String): Boolean
    private external fun nativePause(): Boolean
    private external fun nativeResume(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeSeek(secs: Double): Boolean
    private external fun nativeSetVolume(vol: Float): Boolean
    private external fun nativeSetEqualizer(enabled: Boolean, freqs: FloatArray, gains: FloatArray, qs: FloatArray): Boolean
    private external fun nativeSetFx(
        enabled: Boolean, balance: Float, tempo: Float, damp: Float,
        filterHz: Float, delayMs: Float, size: Float, mix: Float,
        feedback: Float, width: Float
    ): Boolean
    private external fun nativeGetState(): String
    private external fun nativeGetProgress(): String?
    private external fun nativePollEvent(): String?
    
    // USB Audio Direct JNI external methods
    private external fun nativeRegisterRustDirectUsbDevice(
        fd: Int,
        vendorId: Int,
        productId: Int,
        productName: String?,
        manufacturer: String?,
        serial: String?,
        deviceName: String?
    ): Boolean
    private external fun nativeSetRustDirectUsbPlaybackFormat(
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
        isDop: Boolean,
        isNativeDsd: Boolean
    ): Boolean
    private external fun nativeSetRustDirectUsbLockEnabled(enabled: Boolean): Boolean
    private external fun nativeHasRustDirectUsbHardwareVolume(): Boolean
    private external fun nativeGetRustDirectUsbHardwareVolume(): Double
    private external fun nativeSetRustDirectUsbHardwareVolume(volume: Double): Boolean
    private external fun nativeGetRustDirectUsbHardwareMute(): Int
    private external fun nativeSetRustDirectUsbHardwareMute(muted: Boolean): Boolean
    private external fun nativeVerifyRustDirectUsbHardwareVolumeHealth(): Int
    fun getRustAudioDebugStateJson(): String {
        if (!isInitialized) return "{}"
        return nativeGetRustAudioDebugStateJson()
    }

    private external fun nativeGetRustAudioDebugStateJson(): String
    private external fun nativeClearRustDirectUsbPlayback(): Boolean
    private external fun nativeWaitRustDirectUsbSessionStopped(timeoutMs: Int): Boolean
    private external fun nativeIsRustDirectUsbSessionActive(): Boolean
    private external fun nativeMarkRustDirectUsbFallback(reason: String?): Boolean
    
    fun registerDirectUsbDevice(
        fd: Int,
        vendorId: Int,
        productId: Int,
        productName: String?,
        manufacturer: String?,
        serial: String?,
        deviceName: String?
    ): Boolean {
        if (!isInitialized) return false
        return nativeRegisterRustDirectUsbDevice(
            fd, vendorId, productId, productName, manufacturer, serial, deviceName
        )
    }

    fun clearDirectUsbPlayback(): Boolean {
        if (!isInitialized) return false
        return nativeClearRustDirectUsbPlayback()
    }

    fun waitRustDirectUsbSessionStopped(timeoutMs: Int): Boolean {
        if (!isInitialized) return true
        return nativeWaitRustDirectUsbSessionStopped(timeoutMs)
    }

    // ═══════════════════════════════════════════════
    // DSD / DAP 设置 JNI
    // ═══════════════════════════════════════════════

    /** 设置 DSD 输出模式: 0=PCM, 1=DoP, 2=Native, 3=Auto */
    fun setDsdOutputMode(mode: Int): Boolean {
        if (!isInitialized) return false
        return nativeSetDsdOutputMode(mode)
    }

    /** 设置 DAP Bit-Perfect 模式 */
    fun setDapBitPerfectEnabled(enabled: Boolean): Boolean {
        if (!isInitialized) return false
        return nativeSetDapBitPerfectEnabled(enabled)
    }

    private external fun nativeSetDsdOutputMode(mode: Int): Boolean
    private external fun nativeSetDapBitPerfectEnabled(enabled: Boolean): Boolean
}
