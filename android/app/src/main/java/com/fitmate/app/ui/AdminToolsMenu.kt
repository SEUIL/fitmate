package com.fitmate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AdminToolsMenu(snapshot: AdminSnapshot, enabled: Boolean, open: (String) -> Unit) {
    val canManage = snapshot.remote && snapshot.role in listOf("ADMIN", "SUPER_ADMIN")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AdminPageHeading("운영 도구", "서비스 설정과 운영 기준을 관리하세요.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("서비스 관리", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AdminListGroup {
                    AdminToolRow("공지 관리", "작성 · 게시 · 종료", Icons.Outlined.Campaign,
                        enabled && canManage, if (canManage) null else "권한 필요") { open("공지 관리") }
                    ToolDivider()
                    AdminToolRow("카테고리 관리", "종목 순서 · 기타 운동 집계", Icons.Outlined.Category,
                        enabled && canManage, if (canManage) null else "권한 필요") { open("카테고리 관리") }
                    ToolDivider()
                    AdminToolRow("시스템 현황", "인바디 판독 · 실패 내역", Icons.Outlined.MonitorHeart,
                        enabled && canManage, if (canManage) null else "권한 필요") { open("시스템 현황") }
            }
            if (!canManage) Text("서비스 관리는 관리자 이상만 사용할 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("운영 기준", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AdminListGroup {
                AdminToolRow("운영 규칙", "탐지 기준 · 검토 후보", Icons.AutoMirrored.Outlined.Rule,
                    enabled && canManage, if (canManage) null else "권한 필요") { open("운영 규칙") }
                ToolDivider()
                AdminToolRow("운영자 권한", "역할 부여 · 변경 · 회수", Icons.Outlined.AdminPanelSettings,
                    enabled && snapshot.remote && snapshot.role == "SUPER_ADMIN", if (snapshot.role == "SUPER_ADMIN") null else "최고 관리자") { open("운영자 권한") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("관련 기록", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AdminListGroup {
                AdminToolRow("신고 관련 회원", "신고에 연결된 회원", Icons.Outlined.PeopleOutline, enabled) { open("신고 관련 회원") }
                ToolDivider()
                AdminToolRow("신고 콘텐츠", "신고된 원문과 처리 내역", Icons.Outlined.Description, enabled) { open("신고 콘텐츠") }
            }
        }
    }
}

@Composable
private fun ToolDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun AdminToolRow(title: String, description: String, icon: ImageVector, enabled: Boolean,
    badge: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = if (onClick != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (onClick != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
