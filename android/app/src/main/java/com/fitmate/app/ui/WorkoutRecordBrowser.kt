package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.ui.theme.FitMateColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun recordMonth(time: Long?) = time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM")) } ?: "날짜 없음"
internal fun recordDate(time: Long?) = time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")) } ?: "날짜 없음 · 기존 기록"
internal data class RecordReference(val sport: String, val index: Int, val time: Long?)
internal fun recordTimeline(workouts: List<WorkoutSummary>, runs: List<RunResult>) =
    (workouts.mapIndexed { i, row -> RecordReference("헬스", i, row.completedAt) } +
        runs.mapIndexed { i, row -> RecordReference("러닝", i, row.completedAt) }).sortedByDescending { it.time ?: Long.MIN_VALUE }

internal val PreviewRun = RunResult(5000, 1800, listOf(370, 365, 360, 355, 350).mapIndexed { i, seconds ->
    RunLap(i + 1, 1000, seconds)
}, "거리", 5000, 6, 4, "무리하지 않고 일정하게 달렸어요.")

@Composable internal fun WorkoutRecordBrowser(workouts: List<WorkoutSummary>, runs: List<RunResult>, allowExamples: Boolean = true) {
    var examples by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("전체") }
    var month by rememberSaveable { mutableStateOf("전체 기간") }
    var monthsOpen by remember { mutableStateOf(false) }
    var gym by remember { mutableStateOf<Int?>(null) }
    var run by remember { mutableStateOf<RunResult?>(null) }
    val gymRows = if (examples) PreviewWorkouts else workouts
    val runRows = if (examples) listOf(PreviewRun) else runs
    if (allowExamples) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (examples) "예시 기록" else "내 운동 기록", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton({ examples = !examples; gym = null; run = null; month = "전체 기간" }) { Text(if (examples) "내 기록으로" else "예시 보기") }
    }
    if (examples) Text("디자인 확인용 · 실제 기록과 통계에는 포함되지 않아요.", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("전체", "헬스", "러닝").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) }
    }
    val timeline = recordTimeline(gymRows, runRows)
    Box {
        TextButton({ monthsOpen = true }) { Text("$month ▾") }
        FitMateMenuSurface(monthsOpen, { monthsOpen = false }, Modifier.width(156.dp)) {
            (listOf("전체 기간") + timeline.map { recordMonth(it.time) }.distinct()).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.labelLarge) },
                    onClick = { month = option; monthsOpen = false },
                    modifier = Modifier.height(38.dp),
                    colors = MenuDefaults.itemColors(textColor = FitMateColors.TextPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                )
            }
        }
    }
    val visible = timeline.filter { (filter == "전체" || it.sport == filter) && (month == "전체 기간" || recordMonth(it.time) == month) }
    val count = visible.size
    Text("${count}개의 기록 · 카드를 눌러 상세 확인", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
    if (count == 0) Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.FitnessCenter, null)
        Text("아직 ${if (filter == "전체") "운동" else filter} 기록이 없어요", fontWeight = FontWeight.SemiBold)
        Text("운동을 마치고 저장하면 여기에 모여요.", style = MaterialTheme.typography.bodySmall)
    }
    visible.forEach { row ->
        if (row.sport == "헬스") {
            val index = row.index
            val item = gymRows[index]
            RecordCard("헬스", item.sets.map { it.exerciseName }.distinct().joinToString(" · ").ifBlank { "헬스 기록" },
                "${item.durationMinutes}분", "${item.sets.size}세트", "${item.totalVolumeKg.toInt()}kg 볼륨", examples, row.time) { gym = index }
        } else {
            val item = runRows[row.index]
            RecordCard("러닝", "${item.goal} 목표 러닝", "${"%.2f".format(item.meters / 1000.0)}km", "${item.seconds / 60}분", runPace(item.seconds, item.meters), examples, row.time) { run = item }
        }
    }
    gym?.let { index -> gymRows.getOrNull(index)?.let { item -> SocialSheet({ gym = null }) {
        Text("헬스 기록${if (examples) " · 예시" else ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(recordDate(item.completedAt), style = MaterialTheme.typography.bodySmall)
        RecordMetrics("${item.durationMinutes}분", "${item.sets.size}세트", "${item.totalVolumeKg.toInt()}kg 볼륨")
        HorizontalDivider()
        item.sets.groupBy { it.exerciseName }.forEach { (name, sets) ->
            Text(name, style = MaterialTheme.typography.titleMedium)
            sets.forEachIndexed { i, set ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${i + 1}세트", color = FitMateColors.TextSecondary)
                    Text("${set.weightKg}kg × ${set.repetitions}회")
                    Text("RPE ${set.rpe ?: "—"}", color = FitMateColors.TextSecondary)
                }
                if (set.memo.isNotBlank()) Text(set.memo, style = MaterialTheme.typography.bodySmall)
            }
            val prior = gymRows.drop(index + 1).firstOrNull { old -> old.sets.any { it.exerciseName == name } }?.sets?.filter { it.exerciseName == name }
            Text(if (prior == null) "이전 동일 운동 기록 없음" else "이전 대비 볼륨 ${"%+.0f".format(sets.sumOf { it.weightKg * it.repetitions } - prior.sumOf { it.weightKg * it.repetitions })}kg", style = MaterialTheme.typography.bodySmall, color = FitMateColors.WorkoutAction)
            HorizontalDivider()
        }
    } } }
    run?.let { item -> SocialSheet({ run = null }) {
        Text("러닝 기록${if (examples) " · 예시" else ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(recordDate(item.completedAt), style = MaterialTheme.typography.bodySmall)
        RecordMetrics("${"%.2f".format(item.meters / 1000.0)}km", "${item.seconds / 60}분", runPace(item.seconds, item.meters))
        Text("RPE ${item.rpe} · 컨디션 ${item.condition}/5")
        if (item.memo.isNotBlank()) Text(item.memo)
        HorizontalDivider()
        Text("구간 기록", style = MaterialTheme.typography.titleMedium)
        if (item.laps.isEmpty()) Text("저장된 구간 기록이 없어요.")
        item.laps.forEach { lap -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${lap.number}구간 · ${lap.meters}m")
            Text(runPace(lap.seconds, lap.meters))
        } }
    } }
}

@Composable private fun RecordCard(sport: String, title: String, first: String, second: String, third: String, example: Boolean, time: Long?, open: () -> Unit) {
    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.outlinedCardColors(containerColor = FitMateColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$sport · ${if (example && time == null) "디자인 예시" else recordDate(time)}", style = MaterialTheme.typography.bodySmall, color = FitMateColors.TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (sport == "헬스") Icons.Outlined.FitnessCenter else Icons.AutoMirrored.Outlined.DirectionsRun, null, Modifier.size(20.dp), tint = FitMateColors.WorkoutAction)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (example) Text("예시", style = MaterialTheme.typography.labelSmall, color = FitMateColors.TextSecondary)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "기록 상세", Modifier.size(20.dp))
            }
            RecordMetrics(first, second, third)
        }
    }
}

@Composable private fun RecordMetrics(first: String, second: String, third: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(first, second, third).forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
    }
}
