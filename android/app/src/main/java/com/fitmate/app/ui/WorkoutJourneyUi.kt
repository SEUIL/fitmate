package com.fitmate.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.WorkoutSummary

@Composable internal fun WorkoutJourneyPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun WorkoutSportChoice(running: Boolean, title: String, description: String, action: String, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                        Icon(if (running) Icons.AutoMirrored.Outlined.DirectionsRun else Icons.Outlined.FitnessCenter,
                            null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(action, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun WorkoutSavedHeader(summary: WorkoutSummary) {
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (appeared) 1f else .86f, tween(360), label = "저장 완료")
    LaunchedEffect(Unit) { appeared = true }
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(72.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Text("저장 완료", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text("오늘의 운동을 남겼어요.", style = MaterialTheme.typography.headlineSmall)
        Text("${summary.sets.size}세트 · ${summary.durationMinutes}분", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun WorkoutRestContent(seconds: Int, exercise: String, sets: Int, add: () -> Unit, edit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("✓ 세트 기록 완료", color = MaterialTheme.colorScheme.primary)
        Text("잠깐, 숨을 고르세요.", style = MaterialTheme.typography.headlineSmall)
        Text("다음 세트는 준비되면 시작하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.size(174.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary)) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.displaySmall)
                Text("휴식 시간", style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(add) { Text("+ 15초 추가") }
        WorkoutCard {
            Text("다음 세트", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Text(exercise, style = MaterialTheme.typography.titleLarge)
            Text("${sets + 1}세트", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(edit) { Text("방금 기록 수정") }
    }
}
