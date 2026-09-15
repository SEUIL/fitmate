package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal fun workoutInsightPayload(id: String, inbody: String?, samsung: Boolean, consent: Boolean, version: String): Map<String, Any> = buildMap {
    put("requestId", UUID.fromString(id).toString())
    inbody?.let { put("inbodyRecordId", UUID.fromString(it).toString()) }
    put("includeSamsungHealth", samsung); put("externalAiConsent", consent)
    if (consent) { require(version.isNotBlank()); put("consentVersion", version) }
}

@Composable internal fun WorkoutInsightEntry(api: FitMateApiClient, sessionId: String) {
    var open by rememberSaveable(sessionId) { mutableStateOf(false) }
    PlanSummary {
        Text("이번 운동, 어떻게 이어갈까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("운동 기록과 선택한 정보를 바탕으로 다음 운동에서 확인할 점을 정리해요.", style = MaterialTheme.typography.bodyMedium)
        PlanAction("운동 리포트 해석 보기") { open = true }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WorkoutInsightFlow(Modifier.fillMaxSize().systemBarsPadding(), api, sessionId) { open = false }
    }
}

@Composable internal fun WorkoutInsightFlow(modifier: Modifier, api: FitMateApiClient, sessionId: String, back: () -> Unit) {
    val root = "workout-sessions/$sessionId/insights"
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<JsonObject?>(null) }
    var report by remember { mutableStateOf<JsonObject?>(null) }
    var records by remember { mutableStateOf(emptyList<JsonObject>()) }
    var inbodyId by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var inbodyDate by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var samsung by rememberSaveable(sessionId) { mutableStateOf(false) }
    var consent by rememberSaveable(sessionId) { mutableStateOf(false) }
    var editing by rememberSaveable(sessionId) { mutableStateOf(false) }
    var requestJson by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val gson = remember { Gson() }
    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "처리하지 못했어요. 연결을 확인하고 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
    LaunchedEffect(api, sessionId, reload) {
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                api.request("GET", "$root/options").asJsonObject to api.request("GET", root)
            }
            options = result.first
            if (result.first.get("samsungHealthAvailable")?.asBoolean != true && samsung) {
                samsung = false; requestJson = null
            }
            report = result.second.takeUnless { it.isJsonNull }?.asJsonObject
        } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "리포트를 불러오지 못했어요. 다시 시도해 주세요." }
        finally { loading = false }
    }
    val generating = report?.text("status") == "GENERATING"
    LaunchedEffect(report?.text("id"), generating, reload) {
        if (!generating) return@LaunchedEffect
        val id = report!!.text("id")
        try {
            repeat(100) {
                delay(1500)
                val next = withContext(Dispatchers.IO) { api.request("GET", "$root/$id").asJsonObject }
                report = next
                if (next.text("status") != "GENERATING") return@LaunchedEffect
            }
            error = "분석 상태를 다시 확인해 주세요. 요청은 서버에 보존됩니다."
        } catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "분석 상태를 확인하지 못했어요. 다시 불러오면 이어서 확인할 수 있어요." }
    }
    val result = report?.get("result")?.takeUnless { it.isJsonNull }?.asJsonObject
    val showConditions = !generating && (editing || report == null)
    val available = options?.get("aiAvailable")?.asBoolean == true
    val samsungAvailable = options?.get("samsungHealthAvailable")?.asBoolean == true
    fun generate() = launchAction {
        if (requestJson == null) requestJson = gson.toJson(workoutInsightPayload(UUID.randomUUID().toString(), inbodyId, samsung, consent && available, options!!.text("consentVersion")))
        report = withContext(Dispatchers.IO) { api.request("POST", root, gson.fromJson(requestJson, JsonObject::class.java)).asJsonObject }
        editing = false
    }
    PlanTheme {
        PlanPage(modifier, "운동 리포트", back, footer = {
            when {
                loading -> Unit
                generating -> TextButton({ launchAction { report = withContext(Dispatchers.IO) { api.request("POST", "$root/${report!!.text("id")}/cancel").asJsonObject } } }, enabled = !busy) { Text("분석 취소") }
                showConditions -> PlanAction(if (consent && available) "AI 해석 받기" else "기본 해석 보기", !busy && options != null, ::generate)
                else -> PlanAction("정보 선택 후 다시 분석", !busy) { editing = true; requestJson = null; consent = false }
            }
        }) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton({ reload++ }, enabled = !busy) { Text("다시 불러오기") } }
            when {
                generating -> {
                    PlanHeading("운동 기록을\n살펴보고 있어요", "기록 확인 → 종합 설명 → 근거 검토")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("화면을 나가도 분석 상태는 보존돼요. 다시 열면 이어서 확인할 수 있어요.")
                    Text("취소해도 이미 외부 AI에 전송한 요청은 회수할 수 없어요.", style = MaterialTheme.typography.bodySmall)
                }
                showConditions && !loading -> {
                    PlanHeading("오늘의 기록을\n다음 운동으로", "기본 운동 기록은 그대로 유지돼요.")
                    PlanSummary { Text("이번 운동 + 프로필 목표·숙련도", fontWeight = FontWeight.SemiBold); Text("이전 동일 운동 기록이 있으면 함께 비교해요.") }
                    PlanDisclosure("인바디 · ${if (inbodyId == null) "사용 안 함" else inbodyDate ?: "선택한 기록"}") {
                        TextButton({ inbodyId = null; inbodyDate = null; requestJson = null }, enabled = !busy) { Text("인바디 사용 안 함") }
                        TextButton({ launchAction { records = withContext(Dispatchers.IO) { api.items("inbody-records") }; if (records.isEmpty()) error = "저장한 인바디가 없어요. 인바디 없이 진행할 수 있어요." } }, enabled = !busy) { Text("내 인바디 불러오기") }
                        records.forEach { row -> Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(inbodyId == row.text("id"), { inbodyId = row.text("id"); inbodyDate = row.text("measuredDate"); requestJson = null }, enabled = !busy)
                            Text(row.text("measuredDate"))
                        } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(samsung, { samsung = it; requestJson = null }, enabled = !busy && samsungAvailable)
                        Column { Text("연결한 Samsung Health 요약 사용"); Text(if (samsungAvailable) "이 운동에 연결해 저장한 요약만 사용해요." else "연결된 요약이 없어요. 운동 기록에서 먼저 연결해 주세요.", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (available) {
                        Text(options!!.text("consentNotice"), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(consent, { consent = it; requestJson = null }, enabled = !busy)
                            Text("선택한 정보의 외부 AI 전송에 동의해요")
                        }
                    } else Text("현재 기본 해석을 이용할 수 있어요. 외부 AI에는 전송하지 않아요.", style = MaterialTheme.typography.bodySmall)
                }
                result != null && !editing -> WorkoutInsightResultView(result, report!!.text("message"))
                !loading -> {
                    PlanHeading(if (report?.text("status") == "CANCELED") "분석을 취소했어요" else "분석을 완료하지 못했어요")
                    Text(report?.text("message").orEmpty().ifBlank { "기본 운동 기록은 그대로 확인할 수 있어요." })
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable internal fun WorkoutInsightResultView(result: JsonObject, message: String = "") {
    val snapshot = result.getAsJsonObject("snapshot")
    val summary = result.getAsJsonObject("summary")
    val narrative = result.text("methodVersion") == "grounded-narrative-v2"
    PlanBadge(if (result.text("source") == "AI") { if (narrative) "AI 종합 리포트" else "AI 해석 · 기록 근거 검증" } else "기록 기반 기본 해석")
    PlanHeading(summary.text("title"), summary.text("explanation"))
    if (narrative) PlanDisclosure("종합 평가의 근거") {
        summary.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
    } else summary.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    Text("기록에서 확인한 점", fontWeight = FontWeight.SemiBold)
    result.rows("observations").forEach { InsightPointView(it, narrative) }
    Text("다음 운동에서", fontWeight = FontWeight.SemiBold)
    result.rows("nextActions").forEach { InsightPointView(it, narrative) }
    val body = snapshot.get("inbody")?.takeUnless { it.isJsonNull }?.asJsonObject
    Text(body?.let { "인바디 측정일 ${it.text("measuredDate")}" } ?: "인바디 미사용", style = MaterialTheme.typography.bodySmall)
    PlanDisclosure("사용한 정보와 해석의 한계") {
        Text("목표: ${planGoalLabel(snapshot.text("goal")).ifBlank { "미설정" }} · 숙련도: ${planSkillLabel(snapshot.text("skillLevel")).ifBlank { "미설정" }}")
        snapshot.getAsJsonObject("report")?.let { report -> Text("운동 기록: ${report.text("endedAt").take(10)} · 완료 ${report.recordInt("completedSetCount") ?: 0}세트") }
        body?.let { Text("선택 인바디: 체지방률 ${it.text("bodyFatPercentage")}% · 골격근량 ${it.text("skeletalMuscleMassKg")}kg") }
        snapshot.get("samsungHealth")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { samsung ->
            Text("Samsung Health 활동 시간 ${samsung.recordInt("activeDurationSeconds")}초")
            samsung.get("meanHeartRate")?.takeUnless { it.isJsonNull }?.let { Text("기기 평균 심박 ${it.asString} bpm") }
            samsung.get("caloriesKcal")?.takeUnless { it.isJsonNull }?.let { Text("기기 소비 칼로리 ${it.asString} kcal") }
        }
        snapshot.getAsJsonArray("notices")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
        Text(if (narrative) "AI가 여러 기록을 연결해 작성한 설명입니다. 근거 수치는 저장된 기록이며, AI의 해석에는 오류가 있을 수 있어요." else "AI는 기록에 맞는 해석과 다음 행동을 선택합니다. 숫자와 표시 문구는 서버에서 검증한 내용을 사용해요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun InsightPointView(point: JsonObject, narrative: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(point.text("title"), fontWeight = FontWeight.SemiBold)
        Text(point.text("explanation"), style = MaterialTheme.typography.bodyMedium)
        if (narrative) PlanDisclosure("근거 ${point.getAsJsonArray("evidence")?.size() ?: 0}개 보기") {
            point.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
        } else point.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (!narrative) HorizontalDivider()
}
