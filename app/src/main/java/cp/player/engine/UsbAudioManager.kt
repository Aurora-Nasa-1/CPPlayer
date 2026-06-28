package cp.player.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import cp.player.util.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsbAudioManager(private val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    interface OnDeviceChangedListener {
        fun onDeviceDetached(device: UsbDevice)
        fun onDeviceAttached(device: UsbDevice)
    }

    var listener: OnDeviceChangedListener? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var registeredDevice: UsbDevice? = null
    private var currentConnection: android.hardware.usb.UsbDeviceConnection? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var periodicScanJob: Job? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "cp.player.USB_PERMISSION"
        private const val TAG = "UsbAudioManager"
        /** 周期性扫描间隔（ms），作为广播接收器的兜底 */
        private const val PERIODIC_SCAN_INTERVAL_MS = 3000L
        /** 周期性扫描最大持续时间（ms），避免无限轮询 */
        private const val PERIODIC_SCAN_MAX_DURATION_MS = 30000L
    }

    val isDeviceRegistered: Boolean
        get() = registeredDevice != null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { checkAndRequestPermission(it) }
                        } else {
                            Log.d(TAG, "permission denied for device $device")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { checkAndRequestPermission(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device == registeredDevice) {
                        Log.i(TAG, "USB Device Detached: ${device?.productName}")
                        device?.let { listener?.onDeviceDetached(it) }
                        unregisterDevice()
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 必须使用 RECEIVER_EXPORTED：USB 系统广播 (ACTION_USB_DEVICE_ATTACHED/DETACHED)
            // 由系统发送，RECEIVER_NOT_EXPORTED 会阻止接收这些广播
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        UserPreferences.getPrefs(context).registerOnSharedPreferenceChangeListener(this)
    }

    /**
     * 注册通过 Manifest intent-filter 暂存的 USB 设备。
     * 该设备可能已不在 deviceList 中（用户可能已拔出），但仍尝试注册。
     */
    fun registerPendingDevice(device: UsbDevice) {
        if (!UserPreferences.getUsbExclusive(context)) return
        if (UserPreferences.getAudioEngine(context) != 1) return
        Log.i(TAG, "registerPendingDevice: ${device.productName} (vid=${device.vendorId}, pid=${device.productId})")
        checkAndRequestPermission(device)
    }

    /**
     * 扫描已连接的 USB 音频设备并尝试注册。
     * 必须在 Rust 引擎 (RustEngine.initEngine) 初始化完成后调用。
     */
    fun scanForUsbAudioDevices() {
        if (!UserPreferences.getUsbExclusive(context)) return
        if (UserPreferences.getAudioEngine(context) != 1) return

        val deviceList = usbManager.deviceList
        Log.i(TAG, "scanForUsbAudioDevices: checking ${deviceList.size} USB devices")
        for (device in deviceList.values) {
            checkAndRequestPermission(device)
        }
    }

    /**
     * 启动周期性扫描，作为广播接收器的兜底机制。
     *
     * 解决的问题：
     * 1. 应用启动时 USB 设备可能尚未被系统枚举（deviceList 为空）
     * 2. Android 13+ RECEIVER_NOT_EXPORTED 可能阻止某些广播传递
     * 3. Rust 引擎 nativeInit() 可能晚于首次扫描完成
     *
     * 每 3 秒扫描一次，最多持续 30 秒，设备注册成功后自动停止。
     */
    fun startPeriodicScan() {
        periodicScanJob?.cancel()
        if (!UserPreferences.getUsbExclusive(context)) return
        if (UserPreferences.getAudioEngine(context) != 1) return

        periodicScanJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive && registeredDevice == null) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= PERIODIC_SCAN_MAX_DURATION_MS) {
                    Log.w(TAG, "Periodic scan timed out after ${PERIODIC_SCAN_MAX_DURATION_MS}ms")
                    break
                }

                scanForUsbAudioDevices()

                if (registeredDevice != null) {
                    Log.i(TAG, "Periodic scan: device registered, stopping")
                    break
                }
                delay(PERIODIC_SCAN_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        periodicScanJob?.cancel()
        periodicScanJob = null
        scope.cancel()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        UserPreferences.getPrefs(context).unregisterOnSharedPreferenceChangeListener(this)
        unregisterDevice()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "usb_exclusive" || key == "audio_engine") {
            if (UserPreferences.getUsbExclusive(context) && UserPreferences.getAudioEngine(context) == 1) {
                startPeriodicScan()
            } else {
                periodicScanJob?.cancel()
                periodicScanJob = null
                unregisterDevice()
            }
        }
    }

    /**
     * 判断设备是否为 USB 音频设备。
     * 同时检查接口级别（Audio Streaming）和设备级别（Audio Class），
     * 提高对不同 USB DAC 的兼容性。
     */
    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        // 检查设备级别描述符
        if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true

        // 检查接口级别描述符
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO) return true
        }
        return false
    }

    private fun checkAndRequestPermission(device: UsbDevice) {
        if (!UserPreferences.getUsbExclusive(context)) return
        if (UserPreferences.getAudioEngine(context) != 1) return
        if (!isUsbAudioDevice(device)) return

        if (usbManager.hasPermission(device)) {
            registerDevice(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                }, flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun registerDevice(device: UsbDevice) {
        if (registeredDevice == device) return
        if (!UserPreferences.getUsbExclusive(context) || UserPreferences.getAudioEngine(context) != 1) return

        scope.launch(Dispatchers.IO) {
            try {
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    // Claim and immediately release all audio interfaces to forcefully detach the Android kernel audio driver (ALSA).
                    // This is the "Kernel Detach Hack". It leaves the interface completely free (no kernel driver, no Java claim)
                    // so that Rust's libusb can claim it cleanly without hitting EBUSY!
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        if (intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                            val claimed = connection.claimInterface(intf, true)
                            if (claimed) {
                                connection.releaseInterface(intf)
                                Log.d(TAG, "Detached kernel driver and released interface ${i} (Audio)")
                            }
                        }
                    }

                    val fd = connection.fileDescriptor
                    if (fd >= 0) {
                        val success = RustEngine.registerDirectUsbDevice(
                            fd = fd,
                            vendorId = device.vendorId,
                            productId = device.productId,
                            productName = device.productName,
                            manufacturer = device.manufacturerName,
                            serial = device.serialNumber,
                            deviceName = device.deviceName
                        )
                        if (success) {
                            registeredDevice = device
                            currentConnection = connection
                            Log.i(TAG, "Successfully registered USB audio device: ${device.productName}")

                            // The Rust UAC2 engine REQUIRES a base playback format to be configured before it will start.
                            // We set a high-quality default (192kHz, 32-bit, Stereo).
                            // The Rust engine's automatic negotiation will adapt the sample rate to match the playing file's
                            // native sample rate for Bit-Perfect playback, but it needs this initial configuration to know the target bit depth!
                            RustEngine.setRustDirectUsbPlaybackFormat(
                                sampleRate = 192000,
                                bitDepth = 32,
                                channels = 2,
                                isDop = false,
                                isNativeDsd = false
                            )

                            // Initialize Hardware Volume if present (set to 100% by default, or unmute)
                            scope.launch(Dispatchers.IO) {
                                kotlinx.coroutines.delay(500)
                                val hasVolume = RustEngine.hasRustDirectUsbHardwareVolume()
                                if (hasVolume) {
                                    RustEngine.setRustDirectUsbHardwareVolume(1.0)
                                }
                                RustEngine.setRustDirectUsbHardwareMute(false)
                            }
                        } else {
                            Log.e(TAG, "Failed to register USB audio device in RustEngine")
                            connection.close()
                        }
                    } else {
                        Log.e(TAG, "Invalid file descriptor for USB device")
                        connection.close()
                    }
                } else {
                    Log.e(TAG, "Failed to open USB device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering USB device", e)
            }
        }
    }

    private fun unregisterDevice() {
        if (registeredDevice != null) {
            Log.i(TAG, "unregisterDevice: Clearing Rust direct USB playback and closing connection")
            RustEngine.clearDirectUsbPlayback()

            // Wait for session to stop to avoid resource busy on immediate re-open
            val stopped = RustEngine.waitRustDirectUsbSessionStopped(2000)
            Log.i(TAG, "unregisterDevice: Rust session stopped: $stopped")

            currentConnection?.close()
            currentConnection = null
            registeredDevice = null
            Log.i(TAG, "Unregistered USB audio device")
        }
    }
}
