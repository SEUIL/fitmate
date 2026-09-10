package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Scoped to authentication and general settings; Samsung Health keeps its existing UI. */
@Composable internal fun AuthAction(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    SocialPrimaryButton(label, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled, onClick)

@Composable internal fun AuthSecretField(value: String, change: (String) -> Unit, label: String, enabled: Boolean = true) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = enabled,
        shape = RoundedCornerShape(12.dp))
}

@Composable internal fun AuthToggle(label: String, checked: Boolean, change: (Boolean) -> Unit,
    enabled: Boolean = true, description: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable internal fun HealthConsentScreen(modifier: Modifier, agree: () -> Unit, later: () -> Unit) =
    Screen(modifier, background = MaterialTheme.colorScheme.background) {
        WorkoutCard {
            Icon(Icons.Outlined.Shield, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("건강정보 처리 동의가 필요해요.", fontSize = 28.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
            Text("인바디 수치와 사진은 민감한 정보입니다. 동의하면 서버로 전송해 기록하며, 사진 판독 시 외부 AI 서비스에서 처리될 수 있습니다. 동의하지 않아도 기본 운동 기록은 사용할 수 있어요.")
            AuthAction("건강정보 처리에 동의하고 계속", onClick = agree)
            OutlinedButton(later, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) { Text("나중에") }
        }
    }
