package cp.player.util

import android.content.Context
import android.util.Log
import android.widget.Toast

object DebugLog {
    private const val TAG = "CPPlayerDebug"

    fun d(message: String) {
        Log.d(TAG, message)
        LogManager.log("D", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        LogManager.log("E", message, throwable)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        LogManager.log("I", message)
    }

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
