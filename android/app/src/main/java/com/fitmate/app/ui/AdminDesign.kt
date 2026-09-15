package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Local to operator screens; preserves the reviewed FitMate mint palette. */
@Composable internal fun AdminDesignTheme(content: @Composable () -> Unit) {
    WorkoutDesignTheme {
        val type = MaterialTheme.typography
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(tertiary = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFEBBD68) else Color(0xFFA16609)),
            typography = type.copy(
                headlineSmall = type.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
                headlineMedium = type.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
                titleLarge = type.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                titleMedium = type.titleMedium.copy(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
                bodyMedium = type.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                bodySmall = type.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = type.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            ),
            shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AdminTopBar(title: String, onBack: () -> Unit, enabled: Boolean = true, role: String? = null) {
    Column {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onBack, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") } },
            actions = { role?.takeIf { it.isNotBlank() }?.let {
                Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                    color = Color.Transparent, modifier = Modifier.padding(end = 16.dp)) {
                    Text(adminRoleLabel(it), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
    }
}

internal fun adminRoleLabel(value: String) = when (value) {
    "SUPER_ADMIN" -> "최고 관리자"; "ADMIN" -> "관리자"; "MODERATOR" -> "검토자"; "TEST" -> "테스트"; else -> value
}

@Composable internal fun AdminNavigation(tab: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            val icons = listOf(Icons.Outlined.GridView, Icons.Outlined.Flag, Icons.Outlined.Forum, Icons.Outlined.Tune)
            listOf("현황", "신고", "이의신청", "운영 도구").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { onSelect(index) }, enabled = enabled, modifier = Modifier.testTag("admin-tab-$index"),
                    icon = { Icon(icons[index], null, Modifier.size(22.dp)) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

@Composable internal fun AdminPageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun AdminSectionHeading(title: String) {
    Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable internal fun AdminPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable internal fun AdminBadge(text: String, attention: Boolean = false, complete: Boolean = false) {
    val tint = if (attention) MaterialTheme.colorScheme.tertiary else if (complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Surface(color = tint.copy(alpha = .12f), shape = RoundedCornerShape(5.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable internal fun AdminListRow(title: String, subtitle: String, enabled: Boolean = true, icon: ImageVector? = null,
    status: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.let { Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(it, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
            } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                status?.invoke()
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun AdminListGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) { Column(Modifier.fillMaxWidth(), content = content) }
}

@Composable internal fun AdminRowDivider() { HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)) }

@Composable internal fun AdminPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp), content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = shape, content = content)
}

@Composable internal fun AdminFilterChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: @Composable () -> Unit) {
    FilterChip(selected, onClick, label, modifier, enabled = enabled, shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurfaceVariant))
}
