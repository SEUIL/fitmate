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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8DCBB8), onPrimary = Color(0xFF153B30),
    primaryContainer = Color(0xFF254A3E), onPrimaryContainer = Color(0xFFD9F3EB),
    secondary = Color(0xFFB2C9C0), onSecondary = Color(0xFF20382F),
    secondaryContainer = Color(0xFF254A3E), onSecondaryContainer = Color(0xFFD9F3EB),
    tertiary = Color(0xFFA9CABC), onTertiary = Color(0xFF18362B),
    background = Color(0xFF111513), onBackground = Color(0xFFE7EDE9),
    surface = Color(0xFF1B211E), onSurface = Color(0xFFE7EDE9),
    surfaceVariant = Color(0xFF29332D), onSurfaceVariant = Color(0xFFAFBDB5),
    surfaceContainerLowest = Color(0xFF0D110F), surfaceContainerLow = Color(0xFF151B17),
    surfaceContainer = Color(0xFF202823), surfaceContainerHigh = Color(0xFF29312C),
    surfaceContainerHighest = Color(0xFF343D36),
    outline = Color(0xFF819189), outlineVariant = Color(0xFF39463E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0C9277), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F3EB), onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF737373), secondaryContainer = Color(0xFFD9F3EB),
    onSecondaryContainer = Color(0xFF171717),
    background = Color.White, onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF1F3F1), onSurfaceVariant = Color(0xFF737373),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF1F3F1), surfaceContainerHigh = Color(0xFFEBEFEC),
    surfaceContainerHighest = Color(0xFFE5EAE6),
    outline = Color(0xFF87918A), outlineVariant = Color(0xFFEBEBEB)
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
