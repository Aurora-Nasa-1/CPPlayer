package cp.player.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isWavy: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        track = { sliderState ->
            if (isWavy) {
                LinearWavyProgressIndicator(
                    progress = { sliderState.value },
                    modifier = Modifier,
                    color = colors.activeTrackColor,
                    trackColor = colors.inactiveTrackColor,
                    gapSize = 4.dp,
                    stopSize = 0.dp
                )
            } else {
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier,
                    colors = colors,
                    enabled = enabled,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 4.dp,
                    trackInsideCornerSize = 0.dp
                )
            }
        }
    )
}
