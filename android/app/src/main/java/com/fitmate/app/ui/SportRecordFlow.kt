package com.fitmate.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun sportIcon(code: String): ImageVector = when (code) {
    "RUNNING" -> Icons.AutoMirrored.Outlined.DirectionsRun
    "BADMINTON" -> Icons.Outlined.SportsTennis
    "CLIMBING" -> Icons.Outlined.Landscape
    "HIKING" -> Icons.Outlined.Terrain
    "GOLF" -> Icons.Outlined.GolfCourse
    else -> Icons.Outlined.FitnessCenter
}
internal fun sportTime(seconds: Long) = "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

@Composable internal fun SportRecordFlow(
    modifier: Modifier, api: FitMateApiClient, state: SportRecordState, boot: Int,
    persist: () -> Boolean, home: () -> Unit, history: () -> Unit,
) {
    val draft = state.draft
    var saved by remember { mutableStateOf<JsonObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var validate by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<JsonObject?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(draft?.anchor) { while (draft?.anchor != null) { now = SystemClock.elapsedRealtime(); delay(250) } }
    BackHandler { if (!busy) home() }
    fun save() {
        if (busy || draft == null) return
        validate = true; error = null
        val body = try { draft.pending?.let { JsonParser.parseString(it).asJsonObject } ?: draft.payload() }
        catch (failure: Exception) { error = failure.message; return }
        state.draft = draft.copy(pending = body.toString())
        // Persist the exact request before sending: process death must not create a second ID.
        if (!persist()) { error = "초안을 보관하지 못했어요. 저장 공간을 확인하고 다시 시도해주세요."; return }
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    try { api.request("POST", "sport-records", body).asJsonObject }
                    catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        if (failure is ApiFailure && failure.status in listOf(400, 401, 403, 422)) throw failure
                        val recovered = try { api.request("GET", "sport-records/${body.text("clientRecordId")}").asJsonObject }
                        catch (lookup: Exception) { if (lookup is CancellationException) throw lookup; throw failure }
                        if (!sportRecordMatches(body, recovered)) throw ApiFailure(409, "같은 ID에 다른 내용의 기록이 있어요. 기존 기록을 확인해주세요.")
                        recovered
                    }
                }
                check(sportRecordMatches(body, result)) { "서버의 저장 결과가 요청과 달라요. 다시 확인해주세요." }
                state.draft = null
                if (!persist()) error = "저장은 완료했지만 기기 초안 정리에 실패했어요. 다시 실행해도 같은 기록으로 확인됩니다."
                saved = result
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is ApiFailure && failure.status in listOf(400, 422)) { state.draft = state.draft?.copy(pending = null); persist() }
                conflict = failure is ApiFailure && failure.status == 409
                error = failure.message ?: "저장을 확인하지 못했어요. 같은 기록으로 다시 시도해주세요."
            } finally { busy = false }
        }
    }
    WorkoutJourneyPage(modifier.imePadding()) {
        saved?.let { row ->
            Text("기록 저장 완료", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            SportRecordSummary(row)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            WorkoutAction("운동 기록 보기", onClick = history)
            WorkoutSecondaryAction("홈으로", onClick = home)
            return@WorkoutJourneyPage
        }
        if (draft == null) { WorkoutAction("홈으로", onClick = home); return@WorkoutJourneyPage }
        val locked = busy || draft.pending != null
        val fieldsError = if (validate) draft.errors() else emptyMap()
        PlanHeading("${sportLabel(draft.sport)} ${when(draft.phase) { "준비" -> "준비"; "진행" -> "진행 중"; else -> "운동 결과" }}",
            when(draft.phase) { "준비" -> "나만의 페이스로, 오늘의 움직임을 기록해요."; "진행" -> "쉬는 시간은 운동 시간에서 제외해요."; else -> "오늘의 기록을 확인하고 저장해주세요." })
        if (state.restoreNotice) Text("기기가 재시작되어 마지막 보관 시간에서 일시정지했어요.", color = MaterialTheme.colorScheme.primary)
        if (draft.phase == "진행") {
            SportTimer(draft.sport, draft.anchor != null, draft.millis(now) / 1000)
            Text(sportTypes(draft.sport)[draft.type].orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text("총 경과 시간 ${sportTime(((System.currentTimeMillis() - requireNotNull(draft.started)) / 1000).coerceAtLeast(0))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            WorkoutAction(if (draft.anchor == null) "다시 시작" else "일시정지") {
                if (draft.anchor == null) state.resume(SystemClock.elapsedRealtime()) else state.pause(SystemClock.elapsedRealtime())
                persist()
            }
            WorkoutSecondaryAction("운동 종료") { state.finish(System.currentTimeMillis(), SystemClock.elapsedRealtime()); persist() }
        } else {
            WorkoutCard {
                Text("운동 유형", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sportTypes(draft.sport).forEach { (code, label) -> FilterChip(draft.type == code, {
                        state.draft = draft.changeType(code); validate = false
                    }, label = { Text(label) }, enabled = !locked) }
                }
                OutlinedTextField(draft.place, { state.draft = draft.copy(place = it.take(100)) }, Modifier.fillMaxWidth(),
                    label = { Text("장소 (선택)") }, enabled = !locked)
            }
            if (draft.phase == "준비") {
                WorkoutCard { Text("오늘의 작은 움직임도 소중한 기록이에요."); Text("종료 후 결과를 추가할 수 있어요. 시간만 기록해도 괜찮아요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                WorkoutAction("${sportLabel(draft.sport)} 시작") { state.start(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot); persist() }
            } else {
                WorkoutCard {
                    WorkoutMetric("실제 운동 시간", sportTime(draft.accumulated / 1000))
                    Text("총 경과 시간 ${sportTime(((draft.ended ?: 0) - (draft.started ?: 0)) / 1000)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(recordDate(draft.started), style = MaterialTheme.typography.bodySmall)
                }
                Text("운동 결과 · 선택 입력", style = MaterialTheme.typography.titleMedium)
                Text("비워두면 시간만 저장돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                sportFields(draft.sport, draft.type).forEach { field ->
                    OutlinedTextField(draft.fields[field.key].orEmpty(), { state.draft = draft.copy(fields = draft.fields + (field.key to it.take(if (field.text) 100 else 16))) },
                        Modifier.fillMaxWidth(), label = { Text(field.label) }, enabled = !locked, isError = fieldsError.containsKey(field.key),
                        supportingText = fieldsError[field.key]?.let { message -> { Text(message) } },
                        singleLine = true, keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = if (field.text) KeyboardType.Text else if (field.key == "distanceMeters") KeyboardType.Decimal else KeyboardType.Number))
                }
                OutlinedTextField(draft.memo, { state.draft = draft.copy(memo = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text("오늘의 메모 (선택)") }, enabled = !locked)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (draft.pending != null) Text("저장 결과를 확인할 때까지 입력을 보관하고 있어요. 재시도해도 같은 기록으로 저장돼요.", style = MaterialTheme.typography.bodySmall)
                WorkoutAction(if (busy) "저장 확인 중…" else if (draft.pending != null) "같은 기록으로 다시 시도" else "기록 저장하고 종료", !busy, ::save)
                if (!locked) WorkoutSecondaryAction("운동 계속하기") {
                    state.draft = draft.copy(phase = "진행", ended = null, anchor = SystemClock.elapsedRealtime())
                    error = null; persist()
                }
                if (conflict) TextButton({
                    busy = true
                    scope.launch {
                        try { existing = withContext(Dispatchers.IO) { api.request("GET", "sport-records/${draft.id}").asJsonObject } }
                        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text("기존 서버 기록 확인") }
            }
        }
        if (draft.pending == null) TextButton({ cancel = true }, enabled = !busy) { Text("이번 운동 취소") }
        TextButton(home, enabled = !busy) { Text("홈으로 · 나중에 이어하기") }
    }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("이번 운동을 취소할까요?") },
        text = { Text("이 기기에 보관된 초안만 지워요. 운동 기록은 저장되지 않아요.") },
        confirmButton = { TextButton({ state.draft = null; persist(); cancel = false; home() }) { Text("초안 취소") } },
        dismissButton = { TextButton({ cancel = false }) { Text("계속하기") } })
    existing?.let { row -> SocialSheet({ existing = null }) {
        Text("기존 서버 기록", style = MaterialTheme.typography.titleLarge); SportRecordSummary(row)
        Text("현재 입력과 다른 기록입니다. 덮어쓰지 않았어요.")
        WorkoutSecondaryAction("기존 기록 유지하고 초안 닫기") { state.draft = null; persist(); existing = null; history() }
    } }
}

@Composable private fun SportTimer(code: String, running: Boolean, seconds: Long) {
    val transition = rememberInfiniteTransition(label = "sport-timer")
    val pulse by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breathing")
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(Modifier.size(156.dp).scale(if (running) pulse else 1f), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(sportIcon(code), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Text(if (running) "지금 움직이는 중" else "잠시 쉬는 중", color = MaterialTheme.colorScheme.primary)
        Text(sportTime(seconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun SportRecordSummary(row: JsonObject) {
    Text("${sportLabel(row.text("sportCode"))} · ${sportTypes(row.text("sportCode"))[row.text("activityType")].orEmpty()}", style = MaterialTheme.typography.titleLarge)
    Text(recordDate(row.recordTime("startedAt")), color = MaterialTheme.colorScheme.onSurfaceVariant)
    WorkoutCard {
        WorkoutMetric("실제 운동 시간", sportTime(row.recordInt("activeDurationSeconds")?.toLong() ?: 0))
        Text("총 경과 시간 ${sportTime(row.get("elapsedDurationSeconds")?.asLong ?: 0)}")
        if (row.text("placeName").isNotBlank()) Text("장소 · ${row.text("placeName")}")
        sportFields(row.text("sportCode"), row.text("activityType")).forEach { f ->
            if (row.text(f.key).isNotBlank()) Text("${f.label} · ${if (f.key == "distanceMeters") row.get(f.key).asBigDecimal.movePointLeft(3).stripTrailingZeros().toPlainString() else row.text(f.key)}")
        }
        if (row.text("memo").isNotBlank()) Text(row.text("memo"))
    }
}

@Composable internal fun SportRecordDetail(api: FitMateApiClient, id: String) {
    var row by remember(id) { mutableStateOf<JsonObject?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(api, id, retry) {
        error = null
        try { row = withContext(Dispatchers.IO) { api.request("GET", "sport-records/$id").asJsonObject } }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "기록을 불러오지 못했어요." }
    }
    row?.let { SportRecordSummary(it) } ?: if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error); TextButton({ retry++ }) { Text("다시 시도") }
    }
}
