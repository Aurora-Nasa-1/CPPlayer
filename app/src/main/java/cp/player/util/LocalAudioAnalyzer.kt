package cp.player.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import cp.player.viewmodel.AudioFeatures
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object LocalAudioAnalyzer {


    fun analyze(context: Context, uri: Uri): AudioFeatures {
        return try {
            val samples = decodeAudio(context, uri, maxDurationSec = 40)
            if (samples.isEmpty()) return fallback()

            val sampleRate = 44100 // Approximation since we resampled/mixed blindly
            
            // We need:
            // 1. Overall BPM, Energy, Brightness (from first 30s)
            // 2. Start BPM, Energy (first 15s)
            // 3. End BPM, Energy (last 15s)

            val totalSamples = samples.size
            val mainLen = min(totalSamples, sampleRate * 30)
            val startLen = min(totalSamples, sampleRate * 15)
            val endLen = min(totalSamples, sampleRate * 15)

            val yMain = samples.sliceArray(0 until mainLen)
            val yStart = samples.sliceArray(0 until startLen)
            val yEnd = if (totalSamples > endLen) samples.sliceArray(totalSamples - endLen until totalSamples) else samples

            val bpm = estimateBpm(yMain, sampleRate)
            val energy = computeEnergy(yMain)
            val brightness = computeBrightness(yMain, sampleRate)

            val startBpm = estimateBpm(yStart, sampleRate)
            val startEnergy = computeEnergy(yStart)

            val endBpm = estimateBpm(yEnd, sampleRate)
            val endEnergy = computeEnergy(yEnd)

            AudioFeatures(
                bpm = bpm.takeIf { it > 0 } ?: 120.0,
                energy = energy.takeIf { it > 0 } ?: 0.5,
                brightness = brightness.takeIf { it > 0 } ?: 0.5,
                startBpm = startBpm.takeIf { it > 0 } ?: bpm.takeIf { it > 0 } ?: 120.0,
                endBpm = endBpm.takeIf { it > 0 } ?: bpm.takeIf { it > 0 } ?: 120.0,
                startEnergy = startEnergy.takeIf { it > 0 } ?: energy.takeIf { it > 0 } ?: 0.5,
                endEnergy = endEnergy.takeIf { it > 0 } ?: energy.takeIf { it > 0 } ?: 0.5
            )
        } catch (e: Exception) {
            e.printStackTrace()
            fallback()
        }
    }

    private fun fallback() = AudioFeatures(120.0, 0.5, 0.5, 120.0, 120.0, 0.5, 0.5)

    private fun decodeAudio(context: Context, uri: Uri, maxDurationSec: Int): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return FloatArray(0)

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            // Limit decoding to maxDurationSec + some tail if we wanted it, but let's just decode the whole file up to maxDurationSec?
            // Actually, we need the END of the file! So we can't just decode the first 40s.
            // If the file is long, we should decode the first 30s and the last 15s.
            // This requires seeking!
            
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val durationSec = durationUs / 1_000_000L

            val resultSamples = FloatArrayList()

            // Helper to decode a segment
            fun decodeSegment(startUs: Long, lengthUs: Long): FloatArray {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var isEOS = false
                val segmentSamples = FloatArrayList()
                val info = MediaCodec.BufferInfo()
                var decodedUs = 0L

                while (!isEOS && decodedUs < lengthUs) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)
                        if (buffer != null) {
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val time = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
                                extractor.advance()
                            }
                        }
                    }

                    var outputIndex = codec.dequeueOutputBuffer(info, 10000)
                    while (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            // Extract 16-bit PCM mono
                            val shortBuffer = outputBuffer.asShortBuffer()
                            val numSamples = info.size / 2 / channels
                            for (i in 0 until numSamples) {
                                var sum = 0f
                                for (ch in 0 until channels) {
                                    sum += shortBuffer.get(i * channels + ch).toFloat() / 32768f
                                }
                                segmentSamples.add(sum / channels)
                            }
                            decodedUs += info.presentationTimeUs - startUs
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEOS = true
                        }
                        outputIndex = codec.dequeueOutputBuffer(info, 10000)
                    }
                }
                return segmentSamples.toFloatArray()
            }

            // Extract first 30s
            val firstSegmentUs = min(durationUs, 30_000_000L)
            val firstSamples = decodeSegment(0L, firstSegmentUs)
            resultSamples.addAll(firstSamples)

            // If duration > 45s, we skip the middle and seek to the end 15s to save time.
            if (durationUs > 45_000_000L) {
                // To keep the math simple for our downstream logic which expects contiguous arrays,
                // we can just append the tail. The `analyze` function slices it using `samples.size - endLen`.
                codec.flush() // flush codec before seek
                val tailStartUs = durationUs - 15_000_000L
                val tailSamples = decodeSegment(tailStartUs, 15_000_000L)
                resultSamples.addAll(tailSamples)
            }

            codec.stop()
            codec.release()

            return resultSamples.toFloatArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return FloatArray(0)
        } finally {
            extractor.release()
        }
    }

    private fun computeEnergy(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSq = 0.0
        for (v in samples) {
            sumSq += (v * v).toDouble()
        }
        return sqrt(sumSq / samples.size)
    }

    private fun computeBrightness(samples: FloatArray, sampleRate: Int): Double {
        if (samples.size < 512) return 0.0
        val frameSize = 2048
        val hopSize = 1024
        var weighted = 0.0
        var sumWeight = 0.0
        var start = 0
        while (start + frameSize < samples.size) {
            var last = samples[start]
            var diffAcc = 0f
            var absAcc = abs(last)
            for (i in 1 until frameSize) {
                val cur = samples[start + i]
                diffAcc += abs(cur - last)
                absAcc += abs(cur)
                last = cur
            }
            val norm = if (absAcc > 1e-9f) diffAcc / absAcc else 0f
            weighted += norm
            sumWeight += 1.0
            start += hopSize
        }
        val normalized = if (sumWeight > 0) weighted / sumWeight else 0.0
        return max(0.0, min(6000.0, normalized * sampleRate * 0.14))
    }

    private fun estimateBpm(samples: FloatArray, sampleRate: Int): Double {
        val frameSize = 1024
        val hopSize = 512
        if (samples.size < frameSize * 8) return 0.0
        
        val env = FloatArrayList()
        var start = 0
        while (start + frameSize < samples.size) {
            var sum = 0f
            for (i in 0 until frameSize) {
                sum += abs(samples[start + i])
            }
            env.add(sum / frameSize)
            start += hopSize
        }
        if (env.size < 32) return 0.0

        var sumAcc = 0f
        for (i in 0 until env.size) sumAcc += env.data[i]
        val mean = sumAcc / env.size
        var varianceAcc = 0f
        for (i in 0 until env.size) {
            val e = max(0f, env.data[i] - mean)
            env.data[i] = e
            varianceAcc += e * e
        }
        val variance = varianceAcc / max(1, env.size)
        if (variance < 1e-6f) return 0.0

        val envRate = sampleRate.toFloat() / hopSize
        val minLag = ((60 * envRate) / 180).toInt()
        val maxLag = ((60 * envRate) / 70).toInt()

        var bestLag = 0
        var bestScore = -Float.MAX_VALUE
        for (lag in max(1, minLag)..max(minLag + 1, maxLag)) {
            var score = 0f
            for (i in 0 until env.size - lag) {
                score += env.data[i] * env.data[i + lag]
            }
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag == 0) return 0.0
        val bpm = (60f * envRate) / bestLag
        if (bpm.isNaN() || bpm.isInfinite()) return 0.0
        return max(55.0, min(210.0, bpm.toDouble()))
    }
}

