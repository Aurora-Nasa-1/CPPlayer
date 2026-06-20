package cp.player.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val time: String,
        val level: String,
        val message: String,
        val throwable: String? = null
    ) {
        override fun toString(): String {
            val base = "[$time] $level: $message"
            return if (throwable != null) "$base\n$throwable" else base
        }
    }

    fun log(level: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            level = level,
            message = message,
            throwable = throwable?.let { android.util.Log.getStackTraceString(it) }
        )
        _logs.update { current ->
            // 新日志插入头部，限制最大数量
            if (current.size >= 1000) {
                listOf(entry) + current.take(999)
            } else {
                listOf(entry) + current
            }
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsString(): String {
        return _logs.value.joinToString("\n") { it.toString() }
    }
}
