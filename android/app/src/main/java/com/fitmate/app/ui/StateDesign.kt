package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** State presentation for reviewed routes only; callbacks retain the existing state transitions. */
@Composable internal fun StateCard(title: String, description: String, icon: ImageVector,
    loading: Boolean = false, actions: (@Composable ColumnScope.() -> Unit)? = null) {
    WorkoutCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            actions?.invoke(this)
        }
    }
}

@Composable internal fun StateWarning(message: String) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.error.copy(alpha = .08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .35f))) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, "주의", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun StateConfirm(title: String, body: String, dismiss: () -> Unit,
    destructive: Boolean = false, busy: Boolean = false, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(body)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(confirm, Modifier.heightIn(min = 48.dp), enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) { Text("확인") } },
        dismissButton = { TextButton(dismiss, Modifier.heightIn(min = 48.dp), enabled = !busy) { Text("취소") } })
}
