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

internal fun workoutInsightPayload(id: String, inbody: String?, samsung: Boolean, consent: Boolean, version: String,
    weeks: Int = 4, bodyIds: List<String> = emptyList(), previousReports: Boolean = false): Map<String, Any> = buildMap {
    require(weeks in listOf(4, 8, 12) && bodyIds.size <= 12 && bodyIds.distinct().size == bodyIds.size)
    require(inbody == null || bodyIds.isEmpty())
    put("requestId", UUID.fromString(id).toString())
    inbody?.let { put("inbodyRecordId", UUID.fromString(it).toString()) }
    put("includeSamsungHealth", samsung); put("externalAiConsent", consent)
    put("historyWeeks", weeks); put("inbodyRecordIds", bodyIds.map { UUID.fromString(it).toString() })
    put("includePreviousReports", previousReports)
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
    var bodyIds by rememberSaveable(sessionId) { mutableStateOf(arrayListOf<String>()) }
    var weeks by rememberSaveable(sessionId) { mutableIntStateOf(4) }
    var previousReports by rememberSaveable(sessionId) { mutableStateOf(false) }
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
    LaunchedEffect(api, sessionId, reload, weeks) {
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                api.request("GET", "$root/options?historyWeeks=$weeks").asJsonObject to api.request("GET", root)
            }
            options = result.first
            records = result.first.rows("inbodyOptions")
            bodyIds = ArrayList(bodyIds.filter { id -> records.any { it.text("id") == id } })
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
        if (requestJson == null) requestJson = gson.toJson(workoutInsightPayload(UUID.randomUUID().toString(), null, samsung, consent && available, options!!.text("consentVersion"), weeks, bodyIds, previousReports))
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
                    PlanHeading("최근의 흐름에서\n오늘을 살펴봐요", "이번 운동 + 기간 내 헬스 기록을 함께 분석해요.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 8, 12).forEach { value -> FilterChip(weeks == value, {
                            weeks = value; bodyIds = arrayListOf(); requestJson = null; consent = false
                        }, { Text("${value}주") }, enabled = !busy) }
                    }
                    PlanSummary {
                        Text("${options?.text("fromDate")} ~ ${options?.text("throughDate")}", fontWeight = FontWeight.SemiBold)
                        Text("이번 운동 + 이전 헬스 ${options?.recordInt("historySessionCount") ?: 0}회 · 현재 목표·숙련도")
                        if (options?.get("demo")?.asBoolean == true) Text("[DEMO] 가상 운동 분석 · 실제 상태 평가가 아니에요.")
                        if (options?.get("historyTruncated")?.asBoolean == true) Text("최근 40개 후보 내 일부 기록만 분석해요.")
                    }
                    PlanDisclosure("인바디 · ${bodyIds.size}개 선택") {
                        Text("기간 내 여러 측정값을 선택하면 변화 추이를 함께 볼 수 있어요. 최대 12개, 선택하지 않아도 진행할 수 있어요.")
                        TextButton({ bodyIds = ArrayList(records.map { it.text("id") }); requestJson = null; consent = false }, enabled = !busy && records.isNotEmpty()) { Text("기간 내 기록 추천 선택 (${records.size}개)") }
                        TextButton({ bodyIds = arrayListOf(); requestJson = null; consent = false }, enabled = !busy) { Text("인바디 사용 안 함") }
                        if (records.isEmpty()) Text("선택 기간에 운동일 이전 인바디가 없어요.")
                        records.forEach { row -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(row.text("id") in bodyIds, { checked ->
                                bodyIds = ArrayList(if (checked) bodyIds + row.text("id") else bodyIds - row.text("id")); requestJson = null; consent = false
                            }, enabled = !busy)
                            Text(row.text("measuredDate"))
                        } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(samsung, { samsung = it; requestJson = null; consent = false }, enabled = !busy && samsungAvailable)
                        Column { Text("기간 내 Samsung Health 요약 사용"); Text(if (samsungAvailable) "이번 운동과 분석 대상 과거 운동에 연결된 요약을 사용해요." else "분석 대상 운동에 연결된 요약이 없어요.", style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(previousReports, { previousReports = it; requestJson = null; consent = false }, enabled = !busy)
                        Text("이전 리포트의 제안 참고 (최대 3건)")
                    }
                    Text("이전 제안은 사실이나 이행 완료로 간주하지 않아요. 이번 운동 시작 전에 작성된 리포트만 사용해요.", style = MaterialTheme.typography.bodySmall)
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
    val narrative = result.text("methodVersion") in listOf("grounded-narrative-v2", "longitudinal-narrative-v3")
    PlanBadge(if (result.text("source") == "AI") { if (narrative) "AI 종합 리포트" else "AI 해석 · 기록 근거 검증" } else "기록 기반 기본 해석")
    PlanHeading(summary.text("title"), summary.text("explanation"))
    snapshot.get("longitudinal")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { h ->
        PlanSummary {
            Text("최근 ${h.recordInt("weeks")}주 · 헬스 ${h.rows("sessions").size + 1}회")
            Text("인바디 ${h.rows("bodies").size}건 · 이전 리포트 ${h.rows("previousAdvice").size}건")
            if (h.get("demo")?.asBoolean == true) Text("가상 데이터로 만든 시연용 해석입니다.")
        }
    }
    if (narrative) PlanDisclosure("종합 평가의 근거") {
        summary.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
    } else summary.getAsJsonArray("evidence")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    Text("기록에서 확인한 점", fontWeight = FontWeight.SemiBold)
    result.rows("observations").forEach { InsightPointView(it, narrative) }
    Text("다음 운동과 앞으로의 방향", fontWeight = FontWeight.SemiBold)
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
