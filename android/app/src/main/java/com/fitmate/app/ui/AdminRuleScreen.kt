package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminRuleScreen(api: FitMateApiClient, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("rules") }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var detection by remember { mutableStateOf<JsonObject?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("WORD") }
    var area by rememberSaveable { mutableStateOf("POST") }
    var term by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("3") }
    var window by rememberSaveable { mutableStateOf("60") }
    var enabled by rememberSaveable { mutableStateOf(false) }
    var version by rememberSaveable { mutableLongStateOf(0) }
    var reason by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var sample by remember { mutableStateOf("") }
    var prior by remember { mutableStateOf("0") }
    var history by remember { mutableStateOf<JsonObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun request(path: String, method: String = "GET", body: Any? = null, commandKey: String? = null) = withContext(Dispatchers.IO) {
        api.request(method, "admin/rules$path", body, headers = commandKey?.let { mapOf("Idempotency-Key" to it) } ?: emptyMap()).asJsonObject
    }
    suspend fun load(append: Boolean = false) {
        val cursor = if (append) page?.text("nextCursor").orEmpty() else ""
        val path = if (tab == "rules") "?" else "/detections?reviewed=$reviewed&"
        val result = request(path + if (cursor.isBlank()) "" else "cursor=${URLEncoder.encode(cursor, "UTF-8")}")
        if (append) {
            val merged = com.google.gson.JsonArray()
            (page?.rows("items").orEmpty() + result.rows("items")).distinctBy { it.text("id") }.forEach(merged::add)
            result.add("items", merged)
        }
        page = result
    }
    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                message = if (failure is ApiFailure) failure.message else "연결을 확인한 뒤 다시 시도해 주세요."
                if ((failure as? ApiFailure)?.status in listOf(401, 403)) { blocked = true; page = null; detection = null }
                if ((failure as? ApiFailure)?.status in listOf(400, 404, 409)) pending = false
            } finally { busy = false }
        }
    }
    fun edit(value: JsonObject?) {
        selected = value?.text("id") ?: UUID.randomUUID().toString()
        name = value?.text("name").orEmpty(); kind = value?.text("kind") ?: "WORD"; area = value?.text("scope") ?: "POST"
        term = value?.text("term").orEmpty(); threshold = value?.text("thresholdCount") ?: "3"; window = value?.text("windowSeconds") ?: "60"
        enabled = value?.get("enabled")?.asBoolean ?: false; version = value?.get("version")?.asLong ?: 0
        reason = ""; key = UUID.randomUUID().toString(); pending = false; history = null; sample = ""; prior = "0"
    }
    fun body() = mapOf("expectedVersion" to version, "name" to name.trim(), "kind" to kind, "scope" to area,
        "term" to term.trim(), "thresholdCount" to threshold.toInt(), "windowSeconds" to window.toInt(), "enabled" to enabled, "reason" to reason.trim())
    val valid = name.isNotBlank() && name.length <= 80 && reason.isNotBlank() && reason.length <= 300 &&
        (kind != "WORD" || term.isNotBlank()) && term.length <= 100 && threshold.toIntOrNull() in 2..100 && window.toIntOrNull() in 10..3600
    val back = { if (!busy) { if (selected == null || blocked) onExit() else { selected = null; pending = false; detection = null; history = null; run { load() } } } }
    BackHandler(onBack = back)
    LaunchedEffect(api) { run { if (selected != null && tab == "detections") detection = request("/detections/$selected") else load() } }
    Scaffold(topBar = { AdminTopBar("운영 규칙", back, enabled = !busy) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (blocked) TextButton(onExit) { Text("닫기") }
            else if (selected == null) {
                AdminPageHeading("공개 콘텐츠 검토", "규칙에 맞는 글을 검토 후보로 모읍니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rules" to "규칙", "detections" to "검토 후보").forEach { (value, label) ->
                        AdminFilterChip(tab == value, { tab = value; page = null; run { load() } }, label = { Text(label) }, enabled = !busy)
                    }
                }
                if (tab == "rules") OutlinedButton({ edit(null) }, enabled = !busy) { Text("규칙 추가") }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(false to "미검토", true to "검토 완료").forEach { (value, label) ->
                    AdminFilterChip(reviewed == value, { reviewed = value; page = null; run { load() } }, label = { Text(label) }, enabled = !busy)
                } }
                if (page?.rows("items")?.isEmpty() == true) Text("표시할 항목이 없습니다.")
                page?.rows("items")?.takeIf { it.isNotEmpty() }?.let { items -> AdminListGroup { items.forEachIndexed { index, item ->
                    AdminListRow(item.text(if (tab == "rules") "name" else "ruleName"),
                        if (tab == "rules") "${ruleKindLabel(item.text("kind"))} · ${ruleScopeLabel(item.text("scope"))}" else item.text("summary"),
                        enabled = !busy, status = { if (tab == "rules") AdminBadge(if (item.get("enabled").asBoolean) "사용 중" else "비활성", complete = !item.get("enabled").asBoolean) }) {
                        if (tab == "rules") edit(item)
                        else { selected = item.text("id"); reason = ""; key = UUID.randomUUID().toString(); pending = false; run { detection = request("/detections/$selected") } }
                    }
                    if (index < items.lastIndex) AdminRowDivider()
                } } }
                if (!page?.text("nextCursor").isNullOrBlank()) TextButton({ run { load(true) } }, enabled = !busy) { Text("더 불러오기") }
                TextButton({ run { load() } }, enabled = !busy) { Text("새로고침") }
            } else if (tab == "rules") {
                val editable = !busy && !pending
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("규칙 이름") }, enabled = editable, isError = name.length > 80)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WORD", "REPEAT", "RATE").forEach { value -> AdminFilterChip(kind == value, { kind = value }, label = { Text(ruleKindLabel(value)) }, enabled = editable) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("POST", "COMMENT", "MATE").forEach { value -> AdminFilterChip(area == value, { area = value }, label = { Text(ruleScopeLabel(value)) }, enabled = editable) }
                }
                if (kind == "WORD") {
                    OutlinedTextField(term, { term = it }, Modifier.fillMaxWidth(), label = { Text("탐지할 단어·문구") }, enabled = editable, isError = term.length > 100)
                    Text("문자 폭·대소문자·연속 공백을 통일한 뒤 포함 여부를 확인합니다. 정규식은 사용하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("기준 횟수 (2~100건)") }, enabled = editable)
                    OutlinedTextField(window, { window = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("시간 구간 (10~3600초)") }, enabled = editable)
                    Text("같은 작성자·영역의 새 글만 셉니다. 현재 작성도 1건에 포함하며 수정은 횟수에 더하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("규칙 사용"); Switch(enabled, { enabled = it }, enabled = editable) }
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("변경 사유") }, enabled = editable, isError = reason.length > 300)
                if (pending) Text("입력값을 유지하고 같은 요청으로 저장 결과를 확인합니다.")
                AdminPrimaryButton({ run { pending = true; request("/$selected", "PUT", body(), key); pending = false; selected = null; load() } }, enabled = !busy && valid) { Text(if (pending) "저장 결과 확인" else "규칙 저장") }
                HorizontalDivider()
                Text("저장 전 샘플 검사", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), label = { Text("가상 샘플 (저장되지 않음)") }, enabled = editable, isError = sample.length > 5000)
                if (kind != "WORD") OutlinedTextField(prior, { prior = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("시간 구간 내 이전 해당 작성 수") }, enabled = editable)
                TextButton({ run { val result = request("/preview", "POST", mapOf("rule" to body(), "sampleText" to sample, "priorMatchingCount" to prior.toInt()))
                    message = "${if (result.get("matched").asBoolean) "탐지됨" else "탐지되지 않음"} · ${result.text("explanation")}" } }, enabled = editable && valid && sample.length <= 5000 && prior.toIntOrNull() in 0..100) { Text("샘플 검사") }
                TextButton({ run { history = request("/$selected/history") } }, enabled = editable) { Text("변경 이력") }
                history?.rows("items")?.forEach { revision -> Text("버전 ${revision.getAsJsonObject("rule").text("version")} · ${revision.text("reason")}\n${appealTime(revision.text("createdAt"))}") }
                if (!history?.text("nextCursor").isNullOrBlank()) TextButton({ run {
                    val next = request("/$selected/history?cursor=${URLEncoder.encode(history!!.text("nextCursor"), "UTF-8")}")
                    val items = com.google.gson.JsonArray(); (history!!.rows("items") + next.rows("items")).forEach(items::add); next.add("items", items); history = next
                } }, enabled = editable) { Text("이력 더 불러오기") }
            } else {
                detection?.let { item ->
                    Text(item.text("ruleName"), style = MaterialTheme.typography.headlineSmall)
                    Text(item.text("summary"))
                    AdminSectionHeading("현재 공개 원문")
                    AdminPanel { Text(item.text("currentText").ifBlank { "삭제되었거나 현재 공개된 원문이 없습니다." }) }
                    Text("회원 ID: ${item.text("userId")}\n콘텐츠 ID: ${item.text("contentId")}", style = MaterialTheme.typography.bodySmall)
                    if (!item.get("reviewed").asBoolean || pending) {
                        OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("검토 메모") }, enabled = !busy && !pending, isError = reason.length > 300)
                        AdminPrimaryButton({ run { pending = true; request("/detections/$selected/review", "POST", mapOf("reason" to reason.trim()), key)
                            pending = false; detection = request("/detections/$selected") } }, enabled = !busy && reason.isNotBlank() && reason.length <= 300) { Text(if (pending) "처리 결과 확인" else "검토 완료") }
                        Text("검토 완료는 후보의 확인 상태만 변경합니다.", style = MaterialTheme.typography.bodySmall)
                    } else Text("검토 완료: ${item.text("reviewReason")}")
                }
                TextButton({ run { detection = request("/detections/$selected") } }, enabled = !busy && !pending) { Text("최신 내용 조회") }
            }
        }
    }
}

private fun ruleKindLabel(value: String) = when (value) { "WORD" -> "단어 일치"; "REPEAT" -> "동일 내용 반복"; "RATE" -> "과다 작성"; else -> value }
private fun ruleScopeLabel(value: String) = when (value) { "POST" -> "게시물"; "COMMENT" -> "댓글"; "MATE" -> "메이트"; else -> value }
