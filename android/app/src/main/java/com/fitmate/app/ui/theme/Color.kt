package com.fitmate.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object FitMateColors {
    val Primary = Color(0xFF0C9277)
    val PrimaryContainer = Color(0xFFD9F3EB)
    val Background = Color(0xFFF7F8F7)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF18221F)
    val TextSecondary = Color(0xFF66736E)
    val Outline = Color(0xFFE4E9E6)
    val DarkSurface = Color(0xFF17231F)
    val OnDarkSurface = Color(0xFFF4FBF8)
    val OnDarkSurfaceMuted = Color(0xFFBFD1CA)
    val NeutralIcon = Color(0xFF7A8580)
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
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(10.dp)
}
