package cp.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cp.player.model.Song
import cp.player.provider.ProviderManager
import cp.player.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AudioFeatures(
    val bpm: Double,
    val energy: Double,
    val brightness: Double,
    val startBpm: Double = bpm,
    val endBpm: Double = bpm,
    val startEnergy: Double = energy,
    val endEnergy: Double = energy
)

data class SongWithEmotion(
    val song: Song,
    val path: String,
    val bpm: Double,
    val energy: Double,
    val brightness: Double,
    val startBpm: Double = bpm,
    val endBpm: Double = bpm,
    val startEnergy: Double = energy,
    val endEnergy: Double = energy,
    val emotionScore: Double = 0.0
)

sealed class LiveSortState {
    object Idle : LiveSortState()
    data class Analyzing(val progress: Int, val total: Int, val currentSong: String) : LiveSortState()
    object Sorting : LiveSortState()
    data class Completed(
        val sortedSongs: List<SongWithEmotion>,
        val actualCurve: List<Double>,
        val idealCurve: List<Double>
    ) : LiveSortState()
    data class Error(val message: String) : LiveSortState()
}

class LiveSortViewModel(application: Application) : AndroidViewModel(application) {
    private val _sortState = MutableStateFlow<LiveSortState>(LiveSortState.Idle)
    val sortState: StateFlow<LiveSortState> = _sortState.asStateFlow()

    private val featuresCache = mutableMapOf<String, AudioFeatures>()

    init {
        loadCache()
    }

    private fun loadCache() {
        try {
            val json = UserPreferences.getAudioFeatures(getApplication())
            if (json != null) {
                val obj = JSONObject(json)
                obj.keys().forEach { id ->
                    val feat = obj.getJSONObject(id)
                    val bpm = feat.getDouble("bpm")
                    val energy = feat.getDouble("energy")
                    featuresCache[id] = AudioFeatures(
                        bpm = bpm,
                        energy = energy,
                        brightness = feat.getDouble("brightness"),
                        startBpm = feat.optDouble("start_bpm", bpm),
                        endBpm = feat.optDouble("end_bpm", bpm),
                        startEnergy = feat.optDouble("start_energy", energy),
                        endEnergy = feat.optDouble("end_energy", energy)
                    )
                }
            }
        } catch (e: Exception) {}
    }

    private fun saveCache() {
        try {
            val obj = JSONObject()
            featuresCache.forEach { (id, feat) ->
                val fObj = JSONObject()
                fObj.put("bpm", feat.bpm)
                fObj.put("energy", feat.energy)
                fObj.put("brightness", feat.brightness)
                fObj.put("start_bpm", feat.startBpm)
                fObj.put("end_bpm", feat.endBpm)
                fObj.put("start_energy", feat.startEnergy)
                fObj.put("end_energy", feat.endEnergy)
                obj.put(id, fObj)
            }
            UserPreferences.saveAudioFeatures(getApplication(), obj.toString())
        } catch (e: Exception) {}
    }

    fun reset() {
        _sortState.value = LiveSortState.Idle
    }

