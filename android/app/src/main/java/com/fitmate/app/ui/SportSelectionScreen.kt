package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun SportSelectionScreen(modifier: Modifier, api: FitMateApiClient, select: (String) -> Unit) = WorkoutJourneyPage(modifier) {
    var codes by remember(api) { mutableStateOf<Set<String>?>(null) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, retry) {
        error = null
        try { codes = withContext(Dispatchers.IO) { api.fetchSports().map { it.code }.toSet() } }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "운동 종목을 불러오지 못했어요." }
    }
    PlanHeading("오늘의 운동", "어떤 움직임으로 하루를 채울까요?")
    if (codes == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error); WorkoutSecondaryAction("다시 불러오기") { retry++ } }
    val supported = listOf("HEALTH", "RUNNING") + additionalSports.map { it.name }
    supported.filter { codes?.contains(it) == true }.forEach { code ->
        val description = when (code) {
            "HEALTH" -> "루틴 선택 · 세트별 운동 기록"
            "RUNNING" -> "목표 설정 · 거리와 시간 기록"
            else -> sportTypes(code).values.joinToString(" · ")
        }
        OutlinedCard(onClick = { select(code) }, Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(sportIcon(code), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(sportLabel(code), style = MaterialTheme.typography.titleLarge)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "${sportLabel(code)} 준비")
            }
        }
    }
    if (codes != null && codes!!.none { it in supported }) Text("현재 시작할 수 있는 운동이 없어요.")
}
