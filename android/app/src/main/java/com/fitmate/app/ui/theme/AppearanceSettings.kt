package com.fitmate.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class AppearanceMode(val label: String) {
    SYSTEM("시스템 설정 따르기"), LIGHT("라이트"), DARK("다크");
    companion object {
        fun fromStored(value: String?): AppearanceMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class AppearancePreference(val mode: AppearanceMode, val select: (AppearanceMode) -> Unit)
val LocalAppearancePreference = staticCompositionLocalOf { AppearancePreference(AppearanceMode.SYSTEM, {}) }

@Composable
fun AppearanceSettings() {
    val preference = LocalAppearancePreference.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppearanceMode.entries.forEach { mode ->
            Surface(shape = RoundedCornerShape(14.dp), color = if (preference.mode == mode) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (preference.mode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(preference.mode == mode, role = Role.RadioButton, onClick = { preference.select(mode) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = preference.mode == mode, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(mode.label)
            }
            }
        }
    }
}
