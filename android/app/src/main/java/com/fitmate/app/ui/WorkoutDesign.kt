package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.ui.theme.AppearanceMode
import com.fitmate.app.ui.theme.LocalAppearancePreference

/** One quiet navigation row; the screen owns its main title in the content below. */
@Composable internal fun WorkoutFlowBackBar(context: String, back: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Row(modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "이전") }
            Spacer(Modifier.weight(1f))
            if (context.isNotBlank()) Text(context, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Applied only to the reviewed routes. The shared theme and Samsung Health retain their styles.
@Composable internal fun WorkoutDesignTheme(enabled: Boolean = true, journey: Boolean = false, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val dark = when (LocalAppearancePreference.current.mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val colors = MaterialTheme.colorScheme.copy(
        primary = if (dark) Color(0xFF16DFC1) else Color(0xFF007D70),
        onPrimary = if (dark) Color(0xFF182129) else Color.White,
        primaryContainer = Color(0xFF16DFC1), onPrimaryContainer = Color(0xFF182129),
        secondaryContainer = Color(0xFF16DFC1), onSecondaryContainer = Color(0xFF182129),
        background = if (dark) Color(0xFF1C2025) else Color(0xFFF6F8FA),
        surface = if (dark) Color(0xFF282E35) else Color.White,
        onBackground = if (dark) Color(0xFFF5F7FA) else Color(0xFF182129),
        onSurface = if (dark) Color(0xFFF5F7FA) else Color(0xFF182129),
        surfaceContainerLow = if (dark) Color(0xFF1C2025) else Color(0xFFF6F8FA),
        surfaceContainer = if (dark) Color(0xFF333C46) else Color(0xFFEAF0F4),
        surfaceVariant = if (dark) Color(0xFF333C46) else Color(0xFFEAF0F4),
        onSurfaceVariant = if (dark) Color(0xFFAFBAC6) else Color(0xFF5D6874),
        outline = if (dark) Color(0xFF81909E) else Color(0xFF71808D),
        outlineVariant = if (dark) Color(0xFF414B56) else Color(0xFFD7DEE6),
        error = if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E),
    )
    val journeyColors = if (!journey) colors else colors.copy(
        primary = if (dark) Color(0xFF8AD8C5) else Color(0xFF008675),
        onPrimary = if (dark) Color(0xFF123C33) else Color.White,
        primaryContainer = if (dark) Color(0xFF264C42) else Color(0xFFE9F6F0),
        onPrimaryContainer = if (dark) Color(0xFFB5EDDB) else Color(0xFF267C63),
        secondaryContainer = if (dark) Color(0xFF264C42) else Color(0xFFE9F6F0),
        onSecondaryContainer = if (dark) Color(0xFFB5EDDB) else Color(0xFF267C63),
        background = if (dark) colors.background else Color(0xFFF7F9F7),
        onSurface = if (dark) colors.onSurface else Color(0xFF182B2A),
        outlineVariant = if (dark) colors.outlineVariant else Color(0xFFE0E9E3),
        surfaceContainerLow = if (dark) colors.surfaceContainerLow else Color(0xFFF7F9F7),
    )
    val type = MaterialTheme.typography.copy(
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    )
    MaterialTheme(colorScheme = journeyColors, typography = if (!journey) type else type.copy(
        headlineLarge = type.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = type.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = type.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = type.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = type.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = type.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    ), content = content)
}

@Composable internal fun WorkoutHeading(title: String, description: String) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun WorkoutSportLabel(running: Boolean, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(if (running) Icons.AutoMirrored.Outlined.DirectionsRun else Icons.Outlined.FitnessCenter,
            null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun WorkoutMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun WorkoutMetrics(vararg values: Pair<String, String>) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            values.forEach { (label, value) -> WorkoutMetric(label, value) }
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEach { (label, value) -> WorkoutMetric(label, value, Modifier.weight(1f)) }
    }
}

@Composable internal fun WorkoutChoices(options: List<String>, selected: String, columns: Int = 3, onSelect: (String) -> Unit) {
    val count = if (LocalDensity.current.fontScale > 1.3f) minOf(columns, 2) else columns
    options.chunked(count).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { option ->
                FilterChip(selected == option, { onSelect(option) },
                    label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(option) } },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp))
            }
            repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable internal fun WorkoutSecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp)) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable internal fun WorkoutAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun WorkoutCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
