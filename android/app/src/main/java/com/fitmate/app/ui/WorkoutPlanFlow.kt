package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable internal fun ServerRoutineLibrary(modifier: Modifier, api: FitMateApiClient, healthSelected: Boolean,
    select: (JsonObject) -> Unit, local: () -> Unit, back: () -> Unit = {}) {
    var recommendation by rememberSaveable { mutableStateOf(false) }
    if (recommendation) WorkoutPlanFlow(modifier, api, { recommendation = false }, select)
    else Column(modifier) {
        WorkoutFlowBackBar("운동", back)
        ServerRoutineList(Modifier.weight(1f), api, healthSelected, select, local, { recommendation = true })
    }
}

internal fun workoutPlanConditionsError(days: String, minutes: String): String? = when {
    days.toIntOrNull() !in 1..7 -> "주 운동 횟수는 1~7일로 입력해 주세요."
    minutes.toIntOrNull() !in 20..180 -> "회당 시간은 20~180분으로 입력해 주세요."
    else -> null
}

internal fun workoutPlanPayload(id: String, days: String, minutes: String, equipment: List<String>, preferred: List<String>, excluded: List<String>, inbody: String?, consent: Boolean, consentVersion: String): Map<String, Any> = buildMap {
    require(workoutPlanConditionsError(days, minutes) == null)
    put("requestId", id); put("daysPerWeek", days.toInt()); put("sessionMinutes", minutes.toInt())
    put("equipmentTypes", equipment); put("preferredExerciseIds", preferred); put("excludedExerciseIds", excluded)
    inbody?.let { put("inbodyRecordId", it) }
    put("externalAiConsent", consent)
    if (consent) put("consentVersion", consentVersion)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkoutPlanFlow(modifier: Modifier, api: FitMateApiClient, close: () -> Unit, select: (JsonObject) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var page by rememberSaveable { mutableStateOf("conditions") }
    var days by rememberSaveable { mutableStateOf("3") }
    var minutes by rememberSaveable { mutableStateOf("45") }
    var equipment by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var preferred by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var excluded by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var inbodyId by rememberSaveable { mutableStateOf<String?>(null) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var acceptedPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<JsonObject?>(null) }
    var inbody by remember { mutableStateOf(emptyList<JsonObject>()) }
    var plan by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var poll by remember { mutableIntStateOf(0) }
    var replacementDay by rememberSaveable { mutableIntStateOf(1) }
    var replacementId by rememberSaveable { mutableStateOf("") }
    var exerciseSearch by rememberSaveable { mutableStateOf("") }
    var alternatives by remember { mutableStateOf(emptyList<JsonObject>()) }
    var displayedDay by rememberSaveable { mutableIntStateOf(1) }
    var selectedAlternative by rememberSaveable { mutableStateOf<String?>(null) }
    val gson = remember { com.google.gson.Gson() }
    val path = "workout-recommendations/plans"
    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "연결 상태를 확인하고 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
    suspend fun call(method: String, endpoint: String, body: Any? = null) = withContext(Dispatchers.IO) { api.request(method, endpoint, body).asJsonObject }
    fun back() {
        when (page) {
            "conditions" -> close()
            "replace" -> { page = "result"; alternatives = emptyList(); error = null }
            "exercises" -> { page = "conditions"; error = null }
            "generating" -> { error = "진행 중인 추천은 취소 버튼으로 종료해 주세요." }
            else -> { page = "conditions"; requestId = UUID.randomUUID().toString(); acceptedPayload = null; plan = null; error = null }
        }
    }
    BackHandler { if (!busy) back() }
    LaunchedEffect(page) { if (page != "conditions" && page != "exercises") { focus.clearFocus(); keyboard?.hide() } }
    LaunchedEffect(reload) {
        loading = true; error = null
        try { options = call("GET", "$path/options") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { loading = false }
    }
    LaunchedEffect(page, poll) {
        if (page == "generating" || (page == "result" && plan == null) || page == "replace" && plan == null) {
            try {
                var attempts = 0
                while (attempts++ < 125) {
                    val response = call("GET", "$path/$requestId")
                    plan = response
                    when (response.text("status")) {
                        "READY", "SAVED" -> { page = "result"; break }
                        "FAILED", "CANCELED" -> { error = response.text("message"); page = "failed"; break }
                        else -> delay(1000)
                    }
                }
                if (page == "generating") { error = "응답 확인이 지연되고 있습니다. 진행 상태를 다시 확인해 주세요."; page = "failed" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message; page = "failed" }
        }
    }
    val result = plan?.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
    val saved = plan?.text("status") == "SAVED"
    val aiAvailable = options?.get("aiAvailable")?.asBoolean == true
    fun generate() {
        val invalid = workoutPlanConditionsError(days, minutes)
        if (invalid != null) { error = invalid; return }
        if (acceptedPayload == null) {
            requestId = UUID.randomUUID().toString()
            acceptedPayload = gson.toJson(workoutPlanPayload(requestId, days, minutes, equipment, preferred, excluded, inbodyId, consent && aiAvailable, options!!.text("consentVersion")))
        }
        launchAction {
            focus.clearFocus()
            plan = call("POST", path, gson.fromJson(acceptedPayload, JsonObject::class.java))
            page = "generating"; poll++
        }
    }
    PlanTheme { key(page) { PlanPage(modifier,
        title = when (page) { "conditions" -> "추천 조건"; "generating" -> "추천 구성 중"; "replace" -> "운동 교체"; "exercises" -> "선호·제외 운동"; "failed" -> "추천 상태 확인"; else -> if (saved) "내 루틴" else "나의 추천 결과" },
        back = { if (!busy) back() }, footer = {
            when (page) {
                "conditions" -> PlanAction(if (consent && aiAvailable) "AI 맞춤 추천 만들기" else "기본 추천 만들기", !busy && !loading && options != null, ::generate)
                "exercises" -> PlanAction("선택 완료") { page = "conditions" }
                "generating", "failed" -> {
                    if (page == "failed") PlanAction("같은 조건으로 재시도", !busy && acceptedPayload != null) { launchAction {
                        plan = call("POST", path, gson.fromJson(acceptedPayload, JsonObject::class.java)); page = "generating"; poll++
                    } }
                    TextButton({ launchAction { plan = call("POST", "$path/$requestId/cancel"); page = "conditions"; acceptedPayload = null; requestId = UUID.randomUUID().toString() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("추천 취소·조건으로 돌아가기") }
                }
                "result" -> if (result != null) {
                    if (saved) PlanAction("저장한 루틴으로 운동", !busy) { launchAction { select(call("GET", "routines/${plan!!.text("routineId")}")) } }
                    else PlanAction("내 루틴에 저장", !busy && plan?.text("status") == "READY") { launchAction {
                        plan = call("POST", "$path/$requestId/save", mapOf("version" to plan!!.get("version").asLong))
                    } }
                    Text(if (saved) "다음 화면에서 오늘의 운동을 확인해요" else "운동 구성과 추천 조건을 함께 보관해요",
                        Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "replace" -> PlanAction("교체하고 다시 검증", !busy && selectedAlternative != null) { launchAction {
                    plan = call("POST", "$path/$requestId/replace", mapOf("version" to plan!!.get("version").asLong, "day" to replacementDay, "exerciseId" to replacementId, "replacementId" to selectedAlternative!!))
                    page = "result"
                } }
            }
        }) {
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { ErrorText(it) }
        if (options == null && !loading) TextButton({ reload++ }) { Text("조건 다시 불러오기") }
        if (page == "conditions" && options != null) {
            PlanHeading("이번 주, 어떻게\n운동할까요?")
            PlanSummary {
                Text("${planGoalLabel(options!!.text("goal"))} · ${planSkillLabel(options!!.text("skillLevel"))}", fontWeight = FontWeight.SemiBold)
                Text("프로필에 설정한 목표와 숙련도", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(days, { days = it.filter(Char::isDigit).take(1); acceptedPayload = null }, Modifier.fillMaxWidth(), label = { Text("주 운동 횟수 · 1~7일") }, enabled = !busy, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3); acceptedPayload = null }, Modifier.fillMaxWidth(), label = { Text("회당 가능한 시간 · 20~180분") }, enabled = !busy, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            Text("사용할 기구", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MACHINE", "DUMBBELL", "BARBELL", "CABLE", "BODYWEIGHT").forEach { code ->
                    FilterChip(code in equipment, { equipment = ArrayList(if (code in equipment) equipment - code else equipment + code); acceptedPayload = null }, { Text(planEquipmentLabel(code)) }, enabled = !busy)
                }
            }
            Text("미선택 시 전체 기구를 사용해요. 맨몸만 가능하면 ‘맨몸’을 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlanDisclosure("인바디 · ${if (inbodyId == null) "미사용" else "선택한 기록 사용"}") {
                TextButton({ inbodyId = null; acceptedPayload = null }, enabled = !busy) { Text("인바디 사용 안 함") }
                TextButton({ launchAction { inbody = withContext(Dispatchers.IO) { api.items("inbody-records") }; if (inbody.isEmpty()) error = "저장한 인바디가 없습니다. 인바디 없이 계속할 수 있어요." } }, enabled = !busy) { Text("내 인바디 기록 불러오기") }
                inbody.forEach { row -> Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(inbodyId == row.text("id"), { inbodyId = row.text("id"); acceptedPayload = null }, enabled = !busy)
                    Text("측정일 ${row.text("measuredDate")}")
                } }
            }
            TextButton({ page = "exercises" }, Modifier.fillMaxWidth(), enabled = !busy) {
                Text("선호·제외 운동", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text("선호 ${preferred.size} · 제외 ${excluded.size}  ›")
            }
            if (aiAvailable) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it; acceptedPayload = null }, enabled = !busy)
                Text("이번 추천의 외부 AI 전송에 동의", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (aiAvailable) "미동의해도 기본 추천을 받을 수 있어요." else "현재는 기본 추천을 이용할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlanDisclosure("전송 정보 및 개발·시연용 안내") { Text(options!!.text("consentNotice"), style = MaterialTheme.typography.bodySmall) }
        }
        if (page == "exercises" && options != null) {
            PlanHeading("나의 운동 취향", "피하고 싶은 운동은 추천에서 제외해요.")
            OutlinedTextField(exerciseSearch, { exerciseSearch = it }, Modifier.fillMaxWidth(), label = { Text("운동 이름 찾기") }, singleLine = true)
            val filtered = options!!.rows("exercises").filter { it.text("name").contains(exerciseSearch.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) Text("일치하는 운동이 없습니다.")
            filtered.forEach { exercise ->
                val id = exercise.text("id")
                Text(exercise.text("name"), style = MaterialTheme.typography.titleSmall)
                Text(planEquipmentLabel(exercise.text("equipmentType")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(id in preferred, { preferred = ArrayList(if (id in preferred) preferred - id else preferred + id); excluded = ArrayList(excluded - id); acceptedPayload = null }, { Text("선호") })
                    FilterChip(id in excluded, { excluded = ArrayList(if (id in excluded) excluded - id else excluded + id); preferred = ArrayList(preferred - id); acceptedPayload = null }, { Text("제외") })
                }
                HorizontalDivider()
            }
        }
        if (page == "generating") {
            PlanBadge("주 ${days}일 · 회당 ${minutes}분")
            PlanHeading("운동 계획을\n만들고 있어요.")
            PlanSummary { Text("조건 확인 → 구성 제안 → 서버 검증", fontWeight = FontWeight.SemiBold)
                Text("날짜별 운동량과 가능한 기구를 확인하고 있어요.", style = MaterialTheme.typography.bodySmall) }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("생성에 실패해도 입력한 조건은 유지됩니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (page == "failed") {
            PlanHeading("조건을 유지했어요.", "다시 시도하거나 조건을 바꿔볼 수 있어요.")
            TextButton({ error = null; page = "generating"; poll++ }, enabled = !busy) { Text("진행 상태 다시 확인") }
            TextButton({ back() }, enabled = !busy) { Text("조건 수정") }
        }
        if (page == "result" && result != null) {
            if (saved) {
                PlanBadge("내 루틴에 저장했어요")
                PlanHeading("이제, 시작해 볼까요?", "구성과 추천 이유도 함께 보관했어요.")
            }
            WorkoutPlanResultContent(result, selectedDay = displayedDay, selectDay = { displayedDay = it }, enabled = !busy,
                replace = if (saved) null else { day: Int, id: String -> launchAction {
                    replacementDay = day; replacementId = id; selectedAlternative = null
                    alternatives = withContext(Dispatchers.IO) { api.items("$path/$requestId/alternatives?day=$replacementDay&exerciseId=$replacementId") }
                    page = "replace"
                } })
            if (saved) TextButton(close) { Text("루틴 목록으로") }
            if (error != null) TextButton({ launchAction { plan = call("GET", "$path/$requestId") } }, enabled = !busy) { Text("최신 결과 다시 불러오기") }
        }
        if (page == "replace") {
            PlanBadge("${replacementDay}일차")
            PlanHeading("이 운동으로\n바꿔볼까요?", "기구와 운동 부위에 맞는 후보예요.")
            val original = result?.rows("days")?.firstOrNull { it.text("day").toIntOrNull() == replacementDay }?.rows("exercises")?.firstOrNull { it.text("exerciseId") == replacementId }
            original?.let { PlanSummary { Text("현재 운동", style = MaterialTheme.typography.labelMedium); Text(it.text("exerciseName"), fontWeight = FontWeight.SemiBold) } }
            if (alternatives.isEmpty()) Text("교체 가능한 운동이 없습니다. 조건을 수정해 새 추천을 받을 수 있어요.")
            alternatives.forEach { candidate ->
                val exercise = candidate.getAsJsonObject("exercise")
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (selectedAlternative == exercise.text("id")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = { selectedAlternative = exercise.text("id") }, enabled = !busy) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selectedAlternative == exercise.text("id"), null)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(exercise.text("name"), fontWeight = FontWeight.SemiBold)
                            Text(planEquipmentLabel(exercise.text("equipmentType")), style = MaterialTheme.typography.bodySmall)
                            Text("교체 후 ${planMinutes(candidate.text("estimatedDaySeconds"))} · 변화 ${candidate.text("differenceSeconds")}초", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text("교체한 구성도 운동 시간과 조건을 다시 확인합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkoutPlanResultContent(result: JsonObject, selectedDay: Int = 1,
    selectDay: ((Int) -> Unit)? = null, enabled: Boolean = true, replace: ((Int, String) -> Unit)? = null) {
    var localDay by rememberSaveable { mutableIntStateOf(1) }
    val days = result.rows("days")
    val day = days.firstOrNull { it.text("day").toIntOrNull() == if (selectDay == null) localDay else selectedDay } ?: days.firstOrNull()
    PlanBadge(if (result.text("source") == "AI") "AI 구성 · 서버 검증 완료" else "기본 추천 · 서버 검증 완료")
    PlanHeading(result.text("name"), "${planGoalLabel(result.text("goal"))} · ${planSkillLabel(result.text("skillLevel"))}")
    Text(result.get("inbodyMeasuredDate")?.takeUnless { it.isJsonNull }?.asString?.let { "인바디 측정일 $it" }
        ?: "인바디 미사용 · 체성분 정보 없이 구성했어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    PlanSummary {
        Text(result.getAsJsonArray("reasons")?.firstOrNull()?.asString.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        Text("주 ${days.size}일  ·  ${day?.rows("exercises")?.size ?: 0}종목  ·  약 ${planMinutes(day?.text("estimatedSeconds").orEmpty())}", fontWeight = FontWeight.SemiBold)
    }
    Text("날짜별 운동", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { item ->
            val number = item.text("day").toInt()
            FilterChip(day == item, { if (selectDay == null) localDay = number else selectDay(number) },
                { Text("${number}일차 · ${item.rows("exercises").size}종목") }, enabled = enabled)
        }
    }
    day?.let { current ->
        Text("준비운동 ${planMinutes(current.text("warmupSeconds"))} · ${current.text("day")}일차 약 ${planMinutes(current.text("estimatedSeconds"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        current.rows("exercises").forEachIndexed { index, exercise ->
            PlanExerciseRow(index, exercise.text("exerciseName"), "${exercise.text("sets")}세트 × ${exercise.text("reps")}회 · 휴식 ${exercise.text("restSeconds")}초 · 약 ${planMinutes(exercise.text("estimatedSeconds"))}",
                enabled, replace?.let { action -> { action(current.text("day").toInt(), exercise.text("exerciseId")) } })
        }
        Text(if (current.text("cardioSeconds").toIntOrNull() == 0) "별도 유산소 제안 없음" else "유산소 ${planMinutes(current.text("cardioSeconds"))}", style = MaterialTheme.typography.bodySmall)
    }
    PlanDisclosure("추천 이유·사용한 정보") {
        val conditions = result.getAsJsonObject("conditions")
        Text("회당 ${conditions.text("sessionMinutes")}분 이내 · 선호 ${conditions.getAsJsonArray("preferredExerciseIds").size()}개 · 제외 ${conditions.getAsJsonArray("excludedExerciseIds").size()}개", style = MaterialTheme.typography.bodySmall)
        Text("기구: " + conditions.getAsJsonArray("equipmentTypes").joinToString { planEquipmentLabel(it.asString) }.ifBlank { "전체" }, style = MaterialTheme.typography.bodySmall)
        Text("외부 AI 전송 동의: ${if (conditions.get("externalAiConsent").asBoolean) "있음" else "없음"}", style = MaterialTheme.typography.bodySmall)
        result.getAsJsonArray("reasons")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
        result.getAsJsonArray("notices")?.forEach { Text(it.asString, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun planMinutes(seconds: String): String = "${((seconds.toIntOrNull() ?: 0) + 59) / 60}분"
private fun planEquipmentLabel(code: String) = mapOf("MACHINE" to "머신", "DUMBBELL" to "덤벨", "BARBELL" to "바벨", "CABLE" to "케이블", "BODYWEIGHT" to "맨몸")[code] ?: code
internal fun planGoalLabel(code: String) = mapOf("DIET" to "체지방 감소", "MUSCLE_GAIN" to "근육 증가", "FITNESS" to "체력 향상", "RECORD_IMPROVEMENT" to "기록 향상", "HABIT" to "운동 습관")[code] ?: code
internal fun planSkillLabel(code: String) = mapOf("BEGINNER" to "입문", "UNDER_6_MONTHS" to "6개월 미만", "SIX_MONTHS_TO_ONE_YEAR" to "6개월~1년", "ONE_TO_THREE_YEARS" to "1~3년", "OVER_THREE_YEARS" to "3년 이상")[code] ?: code