    fun processPlaylist(songsWithPaths: List<Pair<Song, String>>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val total = songsWithPaths.size
                if (total == 0) {
                    _sortState.value = LiveSortState.Completed(emptyList(), emptyList(), emptyList())
                    return@launch
                }

                val analyzedSongs = mutableListOf<SongWithEmotion>()

                for ((index, item) in songsWithPaths.withIndex()) {
                    val (song, path) = item
                    _sortState.value = LiveSortState.Analyzing(index + 1, total, song.name)

                    val features = featuresCache[song.id] ?: run {
                        val uri = android.net.Uri.parse(path)
                        val f = cp.player.util.LocalAudioAnalyzer.analyze(getApplication(), uri)
                        featuresCache[song.id] = f
                        f
                    }

                    analyzedSongs.add(
                        SongWithEmotion(
                            song = song,
                            path = path,
                            bpm = features.bpm,
                            energy = features.energy,
                            brightness = features.brightness,
                            startBpm = features.startBpm,
                            endBpm = features.endBpm,
                            startEnergy = features.startEnergy,
                            endEnergy = features.endEnergy
                        )
                    )
                }

                saveCache()
                _sortState.value = LiveSortState.Sorting

                val scoredSongs = computeEmotionScores(analyzedSongs)
                val sortedSongs = sortSongsLocallyByFlow(scoredSongs)
                
                val actualCurve = sortedSongs.map { it.emotionScore }
                val idealCurve = generateIdealCurveLocal(sortedSongs.size)

                _sortState.value = LiveSortState.Completed(sortedSongs, actualCurve, idealCurve)

            } catch (e: Exception) {
                _sortState.value = LiveSortState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reorderCompletedList(fromIndex: Int, toIndex: Int) {
        val currentState = _sortState.value as? LiveSortState.Completed ?: return
        val currentSongs = currentState.sortedSongs.toMutableList()
        val movedSong = currentSongs.removeAt(fromIndex)
        currentSongs.add(toIndex, movedSong)
        
        val actualCurve = currentSongs.map { it.emotionScore }
        val idealCurve = generateIdealCurveLocal(currentSongs.size)
        _sortState.value = LiveSortState.Completed(currentSongs, actualCurve, idealCurve)
    }

    private fun parseAudioFeatures(jsonString: String): AudioFeatures {
        return try {
            val jsonObject = JSONObject(jsonString)
            val bpm = jsonObject.optDouble("bpm", 120.0)
            val energy = jsonObject.optDouble("energy", 0.5)
            AudioFeatures(
                bpm = bpm,
                energy = energy,
                brightness = jsonObject.optDouble("brightness", 0.5),
                startBpm = jsonObject.optDouble("start_bpm", bpm),
                endBpm = jsonObject.optDouble("end_bpm", bpm),
                startEnergy = jsonObject.optDouble("start_energy", energy),
                endEnergy = jsonObject.optDouble("end_energy", energy)
            )
        } catch (e: Exception) {
            AudioFeatures(120.0, 0.5, 0.5)
        }
    }

    private fun computeEmotionScores(songs: List<SongWithEmotion>): List<SongWithEmotion> {
        if (songs.isEmpty()) return emptyList()
        val maxBpm = max(songs.maxOfOrNull { it.bpm } ?: 1.0, 1.0)
        val maxEnergy = max(songs.maxOfOrNull { it.energy } ?: 1e-9, 1e-9)
        val maxBrightness = max(songs.maxOfOrNull { it.brightness } ?: 1e-9, 1e-9)

        return songs.map { song ->
            val bpmNorm = song.bpm / maxBpm
            val energyNorm = song.energy / maxEnergy
            val brightnessNorm = song.brightness / maxBrightness
            val emotionScore = (bpmNorm * 0.4 + energyNorm * 0.4 + brightnessNorm * 0.2) * 100.0
            song.copy(emotionScore = Math.round(emotionScore * 100.0) / 100.0)
        }
    }

    fun generateIdealCurveLocal(numSongs: Int): List<Double> {
        if (numSongs <= 0) return emptyList()
        val timelineNorm = listOf(0.0, 0.15, 0.22, 0.32, 0.40, 0.55, 0.65, 0.80, 1.0)
        val emotions = listOf(0.1, 0.6, 0.25, 0.45, 0.35, 0.75, 0.4, 1.0, 0.05)
        val xs = if (numSongs == 1) listOf(0.5) else List(numSongs) { it.toDouble() / (numSongs - 1) }

        fun interp(x: Double): Double {
            if (x <= timelineNorm.first()) return emotions.first()
            if (x >= timelineNorm.last()) return emotions.last()
            for (i in 0 until timelineNorm.size - 1) {
                val left = timelineNorm[i]
                val right = timelineNorm[i + 1]
                if (x in left..right) {
                    val t = (x - left) / (right - left)
                    return emotions[i] * (1 - t) + emotions[i + 1] * t
                }
            }
            return emotions.last()
        }

        return xs.map { interp(it) * 80 + 10 }
    }

    fun sortSongsLocallyByFlow(songs: List<SongWithEmotion>): List<SongWithEmotion> {
        if (songs.isEmpty()) return emptyList()
        val idealCurve = generateIdealCurveLocal(songs.size)
        val remaining = songs.toMutableList()
        val sorted = mutableListOf<SongWithEmotion>()

        // Pick best starting song
        val firstTarget = idealCurve[0]
        val firstSong = remaining.minByOrNull { abs(it.emotionScore - firstTarget) }!!
        sorted.add(firstSong)
        remaining.remove(firstSong)

        for (i in 1 until idealCurve.size) {
            val targetEmotion = idealCurve[i]
            val prev = sorted.last()
            
            val nextSong = remaining.minByOrNull { cand ->
                val emotionDiff = abs(cand.emotionScore - targetEmotion)
                
                // Compare previous end to candidate start
                val candStartBpm = cand.startBpm
                val prevEndBpm = prev.endBpm
                val candStartEnergy = cand.startEnergy
                val prevEndEnergy = prev.endEnergy

                val maxB = max(candStartBpm, prevEndBpm)
                val minB = max(min(candStartBpm, prevEndBpm), 1.0)
                val bpmRatio = maxB / minB
                val bpmDiff = min(abs(bpmRatio - 1.0), abs(bpmRatio - 2.0)) * 100.0
                val energyDiff = abs(candStartEnergy - prevEndEnergy) * 50.0
                
                emotionDiff * 1.5 + bpmDiff * 1.0 + energyDiff * 0.5
            }!!
            
            sorted.add(nextSong)
            remaining.remove(nextSong)
        }
        
        return sorted
    }
}
