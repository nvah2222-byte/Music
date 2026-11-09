package com.example.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/** Стандартный оттенок темы приложения (фиолетовый). */
val DefaultAccentColor = Color(0xFF6750A4)

/**
 * Схема из одного оттенка: primary/контейнеры/вторичный подобраны по HSV,
 * а нейтральные поверхности лишь слегка тонируются оттенком — иначе карточки,
 * шторка и выделение остаются стандартными бледно-фиолетовыми.
 */
private fun accentScheme(hue: Float, dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = Color.hsv(hue, 0.55f, 0.85f),
            onPrimary = Color.hsv(hue, 0.8f, 0.15f),
            primaryContainer = Color.hsv(hue, 0.6f, 0.32f),
            onPrimaryContainer = Color.hsv(hue, 0.25f, 0.95f),
            secondary = Color.hsv(hue, 0.35f, 0.80f),
            onSecondary = Color.hsv(hue, 0.6f, 0.15f),
            secondaryContainer = Color.hsv(hue, 0.35f, 0.30f),
            onSecondaryContainer = Color.hsv(hue, 0.2f, 0.95f),
            tertiary = Color.hsv((hue + 60f) % 360f, 0.40f, 0.80f),
            onTertiary = Color.hsv((hue + 60f) % 360f, 0.6f, 0.15f),
            tertiaryContainer = Color.hsv((hue + 60f) % 360f, 0.4f, 0.30f),
            onTertiaryContainer = Color.hsv((hue + 60f) % 360f, 0.25f, 0.95f),
            background = Color.hsv(hue, 0.06f, 0.08f),
            onBackground = Color.hsv(hue, 0.05f, 0.92f),
            surface = Color.hsv(hue, 0.06f, 0.08f),
            onSurface = Color.hsv(hue, 0.05f, 0.92f),
            surfaceVariant = Color.hsv(hue, 0.10f, 0.26f),
            onSurfaceVariant = Color.hsv(hue, 0.08f, 0.80f),
            surfaceDim = Color.hsv(hue, 0.06f, 0.06f),
            surfaceBright = Color.hsv(hue, 0.06f, 0.24f),
            surfaceContainerLowest = Color.hsv(hue, 0.06f, 0.05f),
            surfaceContainerLow = Color.hsv(hue, 0.06f, 0.10f),
            surfaceContainer = Color.hsv(hue, 0.06f, 0.12f),
            surfaceContainerHigh = Color.hsv(hue, 0.06f, 0.17f),
            surfaceContainerHighest = Color.hsv(hue, 0.06f, 0.22f),
            outline = Color.hsv(hue, 0.08f, 0.55f),
            outlineVariant = Color.hsv(hue, 0.10f, 0.35f),
            inverseSurface = Color.hsv(hue, 0.05f, 0.92f),
            inverseOnSurface = Color.hsv(hue, 0.10f, 0.15f),
            inversePrimary = Color.hsv(hue, 0.60f, 0.45f)
        )
    } else {
        lightColorScheme(
            primary = Color.hsv(hue, 0.65f, 0.45f),
            onPrimary = Color.White,
            primaryContainer = Color.hsv(hue, 0.30f, 0.92f),
            onPrimaryContainer = Color.hsv(hue, 0.70f, 0.18f),
            secondary = Color.hsv(hue, 0.40f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = Color.hsv(hue, 0.22f, 0.90f),
            onSecondaryContainer = Color.hsv(hue, 0.55f, 0.20f),
            tertiary = Color.hsv((hue + 60f) % 360f, 0.45f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = Color.hsv((hue + 60f) % 360f, 0.25f, 0.92f),
            onTertiaryContainer = Color.hsv((hue + 60f) % 360f, 0.60f, 0.18f),
            background = Color.hsv(hue, 0.04f, 0.98f),
            onBackground = Color.hsv(hue, 0.10f, 0.12f),
            surface = Color.hsv(hue, 0.03f, 0.99f),
            onSurface = Color.hsv(hue, 0.10f, 0.12f),
            surfaceVariant = Color.hsv(hue, 0.10f, 0.92f),
            onSurfaceVariant = Color.hsv(hue, 0.12f, 0.34f),
            surfaceDim = Color.hsv(hue, 0.06f, 0.87f),
            surfaceBright = Color.hsv(hue, 0.03f, 0.99f),
            surfaceContainerLowest = Color.hsv(hue, 0.02f, 1f),
            surfaceContainerLow = Color.hsv(hue, 0.03f, 0.97f),
            surfaceContainer = Color.hsv(hue, 0.04f, 0.95f),
            surfaceContainerHigh = Color.hsv(hue, 0.05f, 0.93f),
            surfaceContainerHighest = Color.hsv(hue, 0.06f, 0.91f),
            outline = Color.hsv(hue, 0.08f, 0.50f),
            outlineVariant = Color.hsv(hue, 0.08f, 0.80f),
            inverseSurface = Color.hsv(hue, 0.10f, 0.20f),
            inverseOnSurface = Color.hsv(hue, 0.05f, 0.95f),
            inversePrimary = Color.hsv(hue, 0.45f, 0.80f)
        )
    }

@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    // -1 — стандартная фиолетовая тема, иначе оттенок акцента 0..359.
    accentHue: Int = -1,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        accentHue in 0..359 -> accentScheme(accentHue.toFloat(), darkTheme)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
