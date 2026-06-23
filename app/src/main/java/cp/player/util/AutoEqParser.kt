package cp.player.util

import android.net.Uri
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

data class PeqBand(
    val freq: Float,
    val gain: Float,
    val q: Float
)

object AutoEqParser {
    private const val TAG = "AutoEqParser"

    // Parses a standard AutoEQ txt file.
    // Handles GraphicEQ and ParametricEQ formats.
    fun parse(context: Context, uri: Uri): List<PeqBand>? {
        try {
            val bands = mutableListOf<PeqBand>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var isGraphicEq = false
                    reader.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) return@forEachLine
                        if (trimmed.startsWith("GraphicEQ:")) {
                            isGraphicEq = true
                            bands.addAll(parseGraphicEq(trimmed))
                        } else if (trimmed.startsWith("Filter") && trimmed.contains("PK")) {
                            parseParametricEqLine(trimmed)?.let { bands.add(it) }
                        }
                    }
                }
            }
            return if (bands.isNotEmpty()) bands else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AutoEQ file", e)
            return null
        }
    }

    private fun parseGraphicEq(line: String): List<PeqBand> {
        val bands = mutableListOf<PeqBand>()
        val dataStr = line.substringAfter("GraphicEQ:").trim()
        val pairs = dataStr.split(";")
        for (pair in pairs) {
            val parts = pair.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                try {
                    val freq = parts[0].toFloat()
                    val gain = parts[1].toFloat()
                    // GraphicEQ essentially has fixed Q depending on band spacing,
                    // but we'll default to 1.41 (sqrt(2)) which is a common 1-octave Q,
                    // or maybe 1.0. Let's use 1.41.
                    bands.add(PeqBand(freq, gain, 1.41f))
                } catch (e: Exception) {
                    // ignore format errors for single bands
                }
            }
        }
        return bands
    }

    private fun parseParametricEqLine(line: String): PeqBand? {
        // Example: Filter 1: ON PK Fc 32 Hz Gain 1.5 dB Q 0.8
        try {
            val parts = line.split("\\s+".toRegex())
            val fcIndex = parts.indexOf("Fc")
            val gainIndex = parts.indexOf("Gain")
            val qIndex = parts.indexOf("Q")

            if (fcIndex != -1 && gainIndex != -1 && qIndex != -1 &&
                fcIndex + 1 < parts.size && gainIndex + 1 < parts.size && qIndex + 1 < parts.size) {

                val freq = parts[fcIndex + 1].toFloat()
                val gain = parts[gainIndex + 1].toFloat()
                val q = parts[qIndex + 1].toFloat()

                return PeqBand(freq, gain, q)
            }
        } catch (e: Exception) {
            // ignore malformed lines
        }
        return null
    }
}
