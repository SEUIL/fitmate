package com.fitmate.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object FitMateColors {
    val Primary: Color @Composable get() = MaterialTheme.colorScheme.primary
    val WorkoutAction = Color(0xFF49766C)
    val PrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Outline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val DarkSurface = Color(0xFF171717)
    val OnDarkSurface = Color(0xFFF4FBF8)
    val OnDarkSurfaceMuted = Color(0xFFBFD1CA)
    val NeutralIcon: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

object FitMateSpacing {
    val ScreenHorizontal = 20.dp
    val Section = 20.dp
    val Item = 12.dp
    val CardPadding = 16.dp
    val CompactPadding = 12.dp
}

object FitMateShapes {
    val MainCard = RoundedCornerShape(18.dp)
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(10.dp)
    val Chip = RoundedCornerShape(10.dp)
}
