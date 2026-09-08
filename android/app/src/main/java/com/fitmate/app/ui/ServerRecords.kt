package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ServerRecordList(api: FitMateApiClient) {
    val records = remember(api) { ServerRecordApi(api) }
    var sport by remember { mutableStateOf("헬스") }
    var rows by remember { mutableStateOf(emptyList<JsonObject>()) }
    var cursor by remember { mutableStateOf("0") }
    var next by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sport, cursor, refresh) {
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                records.page(if (sport == "러닝") "running-records" else "workout-sessions?status=COMPLETED", cursor)
            }
            val items = result.rows("items").filter { sport == "러닝" || it.text("sportCode") == "HEALTH" }
            rows = (if (cursor == "0") items else rows + items).distinctBy { it.text("id") }
            next = result.text("nextCursor").takeIf { result.get("hasNext")?.asBoolean == true && it.isNotBlank() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "기록을 불러오지 못했어요."
        } finally { loading = false }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("헬스", "러닝").forEach { label -> FilterChip(sport == label, {
            if (sport != label) { sport = label; rows = emptyList(); cursor = "0"; next = null; selected = null }
        }, label = { Text(label) }, enabled = !loading) }
        TextButton({ cursor = "0"; refresh++ }, enabled = !loading) { Text("새로고침") }
    }
    Text("서버에 저장된 내 운동 기록", style = MaterialTheme.typography.bodySmall)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton({ refresh++ }) { Text("다시 시도") } }
    if (!loading && error == null && rows.isEmpty()) Text("저장된 $sport 기록이 없어요.")
    rows.forEach { row ->
        OutlinedCard(onClick = { selected = row.text("id") }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recordDate(row.recordTime(if (sport == "러닝") "completedAt" else "endedAt")), style = MaterialTheme.typography.bodySmall)
                if (sport == "러닝") {
                    Text("${"%.2f".format((row.recordInt("distanceMeters") ?: 0) / 1000.0)} km", fontWeight = FontWeight.SemiBold)
                    Text("${row.recordInt("activeDurationSeconds") ?: 0}초 · ${row.recordInt("averagePaceSecondsPerKm")?.let { "%d:%02d /km".format(it / 60, it % 60) } ?: "페이스 없음"}")
                } else {
                    Text(row.text("routineName").ifBlank { "헬스 운동" }, fontWeight = FontWeight.SemiBold)
                    Text("${row.recordInt("exerciseCount")}종 · ${row.recordInt("completedSetCount")}세트")
                }
            }
        }
    }
    if (next != null && error == null) OutlinedButton({ cursor = requireNotNull(next) }, enabled = !loading) { Text("더 보기") }
    selected?.let { id -> SocialSheet({ selected = null }) { ServerRecordDetail(api, id, sport == "러닝") } }
}

@Composable internal fun ServerRecordDetail(api: FitMateApiClient, id: String, running: Boolean = false) {
    var detail by remember(id) { mutableStateOf<JsonObject?>(null) }
    var session by remember(id) { mutableStateOf<JsonObject?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(api, id, running, attempt) {
        error = null; detail = null
        try {
            val result = withContext(Dispatchers.IO) {
                val records = ServerRecordApi(api)
                if (running) records.run(id) to null else records.report(id) to records.session(id)
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
    detail?.let { row ->
        Text(recordDate(row.recordTime(if (running) "completedAt" else "endedAt")))
        if (running) {
            Text("${row.recordInt("distanceMeters")} m · ${row.recordInt("activeDurationSeconds")}초")
            Text("평균 페이스 ${runPace(row.recordInt("activeDurationSeconds") ?: 0, row.recordInt("distanceMeters") ?: 0)}")
            Text("RPE ${row.recordInt("rpe") ?: "—"} · 컨디션 ${row.recordInt("conditionScore") ?: "—"}/5")
            if (row.text("memo").isNotBlank()) Text(row.text("memo"))
            row.get("goalAchieved")?.takeUnless { it.isJsonNull }?.let { Text(if (it.asBoolean) "목표 달성" else "목표 미달성") }
            row.rows("laps").forEach { Text("${it.recordInt("lapNo")}랩 · ${it.recordInt("distanceMeters")}m · ${it.recordInt("durationSeconds")}초") }
        } else {
            Text("${row.recordInt("durationSeconds")}초 · ${row.recordInt("completedSetCount")}세트 · ${row.text("totalVolumeKg")}kg 볼륨")
            row.rows("exercises").forEach { exercise ->
                HorizontalDivider()
                Text(exercise.text("exerciseName"), fontWeight = FontWeight.SemiBold)
                Text("${exercise.recordInt("completedSetCount")}세트 · ${exercise.recordInt("totalRepetitions")}회 · ${exercise.text("totalVolumeKg")}kg")
                val previous = exercise.get("previousComparison")?.takeUnless { it.isJsonNull }?.asJsonObject
                Text(previous?.let { "이전 동일 운동 대비 볼륨 ${it.text("volumeChangeKg")}kg" } ?: "비교할 이전 기록 없음")
            }
            session?.rows("exercises")?.forEach { exercise ->
                exercise.rows("sets").filter { it.get("completed")?.asBoolean == true }.forEach { set ->
                    Text("${exercise.text("exerciseName")} · ${set.recordInt("setNo")}세트 · ${set.text("weightKg").ifBlank { "—" }}kg × ${set.recordInt("repetitions") ?: "—"}회 · RPE ${set.recordInt("rpe") ?: "—"}")
                    if (set.text("memo").isNotBlank()) Text(set.text("memo"))
                }
            }
        }
    }
}
