package com.fitmate.app.ui.theme

import android.app.Activity
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
    primary = Color(0xFF6FE5CA),
    secondary = Color(0xFFB6CEC7),
    tertiary = Color(0xFFD7C5FF),
    background = Color(0xFF0B1513),
    surface = Color(0xFF14201E)
)

private val LightColorScheme = lightColorScheme(
    primary = FitMateColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FitMateColors.PrimaryContainer,
    onPrimaryContainer = FitMateColors.TextPrimary,
    secondary = FitMateColors.TextSecondary,
    background = FitMateColors.Background,
    onBackground = FitMateColors.TextPrimary,
    surface = FitMateColors.Surface,
    onSurface = FitMateColors.TextPrimary,
    surfaceVariant = FitMateColors.Background,
    outlineVariant = FitMateColors.Outline
)

@Composable
fun FitmateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
