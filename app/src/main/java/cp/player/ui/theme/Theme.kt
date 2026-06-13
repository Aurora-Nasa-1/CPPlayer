package cp.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import cp.player.R

val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Light),
    Font(R.font.google_sans_flex, weight = FontWeight.Normal),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium),
    Font(R.font.google_sans_flex, weight = FontWeight.SemiBold),
    Font(R.font.google_sans_flex, weight = FontWeight.Bold)
)

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

fun createCustomColorScheme(seedColor: Int, isDark: Boolean, pureBlack: Boolean = false): ColorScheme {
    val color = Color(seedColor)
    val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(seedColor)
    val isDarkSeed = luminance < 0.5

    return if (isDark) {
        darkColorScheme(
            primary = color,
            onPrimary = if (isDarkSeed) Color.White else Color.Black,
            primaryContainer = color.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = color.copy(alpha = 0.5f),
            onSecondary = Color.White,
            secondaryContainer = color.copy(alpha = 0.2f),
            onSecondaryContainer = Color.White,
            surface = if (pureBlack) Color.Black else Color(0xFF121212),
            onSurface = Color.White,
            background = if (pureBlack) Color.Black else Color(0xFF121212),
            onBackground = Color.White,
            surfaceVariant = color.copy(alpha = 0.1f),
            onSurfaceVariant = Color.White
        )
    } else {
        lightColorScheme(
            primary = color,
            onPrimary = if (isDarkSeed) Color.White else Color.Black,
            primaryContainer = color.copy(alpha = 0.2f),
            onPrimaryContainer = color,
            secondary = color.copy(alpha = 0.6f),
            onSecondary = Color.White,
            secondaryContainer = color.copy(alpha = 0.1f),
            onSecondaryContainer = color,
            surface = Color(0xFFFDFDFD),
            onSurface = Color.Black,
            background = Color(0xFFFDFDFD),
            onBackground = Color.Black,
            surfaceVariant = color.copy(alpha = 0.05f),
            onSurfaceVariant = Color.Black
        )
    }
}

@Composable
fun CPPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    themeMode: Int = 0, // 0: System, 1: Follow Cover, 2: Fixed
    followCoverApp: Boolean = false,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        themeMode == 1 && followCoverApp && seedColor != null -> {
            createCustomColorScheme(seedColor, darkTheme, pureBlack)
        }
        themeMode == 2 -> { // Fixed (Reddish/Monet)
            createCustomColorScheme(0xFFB71C1C.toInt(), darkTheme, pureBlack)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    var finalColorScheme = colorScheme

    if (darkTheme && pureBlack && themeMode == 0) {
        finalColorScheme = finalColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceVariant = Color.Black,
            secondaryContainer = Color(0xFF121212),
            onSecondaryContainer = Color.White,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White
        )
    }

    val expressiveShapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), // Large increased (20dp)
        extraLarge = RoundedCornerShape(32.dp) // Extra large increased (32dp)
    )

    // MD3E additional rounding tokens (though standard Shapes doesn't have XXL/Full as specific fields, we can use them in components)
    // Extra extra large (48dp)
    // Fully Rounded (full token)

    val defaultTypography = Typography()
    val expressiveTypography = Typography(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansFlex),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansFlex),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansFlex),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansFlex),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansFlex),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansFlex)
        // All labels and buttons should be Sentence case by default in the UI layer
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    MaterialExpressiveTheme(
        colorScheme = finalColorScheme,
        shapes = expressiveShapes,
        typography = expressiveTypography,
        content = content
    )
}
