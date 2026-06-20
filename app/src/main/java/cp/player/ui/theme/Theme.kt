package cp.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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

/**
 * Creates [FontVariation.Settings] with wght and ROND axes using reflection.
 * Uses different internal Setting types for int (weight) and float (ROND) axes.
 * Compatible with Android 16 QPR2's ROND (roundness) axis for Google Sans Flex.
 * Falls back to weight-only settings if reflection fails.
 */
private fun createFontVariationSettings(wght: Int, rondValue: Float): FontVariation.Settings {
    return try {
        val weightSetting = FontVariation.weight(wght)
        // FontVariation.italic() returns a SettingFloat - same type needed for ROND
        val floatSample = FontVariation.italic(0f)
        val floatSettingClass = floatSample.javaClass

        // Create ROND axis via SettingFloat(String, float) constructor
        val floatCtor = floatSettingClass.getDeclaredConstructor(String::class.java, Float::class.javaPrimitiveType)
        floatCtor.isAccessible = true
        val rondSetting = floatCtor.newInstance("ROND", rondValue)

        // Find the Settings constructor that takes an array
        // The constructor takes the base Setting supertype array
        val settingsClass = FontVariation.Settings::class.java
        // Try constructors until we find one that takes an array parameter
        val settingsCtor = settingsClass.declaredConstructors.firstOrNull { ctor ->
            ctor.parameterCount == 1 && ctor.parameterTypes[0].isArray
        } ?: throw NoSuchMethodException("No array constructor found for Settings")

        settingsCtor.isAccessible = true
        val arrayParamType = settingsCtor.parameterTypes[0].componentType
            ?: throw NoSuchMethodException("Settings constructor parameter is not an array type")

        // Build typed array with both settings
        val axisArray = java.lang.reflect.Array.newInstance(arrayParamType, 2)
        java.lang.reflect.Array.set(axisArray, 0, weightSetting)
        java.lang.reflect.Array.set(axisArray, 1, rondSetting)

        @Suppress("UNCHECKED_CAST")
        settingsCtor.newInstance(axisArray) as FontVariation.Settings
    } catch (e: Exception) {
        // Fallback: use weight-only settings if reflection fails
        android.util.Log.w("CPPlayerTheme", "FontVariation ROND reflection failed, using default", e)
        FontVariation.Settings(FontVariation.weight(wght))
    }
}

/**
 * Creates a [FontFamily] for Google Sans Flex with the specified roundness mode.
 *
 * @param roundnessMode 0 = Standard (default ROND), 1 = Expressive (ROND = 100)
 * Matches Android 16 QPR2's font roundness behavior.
 */
@OptIn(ExperimentalTextApi::class)
fun createGoogleSansFlex(roundnessMode: Int): FontFamily {
    return try {
        val rondValue = when (roundnessMode) {
            1 -> 100f  // Expressive mode: maximum roundness (squircle-like)
            else -> 0f  // Standard mode: default roundness
        }

        // Standard mode uses the default font file without ROND axis modification
        if (rondValue == 0f) return GoogleSansFlex

        val weightValues = listOf(300, 400, 500, 600, 700)
        val fontWeights = listOf(
            FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
            FontWeight.SemiBold, FontWeight.Bold
        )

        FontFamily(
            weightValues.zip(fontWeights).map { (w, fw) ->
                Font(
                    resId = R.font.google_sans_flex,
                    weight = fw,
                    variationSettings = createFontVariationSettings(w, rondValue)
                )
            }
        )
    } catch (e: Exception) {
        android.util.Log.w("CPPlayerTheme", "Failed to create expressive font, using default", e)
        GoogleSansFlex
    }
}

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
            secondaryContainer = if (pureBlack) Color(0xFF121212) else color.copy(alpha = 0.2f),
            onSecondaryContainer = Color.White,
            surface = if (pureBlack) Color.Black else Color(0xFF121212),
            onSurface = Color.White,
            background = if (pureBlack) Color.Black else Color(0xFF121212),
            onBackground = Color.White,
            surfaceVariant = if (pureBlack) Color.Black else color.copy(alpha = 0.1f),
            onSurfaceVariant = Color.White,
            surfaceContainerLowest = if (pureBlack) Color.Black else Color(0xFF0D0D0D),
            surfaceContainerLow = if (pureBlack) Color.Black else Color(0xFF1A1A1A),
            surfaceContainer = if (pureBlack) Color.Black else Color(0xFF1F1F1F),
            surfaceContainerHigh = if (pureBlack) Color.Black else Color(0xFF232323),
            surfaceContainerHighest = if (pureBlack) Color.Black else Color(0xFF282828)
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
    fontRoundness: Int = 0, // 0: Standard, 1: Expressive
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

    if (darkTheme && pureBlack) {
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

    val activeFontFamily = remember(fontRoundness) { createGoogleSansFlex(fontRoundness) }

    val defaultTypography = Typography()
    val expressiveTypography = Typography(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = activeFontFamily, fontWeight = FontWeight.Normal),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = activeFontFamily),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = activeFontFamily),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = activeFontFamily),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = activeFontFamily),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = activeFontFamily),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = activeFontFamily)
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