/**
 * A simple primitive array list to avoid Float boxing overhead when collecting audio samples.
 */
private class FloatArrayList(initialCapacity: Int = 1024) {
    var data = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(element: Float) {
        if (size == data.size) {
            val newData = FloatArray(data.size * 2)
            data.copyInto(newData, 0, 0, size)
            data = newData
        }
        data[size++] = element
    }

    fun addAll(elements: FloatArray) {
        if (size + elements.size > data.size) {
            var newCap = data.size * 2
            while (newCap < size + elements.size) {
                newCap *= 2
            }
            val newData = FloatArray(newCap)
            data.copyInto(newData, 0, 0, size)
            data = newData
        }
        elements.copyInto(data, size, 0, elements.size)
        size += elements.size
    }

    fun addAll(elements: FloatArrayList) {
        if (size + elements.size > data.size) {
            var newCap = data.size * 2
            while (newCap < size + elements.size) {
                newCap *= 2
            }
            val newData = FloatArray(newCap)
            data.copyInto(newData, 0, 0, size)
            data = newData
        }
        elements.data.copyInto(data, size, 0, elements.size)
        size += elements.size
    }

    fun get(index: Int): Float {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException()
        return data[index]
    }

    fun set(index: Int, element: Float) {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException()
        data[index] = element
    }

    fun sum(): Float {
        var s = 0f
        for (i in 0 until size) {
            s += data[i]
        }
        return s
    }

    fun toFloatArray(): FloatArray {
        val result = FloatArray(size)
        data.copyInto(result, 0, 0, size)
        return result
    }
}
