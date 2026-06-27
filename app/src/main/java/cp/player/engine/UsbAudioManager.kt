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
import kotlinx.coroutines.cancel
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

    companion object {
        private const val ACTION_USB_PERMISSION = "cp.player.USB_PERMISSION"
        private const val TAG = "UsbAudioManager"
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
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        UserPreferences.getPrefs(context).registerOnSharedPreferenceChangeListener(this)

        scanForUsbAudioDevices()
    }

    fun stop() {
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
                scanForUsbAudioDevices()
            } else {
                unregisterDevice()
            }
        }
    }

    private fun scanForUsbAudioDevices() {
        if (!UserPreferences.getUsbExclusive(context)) return

        if (UserPreferences.getAudioEngine(context) != 1) return

        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            checkAndRequestPermission(device)
        }
    }

    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            // Interface Class 1 (Audio), Subclass 2 (Audio Streaming)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO && 
                intf.interfaceSubclass == 2) {
                return true
            }
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
