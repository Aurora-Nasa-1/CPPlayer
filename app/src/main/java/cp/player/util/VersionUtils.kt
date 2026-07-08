package cp.player.util

private val PRE_RELEASE_REGEX = Regex("^([a-zA-Z]*)(\\d*)$")

object VersionUtils {
    /**
     * 比较两个语义化版本号（支持 pre-release 后缀，如 `1.0.0-beta3`）。
     *
     * 规则：
     * 1. 先去掉 `v` 前缀，再按 `.` 分段比较数字部分
     * 2. 数字部分相同时，有 pre-release 后缀的版本 **小于** 没有后缀的（如 `1.0.0-beta1` < `1.0.0`）
     * 3. 都有后缀时，提取共同前缀字母后的数字部分比较（`beta2` < `beta3` < `beta123`）
     *
     * @return 负数 = v1 < v2，0 = 相等，正数 = v1 > v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        // 分离数字部分和 pre-release 后缀，同时去掉 v 前缀
        fun parseVersion(version: String): Pair<List<Int>, String?> {
            val v = version.removePrefix("v")
            val dashIndex = v.indexOf('-')
            val numericPart = if (dashIndex >= 0) v.substring(0, dashIndex) else v
            val preRelease = if (dashIndex >= 0) v.substring(dashIndex + 1) else null
            val parts = numericPart.split(".").map { it.toIntOrNull() ?: 0 }
            return parts to preRelease
        }

        // 比较 pre-release 后缀，支持数字部分按数值比较（如 beta2 < beta123）
        fun comparePreRelease(pre1: String, pre2: String): Int {
            // 提取共同字母前缀后的数字部分
            val match1 = PRE_RELEASE_REGEX.matchEntire(pre1)
            val match2 = PRE_RELEASE_REGEX.matchEntire(pre2)

            if (match1 != null && match2 != null) {
                val prefix1 = match1.groupValues[1]
                val prefix2 = match2.groupValues[1]
                // 先比较字母前缀
                val prefixCmp = prefix1.compareTo(prefix2)
                if (prefixCmp != 0) return prefixCmp
                // 字母前缀相同，按数值比较数字部分
                val num1 = match1.groupValues[2].toIntOrNull() ?: 0
                val num2 = match2.groupValues[2].toIntOrNull() ?: 0
                return num1 - num2
            }
            // 无法解析时回退到字符串比较
            return pre1.compareTo(pre2)
        }

        val (parts1, pre1) = parseVersion(v1)
        val (parts2, pre2) = parseVersion(v2)

        // 比较数字部分
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }

        // 数字部分相同，比较 pre-release 后缀
        // 有 pre-release < 无 pre-release（如 1.0.0-beta1 < 1.0.0）
        return when {
            pre1 != null && pre2 != null -> comparePreRelease(pre1, pre2)
            pre1 != null && pre2 == null -> -1
            pre1 == null && pre2 != null -> 1
            else -> 0
        }
    }
}
