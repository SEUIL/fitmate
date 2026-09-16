package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun PlanTheme(content: @Composable () -> Unit) {
    // Inherit the app's workout palette, including the user's appearance preference.
    MaterialTheme(typography = MaterialTheme.typography.copy(
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        titleSmall = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 21.sp),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    ), content = content)
}

@Composable internal fun PlanPage(modifier: Modifier, title: String, back: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            WorkoutFlowBackBar(title, back)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = footer)
        }
    }
}

@Composable internal fun PlanHeading(title: String, subtitle: String? = null) {
    Text(title, fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
}

@Composable internal fun PlanAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled,
        shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(text) }
}

@Composable internal fun PlanSummary(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable internal fun PlanBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun PlanDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton({ expanded = !expanded }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(if (expanded) "−" else "+", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
        HorizontalDivider()
    }
}

@Composable internal fun PlanExerciseRow(index: Int, name: String, detail: String, enabled: Boolean = true, replace: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("%02d".format(index + 1), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            replace?.let { TextButton(it, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("교체", style = MaterialTheme.typography.labelSmall) } }
        }
        HorizontalDivider()
    }
}

/** Null means the server does not support a trustworthy day preview. Never guess day one. */
internal fun nextRoutineItems(routine: JsonObject): List<JsonObject>? {
    val day = routine.get("nextSplitDayNo")?.takeUnless { it.isJsonNull }?.asInt ?: return null
    return routine.rows("items").filter { it.text("splitDayNo").toIntOrNull() == day }
}

@Composable internal fun ServerWorkoutPreparation(modifier: Modifier, api: FitMateApiClient, routineId: String,
    back: () -> Unit, start: (Boolean) -> Unit) {
    var routine by remember(routineId) { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var samsung by rememberSaveable(routineId) { mutableStateOf(false) }
    LaunchedEffect(routineId, reload) {
        routine = null; error = null
        try { routine = withContext(Dispatchers.IO) { api.request("GET", "routines/$routineId").asJsonObject } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "오늘의 운동을 불러오지 못했습니다." }
    }
    val items = routine?.let(::nextRoutineItems)
    val day = routine?.text("nextSplitDayNo").orEmpty()
    PlanTheme { PlanPage(modifier, "운동 준비", back, footer = {
        PlanAction(if (day.isBlank()) "운동 시작" else "${day}일차 운동 시작", enabled = !items.isNullOrEmpty()) { start(samsung) }
    }) {
        if (routine == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { ErrorText(it); TextButton({ reload++ }) { Text("다시 불러오기") } }
        routine?.let { value ->
            PlanBadge("${value.text("splitCount")}일 분할")
            PlanHeading(if (items == null) "오늘의 운동 확인" else "준비되셨나요?", "${value.text("name")} · ${day}일차")
            if (items.isNullOrEmpty()) {
                Text("오늘 진행할 운동을 확인할 수 없습니다. 다시 불러와 주세요.")
                TextButton({ reload++ }) { Text("다시 불러오기") }
            } else {
                PlanSummary {
                    Text("전체 ${value.rows("items").size}종목 중 오늘의 ${items.size}종목", fontWeight = FontWeight.SemiBold)
                    Text("${items.sumOf { it.text("targetSets").toIntOrNull() ?: 1 }}세트 · 완료하면 다음 일차로 이어져요.", style = MaterialTheme.typography.bodySmall)
                }
                PlanSummary {
                    items.forEachIndexed { index, item -> PlanExerciseRow(index, item.text("exerciseName"), routineTargetLabel(item)) }
                }
                PlanSummary {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Samsung Health와 함께 기록", style = MaterialTheme.typography.titleSmall)
                            Text("이번 운동에만 선택해요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(samsung, { samsung = it })
                    }
                }
                PlanDisclosure("시작 전 확인") { Text("가볍게 몸을 풀고 시작하세요. 통증이 느껴지면 운동을 중단하고 쉬세요.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    } }
}
