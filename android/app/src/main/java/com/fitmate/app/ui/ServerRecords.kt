package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable internal fun ServerRecordList(
    api: FitMateApiClient,
    samsungHealthState: SamsungHealthUiState = SamsungHealthUiState(),
    loadSamsungWorkouts: (java.time.Instant, java.time.Instant, (SamsungHealthUiState) -> Unit) -> Unit = { _, _, _ -> },
    requestSamsungExercisePermission: () -> Unit = {},
) {
    val records = remember(api) { ServerRecordApi(api) }
    var sport by remember { mutableStateOf("전체") }
    var rows by remember { mutableStateOf(emptyList<JsonObject>()) }
    var cursor by remember { mutableStateOf("0") }
    var next by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedSport by remember { mutableStateOf("HEALTH") }
    LaunchedEffect(sport, cursor, refresh) {
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                val code = if (sport == "헬스") "HEALTH" else com.fitmate.app.data.model.Sport.entries.firstOrNull { it.label == sport }?.name
                records.page("workout-sessions?status=COMPLETED" + (code?.let { "&sportCode=$it" } ?: ""), cursor)
            }
            val items = result.rows("items")
            rows = (if (cursor == "0") items else rows + items).distinctBy { it.text("id") }
            next = result.text("nextCursor").takeIf { result.get("hasNext")?.asBoolean == true && it.isNotBlank() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "기록을 불러오지 못했어요."
        } finally { loading = false }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton({ cursor = "0"; refresh++ }, enabled = !loading) { Text("새로고침") }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("전체", "헬스", "러닝") + additionalSports.map { it.label }).forEach { label -> FilterChip(sport == label, {
            if (sport != label) { sport = label; rows = emptyList(); cursor = "0"; next = null; selected = null }
        }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp), enabled = !loading) }
    }
    Text("서버에 저장된 내 운동 기록", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton({ refresh++ }) { Text("다시 시도") } }
    if (!loading && error == null && rows.isEmpty()) WorkoutCard {
        Text("저장된 $sport 기록이 없어요.", fontWeight = FontWeight.SemiBold)
        Text("운동을 마치고 저장하면 여기에 모여요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    rows.forEach { row ->
        val code = row.text("sportCode")
        OutlinedCard(onClick = { selectedSport = code; selected = row.text("id") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(recordDate(row.recordTime("endedAt")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (code == "HEALTH") {
                    WorkoutSportLabel(false, row.text("routineName").ifBlank { "헬스 운동" },
                        "운동 ${row.recordInt("exerciseCount")}종 · ${row.recordInt("completedSetCount")}세트")
                } else {
                    Text(sportLabel(code), style = MaterialTheme.typography.titleLarge)
                    Text("완료한 운동 · 상세 기록 보기", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (next != null && error == null) WorkoutSecondaryAction("더 보기", enabled = !loading) { cursor = requireNotNull(next) }
    selected?.let { id -> SocialSheet({ selected = null }) {
        if (additionalSports.any { it.name == selectedSport }) SportRecordDetail(api, id)
        else ServerRecordDetail(api, id, selectedSport == "RUNNING", samsungHealthState, loadSamsungWorkouts, requestSamsungExercisePermission)
    } }
}

@Composable internal fun ServerRecordDetail(
    api: FitMateApiClient,
    id: String,
    running: Boolean = false,
    samsungHealthState: SamsungHealthUiState = SamsungHealthUiState(),
    loadSamsungWorkouts: (java.time.Instant, java.time.Instant, (SamsungHealthUiState) -> Unit) -> Unit = { _, _, _ -> },
    requestSamsungExercisePermission: () -> Unit = {},
    samsungSelected: Boolean? = null,
) {
    var detail by remember(id) { mutableStateOf<JsonObject?>(null) }
    var session by remember(id) { mutableStateOf<JsonObject?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var deleting by remember(id) { mutableStateOf(false) }
    var deleted by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api, id, running, attempt) {
        error = null; detail = null
        try {
            val result = withContext(Dispatchers.IO) {
                val records = ServerRecordApi(api)
                (if (running) records.run(id) else records.report(id)) to records.session(id)
            }
            detail = result.first; session = result.second
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "리포트를 불러오지 못했어요."
        }
    }
    Text(if (running) "러닝 리포트" else "헬스 리포트", style = MaterialTheme.typography.titleLarge)
    if (detail == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton({ attempt++ }) { Text("다시 시도") } }
    if (deleted) Text("러닝 기록과 비공개 경로를 삭제했습니다.", color = MaterialTheme.colorScheme.primary)
    detail?.let { row ->
        Text(recordDate(row.recordTime(if (running) "completedAt" else "endedAt")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("운동 완료", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        if (running) {
            RunningRecordReport(row)
            OutlinedButton({
                if (!deleting) {
                    deleting = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { ServerRecordApi(api).deleteRun(id) }
                            detail = null
                            deleted = true
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            error = failure.message ?: "러닝 기록을 삭제하지 못했어요."
                        } finally {
                            deleting = false
                        }
                    }
                }
            }, enabled = !deleting) { Text(if (deleting) "삭제 중…" else "러닝 기록 삭제") }
        } else {
            WorkoutInsightEntry(api, id)
            WorkoutHeading("오늘의 운동 기록", "FitMate에 기록한 운동 정보입니다.")
            WorkoutCard {
                WorkoutMetrics("운동 시간" to "${row.recordInt("durationSeconds")}초",
                    "완료 세트" to "${row.recordInt("completedSetCount")}세트", "총 볼륨" to "${row.text("totalVolumeKg")} kg")
            }
            Text("운동별 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            row.rows("exercises").forEach { exercise ->
                WorkoutCard {
                Text(exercise.text("exerciseName"), fontWeight = FontWeight.SemiBold)
                Text("${exercise.recordInt("completedSetCount")}세트 · ${exercise.recordInt("totalRepetitions")}회 · ${exercise.text("totalVolumeKg")}kg")
                val previous = exercise.get("previousComparison")?.takeUnless { it.isJsonNull }?.asJsonObject
                Text(previous?.let { "이전 동일 운동 대비 볼륨 ${it.text("volumeChangeKg")}kg" } ?: "비교할 이전 기록 없음")
                }
            }
            Text("세트별 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            session?.rows("exercises")?.forEach { exercise ->
                exercise.rows("sets").filter { it.get("completed")?.asBoolean == true }.forEach { set ->
                    WorkoutCard {
                    Text("${exercise.text("exerciseName")} · ${set.recordInt("setNo")}세트", fontWeight = FontWeight.SemiBold)
                    Text("${set.text("weightKg").ifBlank { "—" }}kg × ${set.recordInt("repetitions") ?: "—"}회 · RPE ${set.recordInt("rpe") ?: "—"}")
                    if (set.text("memo").isNotBlank()) Text(set.text("memo"))
                    }
                }
            }
        }
        val serverSession = session
        val startedAt = serverSession?.recordTime("startedAt")?.let(java.time.Instant::ofEpochMilli)
        val endedAt = serverSession?.recordTime("endedAt")?.let(java.time.Instant::ofEpochMilli)
        val sessionExerciseNames = if (running) emptyList() else (
            row.rows("exercises").map { it.text("exerciseName") } +
                serverSession?.rows("exercises").orEmpty().map { it.text("exerciseName") }
            ).filter { it.isNotBlank() }.distinct()
        if (samsungSelected == false) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samsung Health 미사용", fontWeight = FontWeight.SemiBold)
                    Text("이번 리포트는 FitMate에 직접 기록한 운동 정보만으로 구성됐어요.")
                }
            }
        } else if (startedAt != null && endedAt != null) {
            ExternalWorkoutSection(
                api = api,
                sessionId = id,
                sportCode = if (running) "RUNNING" else "HEALTH",
                sessionExerciseNames = sessionExerciseNames,
                sessionStartedAt = startedAt,
                sessionEndedAt = endedAt,
                sessionActiveDurationSeconds = row.recordInt(
                    if (running) "activeDurationSeconds" else "durationSeconds",
                )?.toLong(),
                samsungState = samsungHealthState,
                loadWorkouts = loadSamsungWorkouts,
                requestPermission = requestSamsungExercisePermission,
            )
        }
    }
}
