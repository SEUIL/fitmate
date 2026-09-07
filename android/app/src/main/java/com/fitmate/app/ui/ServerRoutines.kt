package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ServerRoutineLibrary(modifier: Modifier, api: FitMateApiClient, healthSelected: Boolean,
    select: (JsonObject) -> Unit, local: () -> Unit) = Screen(modifier) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("추천 루틴") }
    var rows by remember { mutableStateOf(emptyList<JsonObject>()) }
    var exercises by remember { mutableStateOf(emptyList<com.fitmate.app.data.remote.ApiExercise>()) }
    var revision by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<JsonObject?>(null) }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf<JsonObject?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    fun perform(block: () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; done() }
            catch (failure: Exception) { error = failure.message }
            finally { busy = false }
        }
    }
    LaunchedEffect(tab, revision) {
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
        } catch (failure: Exception) { error = failure.message }
        finally { busy = false }
    }
    SocialTabs(listOf("추천 루틴", "기본 루틴", "내 루틴"), tab) { if (!busy) tab = it }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { ErrorText(it); TextButton({ revision++ }, enabled = !busy) { Text("다시 불러오기") } }
    if (!busy && rows.isEmpty() && error == null) Text(if (tab == "추천 루틴" && !healthSelected) "프로필에서 헬스 숙련도를 설정하면 추천을 받을 수 있어요. 기본 루틴은 바로 이용할 수 있습니다." else "등록된 루틴이 없습니다.")
    rows.forEach { row -> SoftCard {
        Text(row.text("name"), style = MaterialTheme.typography.titleMedium)
        Text(row.rows("items").joinToString(" → ") { it.text("exerciseName") })
        Button({ select(row) }, enabled = !busy) { Text("이 루틴으로 운동") }
        if (row.get("template").asBoolean) TextButton({ perform({ api.request("POST", "routines", mapOf("name" to "${row.text("name")} 복사", "sourceType" to "USER_COPY", "sourceRoutineId" to row.text("id"))) }, { tab = "내 루틴"; revision++ }) }, enabled = !busy) { Text("내 루틴으로 복사") }
        else Row {
            TextButton({ original = row; name = row.text("name"); selected = row.rows("items").map { it.text("exerciseId") }; edit = true }, enabled = !busy) { Text("수정") }
            TextButton({ deleting = row }, enabled = !busy) { Text("삭제") }
        }
    } }
    Button({ original = null; name = ""; selected = emptyList(); edit = true }, enabled = !busy && exercises.isNotEmpty()) { Text("새 루틴 만들기") }
    TextButton(local, enabled = !busy) { Text("기기에 저장한 루틴·직접 입력 운동") }
    deleting?.let { row -> ConfirmAction("루틴 삭제", "이 루틴을 삭제할까요? 완료한 운동 기록은 유지됩니다.", { deleting = null }) {
        perform({ api.request("DELETE", "routines/${row.text("id")}?version=${row.text("version")}") }, { deleting = null; revision++ })
    } }
    if (edit) SocialSheet({ if (!busy) edit = false }) {
        OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("루틴 이름") })
        selected.forEachIndexed { index, id -> Row {
            Text(exercises.firstOrNull { it.id == id }?.name ?: original?.rows("items")?.firstOrNull { it.text("exerciseId") == id }?.text("exerciseName").orEmpty(), Modifier.weight(1f))
            TextButton({ selected = selected.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = !busy && index > 0) { Text("↑") }
            TextButton({ selected = selected.filterIndexed { i, _ -> i != index } }, enabled = !busy) { Text("제거") }
        } }
        Text("추가할 운동", style = MaterialTheme.typography.titleMedium)
        exercises.forEach { exercise -> Row {
            TextButton({ if (exercise.id !in selected) selected = selected + exercise.id }, enabled = !busy && exercise.id !in selected) { Text("+ ${exercise.name}") }
            TextButton({ perform({ val value = api.request("GET", "exercises/${exercise.id}").asJsonObject; value.add("substitutions", com.google.gson.JsonArray().also { list -> api.items("exercises/${exercise.id}/substitutions", false).forEach { list.add(it) } }); detail = value }) }, enabled = !busy) { Text("상세·대체") }
        } }
        Text("새 운동의 기본 구성: 3세트. 기존 운동의 목표 무게·횟수·휴식은 유지됩니다.")
        error?.let { ErrorText(it) }
        Button({
            val previous = original
            val items = selected.mapIndexed { i, id -> (previous?.rows("items")?.firstOrNull { it.text("exerciseId") == id }?.deepCopy() ?: JsonObject().apply { addProperty("targetSets", 3) }).apply {
                remove("id"); remove("version"); remove("exerciseName"); remove("bodyPart"); addProperty("exerciseId", id); addProperty("sequenceNo", i + 1)
            } }
            val payload = mutableMapOf<String, Any>("name" to name.trim(), "items" to items)
            if (previous == null) { payload["sourceType"] = "MANUAL"; payload["sportCode"] = "HEALTH" } else payload["version"] = previous.get("version").asLong
            perform({ api.request(if (previous == null) "POST" else "PATCH", "routines" + (previous?.let { "/${it.text("id")}" } ?: ""), payload) }, { edit = false; tab = "내 루틴"; revision++ })
        }, enabled = !busy && name.isNotBlank() && selected.isNotEmpty()) { Text(if (busy) "저장 중…" else "서버에 저장") }
    }
    detail?.let { row -> SocialSheet({ detail = null }) {
        Text(row.text("name"), style = MaterialTheme.typography.titleLarge)
        Text("${row.text("bodyPart")} · ${row.text("equipmentType")}")
        Text("대체 운동: ${row.rows("substitutions").joinToString { it.text("name") }.ifBlank { "등록된 대체 운동 없음" }}")
    } }
}
