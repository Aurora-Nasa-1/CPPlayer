package cp.player.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

// AGSL Fluid Shader inspired by Apple Music's dynamic background
private const val FLUID_SHADER = """
    uniform float2 iResolution;
    uniform float iTime;
    uniform float3 iColor1;
    uniform float3 iColor2;
    uniform float3 iColor3;
    uniform float3 iColor4;
    uniform float iLightMode;

    // High frequency dither to prevent banding
    float dither(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) * 0.02 - 0.01;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float aspect = iResolution.x / iResolution.y;
        uv.x *= aspect;

        float t = iTime * 0.2; // Smooth, gentle movement
        
        // Fluid simulation via smooth domain warping
        float2 p = uv;
        
        for (int i = 1; i < 6; i++) {
            float fi = float(i);
            float2 newp = p;
            newp.x += 0.3 / fi * sin(fi * 2.0 * p.y + t + 0.3 * fi) + 0.5;
            newp.y += 0.3 / fi * cos(fi * 1.5 * p.x - t + 0.3 * fi) - 0.5;
            p = newp;
        }
        
        // Use beautifully distorted coordinates to define smooth color zones
        float w1 = sin(p.x * 1.5 + t) * cos(p.y * 1.5 - t);
        float w2 = sin(p.x * 2.0 - t * 0.8) * cos(p.y * 1.2 + t * 1.2);
        float w3 = sin(p.x * 1.2 + t * 1.1) * cos(p.y * 1.8 - t * 0.9);
        float w4 = sin(p.x * 1.7 - t * 1.3) * cos(p.y * 1.4 + t * 0.7);

        // Map from [-1, 1] to [0, 1] softly
        w1 = smoothstep(-1.0, 1.0, w1);
        w2 = smoothstep(-1.0, 1.0, w2);
        w3 = smoothstep(-1.0, 1.0, w3);
        w4 = smoothstep(-1.0, 1.0, w4);

        // Non-linear weights to emphasize colors and prevent mud
        w1 = pow(w1, 2.0);
        w2 = pow(w2, 2.0);
        w3 = pow(w3, 2.0);
        w4 = pow(w4, 2.0);

        float sum = w1 + w2 + w3 + w4 + 0.001;
        float3 color = (iColor1 * w1 + iColor2 * w2 + iColor3 * w3 + iColor4 * w4) / sum;

        // Apply dither to prevent banding on smooth gradients
        color += dither(fragCoord);

        if (iLightMode < 0.5) {
            float3 baseLight = float3(0.98, 0.96, 0.95);
            color = mix(baseLight, color, 0.45);
            color = clamp(color * 1.05, 0.0, 1.0);
            return half4(color, 1.0);
        } else {
            float3 baseDark = float3(0.08, 0.09, 0.12);
            color = mix(baseDark, color, 0.55);
            color = smoothstep(0.0, 1.2, color);
            return half4(color, 1.0);
        }
    }
"""

@Composable
fun FluidBackground(
    color: Color,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AgslFluidBackground(color, isDark, modifier)
    } else {
        // Fallback for older versions: Simple animated gradient
        val infiniteTransition = rememberInfiniteTransition(label = "FluidFallback")
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Offset"
        )

        val colors = if (isDark) {
            listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.2f), Color.Black)
        } else {
            listOf(color.copy(alpha = 0.1f), color.copy(alpha = 0.05f), Color.White)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = colors,
                        start = androidx.compose.ui.geometry.Offset(offset, offset),
                        end = androidx.compose.ui.geometry.Offset(offset + 500f, offset + 500f)
                    )
                )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AgslFluidBackground(
    color: Color,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shader = remember { RuntimeShader(FLUID_SHADER) }
    val infiniteTransition = rememberInfiniteTransition(label = "FluidShader")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )

    // Monet-inspired complementary colors for fluid effect: soft, pastel-like transition
    val color1 = color
    val color2 = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] + 25) % 360
        hsv[1] = (hsv[1] * 0.6f).coerceIn(0.2f, 0.6f) // Softer saturation for Monet feel
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }
    val color3 = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] - 20 + 360) % 360
        hsv[1] = (hsv[1] * 0.7f).coerceIn(0.2f, 0.7f)
        hsv[2] = (hsv[2] * 0.85f).coerceIn(0.3f, 0.9f)
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }
    val color4 = remember(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] + 180) % 360 // Complementary contrast hue
        hsv[1] = (hsv[1] * 0.4f).coerceIn(0.1f, 0.4f)
        hsv[2] = (hsv[2] * 0.9f).coerceIn(0.4f, 1f)
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iColor1", color1.red, color1.green, color1.blue)
        shader.setFloatUniform("iColor2", color2.red, color2.green, color2.blue)
        shader.setFloatUniform("iColor3", color3.red, color3.green, color3.blue)
        shader.setFloatUniform("iColor4", color4.red, color4.green, color4.blue)
        shader.setFloatUniform("iLightMode", if (isDark) 1.0f else 0.0f)

        drawRect(brush = ShaderBrush(shader))
    }
}
