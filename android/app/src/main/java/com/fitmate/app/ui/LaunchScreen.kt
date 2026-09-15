package com.fitmate.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.R
import com.fitmate.app.ui.theme.AppearanceMode
import com.fitmate.app.ui.theme.FitmateTheme
import com.fitmate.app.ui.theme.LocalAppearancePreference
import kotlinx.coroutines.delay

@Composable
internal fun rememberLaunchReady(): Boolean {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Start the minimum display time after the first rendered frame.
        withFrameNanos { }
        delay(2_000)
        ready = true
    }
    return ready
}

@Composable
internal fun LaunchScreen(
    modifier: Modifier = Modifier,
    dark: Boolean = when (LocalAppearancePreference.current.mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
) {
    val background = if (dark) Color(0xFF1C2025) else Color(0xFFF6F8FA)
    val foreground = if (dark) Color(0xFFF5F7FA) else Color(0xFF182129)
    val secondary = if (dark) Color(0xFFAFBAC6) else Color(0xFF5D6874)
    BoxWithConstraints(modifier.fillMaxSize().background(background).safeDrawingPadding()) {
        // The wordmark is branding; keep it within the viewport when body text is enlarged.
        val brandSize = (maxWidth.value / 7.5f).coerceIn(32f, 56f) / LocalDensity.current.fontScale.coerceAtLeast(1f)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(if (dark) R.drawable.fitmate_symbol else R.drawable.fitmate_launch_symbol_light),
                    contentDescription = null,
                    modifier = Modifier.size(176.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("FitMate", color = foreground, fontSize = brandSize.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("나의 운동을 함께 기록하다", color = secondary, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Column(
                Modifier.padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.width(128.dp).height(4.dp),
                    color = Color(0xFF16DFC1),
                    trackColor = if (dark) Color(0xFF414B56) else Color(0xFFD7DEE6)
                )
                Text(
                    "잠시만 기다려 주세요.", color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

@Preview(name = "Launch light", widthDp = 393, heightDp = 852)
@Composable private fun LaunchLightPreview() = FitmateTheme(darkTheme = false) { LaunchScreen(dark = false) }

@Preview(name = "Launch dark", widthDp = 393, heightDp = 852)
@Composable private fun LaunchDarkPreview() = FitmateTheme(darkTheme = true) { LaunchScreen(dark = true) }
