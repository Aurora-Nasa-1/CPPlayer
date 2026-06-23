package cp.player.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cp.player.engine.ExoAudioFxManager
import cp.player.engine.RustEngine
import cp.player.ui.component.AppScaffold
import cp.player.util.AutoEqParser
import cp.player.util.DspPreferences
import cp.player.util.UserPreferences
import cp.player.util.PeqBand
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val engineType = UserPreferences.getAudioEngine(context) // 0: ExoPlayer, 1: FlickPlayer

    AppScaffold(
        title = "DSP & Equalizer",
        onBackPressed = onNavigateBack
    ) { innerPadding ->
        if (engineType == 0) {
            ExoPlayerDspConfig(Modifier.padding(innerPadding))
        } else {
            FlickPlayerDspConfig(Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun ExoPlayerDspConfig(modifier: Modifier = Modifier) {
    var eqEnabled by remember { mutableStateOf(ExoAudioFxManager.getEqualizerEnabled()) }
    var virtualizerEnabled by remember { mutableStateOf(ExoAudioFxManager.getVirtualizerEnabled()) }
    var virtualizerStrength by remember { mutableStateOf(ExoAudioFxManager.getVirtualizerStrength().toFloat()) }

    val numBands = ExoAudioFxManager.getNumberOfBands()
    val bandLevels = remember { mutableStateMapOf<Short, Short>() }

    LaunchedEffect(numBands) {
        for (i in 0 until numBands) {
            val band = i.toShort()
            bandLevels[band] = ExoAudioFxManager.getBandLevel(band)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("ExoPlayer Native DSP", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Equalizer Enabled")
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = {
                        eqEnabled = it
                        ExoAudioFxManager.setEqualizerEnabled(it)
                    }
                )
            }
        }

        if (eqEnabled) {
            items(numBands.toInt()) { i ->
                val band = i.toShort()
                val freq = ExoAudioFxManager.getCenterFreq(band) / 1000
                val level = bandLevels[band] ?: 0
                val range = ExoAudioFxManager.getBandLevelRange()

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${freq} Hz")
                        Text("${level / 100} dB")
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = {
                            val newLevel = it.toInt().toShort()
                            bandLevels[band] = newLevel
                            ExoAudioFxManager.setBandLevel(band, newLevel)
                        },
                        valueRange = range[0].toFloat()..range[1].toFloat()
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Virtualizer Enabled")
                Switch(
                    checked = virtualizerEnabled,
                    onCheckedChange = {
                        virtualizerEnabled = it
                        ExoAudioFxManager.setVirtualizerEnabled(it)
                    }
                )
            }
        }

        if (virtualizerEnabled) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Virtualizer Strength")
                    Slider(
                        value = virtualizerStrength,
                        onValueChange = {
                            virtualizerStrength = it
                            ExoAudioFxManager.setVirtualizerStrength(it.toInt().toShort())
                        },
                        valueRange = 0f..1000f
                    )
                }
            }
        }
    }
}

@Composable
fun FlickPlayerDspConfig(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var eqEnabled by remember { mutableStateOf(DspPreferences.getEqEnabled(context)) }
    val peqBands = remember { mutableStateListOf<PeqBand>().apply { addAll(DspPreferences.getPeqBands(context)) } }

    // Rust Engine FX States
    var fxEnabled by remember { mutableStateOf(DspPreferences.getFxEnabled(context)) }
    var fxBalance by remember { mutableStateOf(0f) }
    var fxTempo by remember { mutableStateOf(1f) }
    var fxDamp by remember { mutableStateOf(0.35f) }
    var fxFilterHz by remember { mutableStateOf(6800f) }
    var fxDelayMs by remember { mutableStateOf(240f) }
    var fxSize by remember { mutableStateOf(DspPreferences.getFxSize(context)) }
    var fxMix by remember { mutableStateOf(DspPreferences.getFxMix(context)) }
    var fxFeedback by remember { mutableStateOf(0.35f) }
    var fxWidth by remember { mutableStateOf(DspPreferences.getFxWidth(context)) }

    fun applyRustEq() {
        if (peqBands.isEmpty()) {
            RustEngine.setEqualizer(eqEnabled, floatArrayOf(), floatArrayOf(), floatArrayOf())
            return
        }
        val freqs = peqBands.map { it.freq }.toFloatArray()
        val gains = peqBands.map { it.gain }.toFloatArray()
        val qs = peqBands.map { it.q }.toFloatArray()
        RustEngine.setEqualizer(eqEnabled, freqs, gains, qs)
    }

    fun applyRustFx() {
        DspPreferences.setFxEnabled(context, fxEnabled)
        DspPreferences.setFxSize(context, fxSize)
        DspPreferences.setFxMix(context, fxMix)
        DspPreferences.setFxWidth(context, fxWidth)
        RustEngine.setFx(
            fxEnabled, fxBalance, fxTempo, fxDamp, fxFilterHz, fxDelayMs, fxSize, fxMix, fxFeedback, fxWidth
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val parsedBands = AutoEqParser.parse(context, it)
            if (parsedBands != null) {
                peqBands.clear()
                peqBands.addAll(parsedBands)
                applyRustEq()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("FlickPlayer Rust DSP", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Parametric EQ Enabled")
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = {
                        eqEnabled = it
                        applyRustEq()
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { launcher.launch("text/plain") }) {
                    Text("Import AutoEQ")
                }
                IconButton(onClick = {
                    peqBands.add(PeqBand(1000f, 0f, 1f))
                    applyRustEq()
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Band")
                }
            }
        }

        if (eqEnabled) {
            itemsIndexed(peqBands) { index, band ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Band ${index + 1}")
                            IconButton(onClick = {
                                peqBands.removeAt(index)
                                applyRustEq()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Band")
                            }
                        }

                        // Freq Slider
                        Text("Freq: ${band.freq.roundToInt()} Hz")
                        Slider(
                            value = band.freq,
                            onValueChange = {
                                peqBands[index] = band.copy(freq = it)
                                applyRustEq()
                            },
                            valueRange = 20f..20000f
                        )

                        // Gain Slider
                        Text("Gain: ${String.format("%.1f", band.gain)} dB")
                        Slider(
                            value = band.gain,
                            onValueChange = {
                                peqBands[index] = band.copy(gain = it)
                                applyRustEq()
                            },
                            valueRange = -24f..24f
                        )

                        // Q Slider
                        Text("Q: ${String.format("%.2f", band.q)}")
                        Slider(
                            value = band.q,
                            onValueChange = {
                                peqBands[index] = band.copy(q = it)
                                applyRustEq()
                            },
                            valueRange = 0.1f..10f
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Spatial / Time FX Enabled")
                Switch(
                    checked = fxEnabled,
                    onCheckedChange = {
                        fxEnabled = it
                        applyRustFx()
                    }
                )
            }
        }

        if (fxEnabled) {
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Crossfeed Size: ${String.format("%.2f", fxSize)}")
                    Slider(
                        value = fxSize,
                        onValueChange = { fxSize = it; applyRustFx() },
                        valueRange = 0f..1f
                    )

                    Text("Mix: ${String.format("%.2f", fxMix)}")
                    Slider(
                        value = fxMix,
                        onValueChange = { fxMix = it; applyRustFx() },
                        valueRange = 0f..1f
                    )

                    Text("Stereo Width: ${String.format("%.2f", fxWidth)}")
                    Slider(
                        value = fxWidth,
                        onValueChange = { fxWidth = it; applyRustFx() },
                        valueRange = 0f..2f
                    )
                }
            }
        }
    }
}
