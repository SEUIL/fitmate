package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Keeps the existing selection order, including the primary goal, in the caller. */
@Composable internal fun ProfileChoices(options: List<String>, selected: Set<String>, columns: Int = 3,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, onSelect: (String) -> Unit) {
    val count = if (LocalDensity.current.fontScale > 1.3f) 1 else columns
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(count).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    FilterChip(label in selected, { onSelect(label) },
                        label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(label, style = labelStyle, fontWeight = if (label in selected) FontWeight.Bold else FontWeight.Normal) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
                repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
