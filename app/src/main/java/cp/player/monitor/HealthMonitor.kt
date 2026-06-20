package cp.player.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

/**
 * API 调用健康监控单例。
 *
 * 覆盖范围：所有 API 调用通过 [cp.player.api.MusicApiServiceImpl.callApi] 统一拦截。
 *
 * ### 性能设计
 * - 使用 [MutableStateFlow.update] 避免每次记录都完整复制列表
 * - 统计查询从 Flow snapshot 计算，不阻塞记录路径
 * - 环形缓冲区用 ArrayList 预分配，避免频繁扩容
 */
object HealthMonitor {

    private const val MAX_RECORDS = 500

    // ======================== 数据模型 ========================

    enum class ResponseWarning {
        MISSING_CODE, UNEXPECTED_CODE, MISSING_DATA_FIELD,
        EMPTY_DATA_ARRAY, EMPTY_DATA_OBJECT, MALFORMED_RESPONSE,
        UNSUPPORTED_BY_PROVIDER, SLOW_RESPONSE
    }

    data class ApiCallRecord(
        val timestamp: Long,
        val providerId: String,
        val method: String,
        val durationMs: Long,
        val success: Boolean,
        val errorCode: Int? = null,
        val errorMessage: String? = null,
        val wasFallback: Boolean = false,
        val fallbackFrom: String? = null,
        val responseWarnings: List<ResponseWarning> = emptyList(),
        val responseCode: Int? = null,
        /** 期望的数据字段名（如 "data", "playlist"），仅 MISSING_DATA_FIELD 时有值 */
        val expectedField: String? = null
    )

    data class ProviderHealthStats(
        val providerId: String,
        val providerName: String,
        val totalCalls: Int,
        val successCount: Int,
        val failCount: Int,
        val avgResponseMs: Long,
        val p95ResponseMs: Long,
        val fallbackCount: Int,
        val lastError: String?,
        val lastErrorTime: Long,
        val healthScore: Float,
        val warningCount: Int,
        val lastWarning: ResponseWarning?
    )

    data class MethodStats(
        val method: String,
        val totalCalls: Int,
        val successCount: Int,
        val failCount: Int,
        val avgResponseMs: Long,
        val fallbackCount: Int,
        val warningCount: Int,
        val warningTypes: Map<ResponseWarning, Int>
    )

    // ======================== 状态 ========================
    // 使用 update {} 代替手动 toList()，减少每次记录的内存分配

    private val _recordsFlow = MutableStateFlow<List<ApiCallRecord>>(emptyList())
    val recordsFlow: StateFlow<List<ApiCallRecord>> = _recordsFlow.asStateFlow()

    // ======================== 记录 ========================

    /**
     * 记录一次 API 调用。使用 Flow update 避免完整列表复制。
     */
    fun recordCall(record: ApiCallRecord) {
        _recordsFlow.update { current ->
            val next = if (current.size >= MAX_RECORDS) {
                // 移除最旧的，添加新的
                ArrayList<ApiCallRecord>(MAX_RECORDS).apply {
                    addAll(current.subList(1, current.size))
                    add(record)
                }
            } else {
                ArrayList<ApiCallRecord>(current.size + 1).apply {
                    addAll(current)
                    add(record)
                }
            }
            next
        }
    }

    fun clearRecords() {
        _recordsFlow.value = emptyList()
    }

    // ======================== 查询（从 Flow snapshot 计算，不加锁） ========================

    fun getStats(providerId: String, providerName: String = providerId): ProviderHealthStats {
        return buildStats(providerId, providerName, _recordsFlow.value.filter { it.providerId == providerId })
    }

    fun getAllStats(): Map<String, ProviderHealthStats> {
        val snapshot = _recordsFlow.value
        val providerIds = snapshot.asSequence().map { it.providerId }.distinct().toList()
        return providerIds.associateWith { id ->
            buildStats(id, id, snapshot.filter { it.providerId == id })
        }
    }

    fun getRecentRecords(
        limit: Int = 100,
        providerId: String? = null,
        onlyFailures: Boolean = false,
        onlyWarnings: Boolean = false
    ): List<ApiCallRecord> {
        val snapshot = _recordsFlow.value
        // 从末尾遍历（最新的在后），收集满足条件的记录
        val result = mutableListOf<ApiCallRecord>()
        for (i in snapshot.indices.reversed()) {
            if (result.size >= limit) break
            val r = snapshot[i]
            if (providerId != null && r.providerId != providerId) continue
            if (onlyFailures && r.success) continue
            if (onlyWarnings && r.responseWarnings.isEmpty()) continue
            result.add(r)
        }
        return result
    }

    fun getStatsByMethod(): Map<String, MethodStats> {
        val snapshot = _recordsFlow.value
        return snapshot.groupBy { it.method }.mapValues { (method, list) ->
            var successCount = 0
            var failCount = 0
            var totalDuration = 0L
            var fallbackCount = 0
            var warningCount = 0
            val warningTypeMap = mutableMapOf<ResponseWarning, Int>()

            for (r in list) {
                if (r.success) successCount++ else failCount++
                totalDuration += r.durationMs
                if (r.wasFallback) fallbackCount++
                if (r.responseWarnings.isNotEmpty()) {
                    warningCount++
                    for (w in r.responseWarnings) {
                        warningTypeMap[w] = (warningTypeMap[w] ?: 0) + 1
                    }
                }
            }

            MethodStats(
                method = method,
                totalCalls = list.size,
                successCount = successCount,
                failCount = failCount,
                avgResponseMs = if (list.isNotEmpty()) totalDuration / list.size else 0L,
                fallbackCount = fallbackCount,
                warningCount = warningCount,
                warningTypes = warningTypeMap
            )
        }
    }

    // ======================== 内部 ========================

    private fun buildStats(providerId: String, providerName: String, list: List<ApiCallRecord>): ProviderHealthStats {
        val total = list.size
        var success = 0
        var totalDuration = 0L
        var fallbacks = 0
        var warningCount = 0
        var lastFail: ApiCallRecord? = null
        var lastWarningRecord: ApiCallRecord? = null

        for (r in list) {
            if (r.success) success++
            totalDuration += r.durationMs
            if (r.wasFallback) fallbacks++
            if (r.responseWarnings.isNotEmpty()) {
                warningCount++
                lastWarningRecord = r
            }
            if (!r.success) lastFail = r
        }

        val fail = total - success
        val avgMs = if (total > 0) totalDuration / total else 0L
        val p95Ms = if (total > 0) {
            val sorted = list.asSequence().map { it.durationMs }.sorted().toList()
            sorted[(total * 0.95f).toInt().coerceIn(0, total - 1)]
        } else 0L

        val successRate = if (total > 0) success.toFloat() / total else 1f
        val responseScore = if (avgMs <= 0) 1f else max(0f, min(1f, 1f - (avgMs - 500f) / 4500f))
        val cleanRate = if (total > 0) (total - warningCount).toFloat() / total else 1f
        val score = successRate * 0.6f + responseScore * 0.2f + cleanRate * 0.2f

        return ProviderHealthStats(
            providerId = providerId,
            providerName = providerName,
            totalCalls = total,
            successCount = success,
            failCount = fail,
            avgResponseMs = avgMs,
            p95ResponseMs = p95Ms,
            fallbackCount = fallbacks,
            lastError = lastFail?.errorMessage,
            lastErrorTime = lastFail?.timestamp ?: 0L,
            healthScore = score,
            warningCount = warningCount,
            lastWarning = lastWarningRecord?.responseWarnings?.firstOrNull()
        )
    }
}
