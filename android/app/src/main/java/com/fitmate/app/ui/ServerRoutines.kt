package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ServerRoutineList(modifier: Modifier, api: FitMateApiClient, healthSelected: Boolean,
    select: (JsonObject) -> Unit, local: () -> Unit, recommend: () -> Unit) = Column(modifier) {
    var chosen by remember { mutableStateOf<JsonObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    WorkoutJourneyPage(Modifier.weight(1f)) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("추천 루틴") }
    var rows by remember { mutableStateOf(emptyList<JsonObject>()) }
    var exercises by remember { mutableStateOf(emptyList<com.fitmate.app.data.remote.ApiExercise>()) }
    var revision by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<JsonObject?>(null) }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf<JsonObject?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var recommendationDetails by remember { mutableStateOf<String?>(null) }
    var expandedIds by remember(tab) { mutableStateOf(emptySet<String>()) }
    fun perform(block: () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; done() }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "요청을 처리하지 못했어요."
            }
            finally { busy = false }
        }
    }
    LaunchedEffect(tab, revision) {
        chosen = null
        expandedIds = emptySet()
        busy = true; error = null; rows = emptyList()
        try {
            val loaded = withContext(Dispatchers.IO) {
                val summaries = when (tab) {
                    "추천 루틴" -> if (healthSelected) listOf(api.request("GET", "routines/recommended?sportCode=HEALTH").asJsonObject) else emptyList()
                    "기본 루틴" -> api.items("routines/templates")
                    else -> api.items("routines")
                }
                summaries.map { row -> if (row.has("items")) row else api.request("GET", "routines/${row.text("id")}").asJsonObject } to api.fetchExercises()
            }
            rows = loaded.first; exercises = loaded.second
        } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "요청을 처리하지 못했어요."
            }
        finally { busy = false }
    }
    fun showDetail(id: String) {
        var loaded: JsonObject? = null
        perform({
            loaded = api.request("GET", "exercises/$id").asJsonObject.apply {
                add("substitutions", com.google.gson.JsonArray().also { list ->
                    api.items("exercises/$id/substitutions", false).forEach { list.add(it) }
                })
            }
        }, { detail = loaded })
    }
    PlanHeading("헬스 루틴", "지금 나에게 맞는 루틴으로 시작해요.")
    Row(Modifier.fillMaxWidth()) {
        listOf("추천 루틴", "기본 루틴", "내 루틴").forEach { label ->
            Column(Modifier.weight(1f).selectable(tab == label, enabled = !busy, role = Role.Tab, onClick = { tab = label }), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, Modifier.padding(vertical = 16.dp), color = if (tab == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (tab == label) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (tab == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
    if (busy) { Text("루틴을 불러오거나 변경 사항을 처리하고 있어요."); LinearProgressIndicator(Modifier.fillMaxWidth()) }
    error?.let { ErrorText(it); TextButton({ revision++ }, enabled = !busy) { Text("다시 불러오기") } }
    if (!busy && rows.isEmpty() && error == null) WorkoutCard {
        Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (tab == "내 루틴") "아직 만든 루틴이 없어요" else "이용 가능한 루틴을 확인해 주세요", style = MaterialTheme.typography.titleLarge)
        Text(if (tab == "추천 루틴" && !healthSelected) "프로필에서 헬스 숙련도를 설정하면 추천을 받을 수 있어요. 기본 루틴은 바로 이용할 수 있습니다." else "기본 루틴으로 시작하거나 나만의 루틴을 만들어 보세요.")
        if (tab != "기본 루틴") OutlinedButton({ tab = "기본 루틴" }, Modifier.fillMaxWidth()) { Text("기본 루틴 보기") }
    }
    if (tab == "추천 루틴") OutlinedButton(recommend, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Text("맞춤 조건으로 추천 받기")
    }
    if (rows.isNotEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$tab ${rows.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (rows.size > 1) {
            val allExpanded = rows.all { it.text("id") in expandedIds }
            TextButton({ expandedIds = if (allExpanded) emptySet() else rows.map { it.text("id") }.toSet() }, enabled = !busy) {
                Text(if (allExpanded) "모두 접기" else "모두 펼치기", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    rows.forEach { row -> WorkoutCard {
        val id = row.text("id")
        val expanded = id in expandedIds
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(chosen?.text("id") == id, { chosen = row }, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "${row.text("name")} 선택" })
            Box(Modifier.weight(1f)) {
                RoutineSummary(row, expanded, enabled = !busy) { expandedIds = if (expanded) expandedIds - id else expandedIds + id }
            }
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineExerciseList(row, tab, !busy, showHeading = false, detail = ::showDetail)
        row.get("recommendation")?.takeIf { it.isJsonObject }?.asJsonObject?.let { evidence ->
            TextButton({ recommendationDetails = if (recommendationDetails == row.text("id")) null else row.text("id") }) { Text("저장 당시 추천 조건·근거") }
            if (recommendationDetails == row.text("id")) {
                Text("아래는 저장 당시 추천입니다. 이후 직접 수정한 운동 구성과 다를 수 있어요.")
                WorkoutPlanResultContent(evidence)
            }
        }
        if (row.get("template").asBoolean) TextButton({ perform({ api.request("POST", "routines", mapOf("name" to "${row.text("name")} 복사", "sourceType" to "USER_COPY", "sourceRoutineId" to row.text("id"))) }, { tab = "내 루틴"; revision++ }) }, enabled = !busy) { Text("내 루틴으로 복사") }
        else Row {
            TextButton({ original = row; name = row.text("name"); selected = row.rows("items").map { it.text("exerciseId") }; edit = true }, enabled = !busy) { Text("수정") }
            TextButton({ deleting = row }, enabled = !busy) { Text("삭제") }
        }
            }
        }
    } }
    OutlinedButton({ original = null; name = ""; selected = emptyList(); edit = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && exercises.isNotEmpty(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Text("+ 새 루틴 만들기") }
    TextButton(local, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("기기에 저장한 루틴·직접 입력 운동") }
    deleting?.let { row -> ConfirmAction("루틴 삭제", "이 루틴을 삭제할까요? 완료한 운동 기록은 유지됩니다.", { deleting = null }) {
        perform({ api.request("DELETE", "routines/${row.text("id")}?version=${row.text("version")}") }, { deleting = null; revision++ })
    } }
    if (edit) RoutineEditorSheet(
        editing = original != null, name = name, onName = { name = it.take(100) },
        exercises = exercises, selected = selected, onSelection = { selected = it },
        originalNames = original?.rows("items")?.associate { it.text("exerciseId") to it.text("exerciseName") }.orEmpty(),
        busy = busy, error = error, dismiss = { if (!busy) edit = false }, detail = ::showDetail,
        save = {
            val previous = original
            val items = selected.mapIndexed { i, id -> (previous?.rows("items")?.firstOrNull { it.text("exerciseId") == id }?.deepCopy() ?: JsonObject().apply { addProperty("targetSets", 3) }).apply {
                remove("id"); remove("version"); remove("exerciseName"); remove("bodyPart"); addProperty("exerciseId", id); addProperty("sequenceNo", i + 1)
            } }
            val payload = mutableMapOf<String, Any>("name" to name.trim(), "items" to items)
            if (previous == null) { payload["sourceType"] = "MANUAL"; payload["sportCode"] = "HEALTH" } else payload["version"] = previous.get("version").asLong
            perform({ api.request(if (previous == null) "POST" else "PATCH", "routines" + (previous?.let { "/${it.text("id")}" } ?: ""), payload) }, { edit = false; tab = "내 루틴"; revision++ })
        }
    )
    detail?.let { row -> SocialSheet({ detail = null }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("운동 소개", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton({ detail = null }) { Text("닫기") }
        }
        ExerciseIntroduction(row.text("id"), row.text("name"), row.text("bodyPart"), row.text("equipmentType"), row.text("description"))
        if (edit) Button({
            val id = row.text("id")
            selected = if (id in selected) selected - id else selected + id
            detail = null
        }, Modifier.fillMaxWidth(), enabled = !busy) {
            Text(if (row.text("id") in selected) "루틴에서 제외" else "루틴에 추가")
        }
        Text("대체 운동: ${row.rows("substitutions").joinToString { it.text("name") }.ifBlank { "등록된 대체 운동 없음" }}")
    } }
    }
    Surface(shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            chosen?.let { Text(it.text("name"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleSmall) }
            Button({ chosen?.let(select) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = chosen != null && !busy,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text(if (chosen == null) "진행할 루틴을 선택해주세요" else "이 루틴으로 운동")
            }
        }
    }
}

@Composable internal fun RoutineExerciseList(row: JsonObject, label: String, enabled: Boolean, showHeading: Boolean = true, detail: (String) -> Unit) {
    val items = row.rows("items")
    if (showHeading) {
    Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text(row.text("name"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val sets = items.map { it.text("targetSets").toIntOrNull() }
    Text("운동 ${items.size}개" + if (sets.isNotEmpty() && sets.all { it != null }) " · 총 ${sets.filterNotNull().sum()}세트" else "",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val splitCount = row.text("splitCount").toIntOrNull() ?: 1
    items.groupBy { it.text("splitDayNo").toIntOrNull() ?: 1 }.toSortedMap().forEach { (splitDayNo, splitItems) ->
        if (splitCount > 1) Text("${splitDayNo}일차", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        splitItems.forEach { item ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton({ detail(item.text("exerciseId")) }, Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = enabled,
            shape = androidx.compose.ui.graphics.RectangleShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            Text(item.text("sequenceNo").toIntOrNull()?.toString()?.padStart(2, '0') ?: "--", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.FitnessCenter, null, Modifier.padding(horizontal = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.text("exerciseName"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(routineTargetLabel(item), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "${item.text("exerciseName")} 상세·대체", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}

internal fun routineTargetLabel(item: JsonObject): String {
    val minimum = item.text("targetRepsMin").toIntOrNull()
    val maximum = item.text("targetRepsMax").toIntOrNull()
    val repetitions = when {
        minimum != null && maximum != null -> if (minimum == maximum) "${minimum}회" else "${minimum}–${maximum}회"
        minimum != null -> "${minimum}회 이상"
        maximum != null -> "${maximum}회 이하"
        else -> null
    }
    return listOfNotNull(
    item.text("targetSets").toIntOrNull()?.let { "${it}세트" },
    item.text("targetWeightKg").toDoubleOrNull()?.let { "${it} kg" },
    repetitions,
    item.text("targetRpe").toDoubleOrNull()?.let { "RPE $it" },
    item.text("restSeconds").toIntOrNull()?.let { "휴식 ${it}초" }
).joinToString(" · ").ifBlank { "목표 값 미지정" }
}

@Composable internal fun RoutineSummary(row: JsonObject, expanded: Boolean, enabled: Boolean = true, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.text("name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            val items = row.rows("items")
            val count = items.sumOf { it.text("targetSets").toIntOrNull() ?: 0 }
            Text("운동 ${items.size}개" + if (count > 0) " · 총 ${count}세트" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(toggle, enabled = enabled, modifier = Modifier.semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }) {
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                "${row.text("name")} ${if (expanded) "접기" else "펼치기"}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
