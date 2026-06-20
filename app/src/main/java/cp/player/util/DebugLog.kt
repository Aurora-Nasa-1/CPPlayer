package cp.player.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import cp.player.BuildConfig

/**
 * 统一调试日志工具。
 *
 * - Debug 构建：输出到 logcat + LogManager 内存缓冲区
 * - Release 构建：仅 LogManager（供"复制日志"功能使用），不输出 logcat
 */
object DebugLog {
    private const val TAG = "CPPlayerDebug"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        LogManager.log("D", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable) // 错误日志始终输出
        LogManager.log("E", message, throwable)
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
        LogManager.log("I", message)
    }

    fun w(message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
        LogManager.log("W", message)
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
