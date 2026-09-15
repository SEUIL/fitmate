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
internal fun AdminAppealScreen(api: FitMateApiClient, onNavigate: ((Int) -> Unit)? = null, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf("RECEIVED") }
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var decision by rememberSaveable { mutableStateOf("REJECTED") }
    var version by rememberSaveable { mutableLongStateOf(0) }
    var key by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun request(path: String, body: Any? = null, commandKey: String? = null) = withContext(Dispatchers.IO) {
        api.request(if (body == null) "GET" else "POST", "admin/appeals$path", body,
            headers = commandKey?.let { mapOf("Idempotency-Key" to it) } ?: emptyMap()).asJsonObject
    }
    suspend fun load(append: Boolean = false) {
        val cursor = if (append) page?.text("nextCursor").orEmpty() else ""
        val result = request("?status=$filter" + if (cursor.isBlank()) "" else "&cursor=${URLEncoder.encode(cursor, "UTF-8")}")
        if (append) {
            val merged = com.google.gson.JsonArray()
            (page?.rows("items").orEmpty() + result.rows("items")).distinctBy { it.text("id") }.forEach(merged::add)
            result.add("items", merged)
        }
        page = result
    }
    suspend fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        try { work() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            message = if (failure is ApiFailure) failure.message else "연결을 확인한 뒤 다시 시도해 주세요."
            if ((failure as? ApiFailure)?.status in listOf(401, 403)) { blocked = true; detail = null; page = null }
            if ((failure as? ApiFailure)?.status in listOf(400, 404, 409)) pending = false
        } finally { busy = false }
    }
    suspend fun save() = run {
        pending = true
        detail = request("/$selected/decision", mapOf("expectedVersion" to version, "status" to decision,
            "publicReason" to reason.trim(), "internalNote" to note.trim().ifBlank { null }), key)
        pending = false; reason = ""; note = ""; key = UUID.randomUUID().toString()
    }
    val back = { if (!busy) { if (selected == null || blocked) onExit() else { selected = null; detail = null; pending = false; scope.launch { run { load() } } } } }
    BackHandler(onBack = back)
    LaunchedEffect(api) { run { if (selected == null) load() else detail = request("/$selected") } }
    val loading = busy
    val visiblePage = page
    val visibleDetail = detail
    Scaffold(topBar = { AdminTopBar(if (selected == null && onNavigate != null) "운영자 센터" else "이의신청", back, enabled = !busy) },
        bottomBar = { if (selected == null && onNavigate != null) AdminNavigation(2, enabled = !busy) { onNavigate(it) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (blocked) TextButton(onExit) { Text("닫기") }
            else if (selected == null) {
                AdminPageHeading("제재 재검토", "회원의 이의신청을 확인하고 처리하세요.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RECEIVED", "REVIEWING", "ACCEPTED", "REJECTED").forEach { status ->
                        AdminFilterChip(filter == status, { filter = status; page = null; scope.launch { run { load() } } }, label = {
                            Text(when (status) { "RECEIVED" -> "접수"; "REVIEWING" -> "검토 중"; "ACCEPTED" -> "수용"; else -> "기각" })
                        }, enabled = !busy)
                    }
                }
                if (visiblePage?.rows("items")?.isEmpty() == true) Text("해당 상태의 신청이 없습니다.")
                visiblePage?.rows("items")?.takeIf { it.isNotEmpty() }?.let { items -> AdminListGroup {
                    items.forEachIndexed { index, item ->
                        AdminListRow(item.text("nickname").ifBlank { "회원" }, item.text("statement"), enabled = !busy,
                            status = { AdminBadge(appealStatusLabel(item.text("status")), attention = item.text("status") == "RECEIVED",
                                complete = item.text("status") in listOf("ACCEPTED", "REJECTED")) }) {
                            selected = item.text("id"); reason = ""; note = ""; pending = false; key = UUID.randomUUID().toString()
                            scope.launch { run { detail = request("/$selected") } }
                        }
                        if (index < items.lastIndex) AdminRowDivider()
                    }
                } }
                if (!visiblePage?.text("nextCursor").isNullOrBlank()) TextButton({ scope.launch { run { load(true) } } }, enabled = !busy) { Text("더 불러오기") }
                TextButton({ scope.launch { run { load() } } }, enabled = !busy) { Text("새로고침") }
            } else {
                visibleDetail?.let { value ->
                    Text(value.text("nickname").ifBlank { "회원" }, style = MaterialTheme.typography.headlineSmall)
                    AdminPanel {
                        Text("원래 제재", style = MaterialTheme.typography.labelLarge)
                        AppealSanctionContent(value.getAsJsonObject("sanction"))
                    }
                    AdminSectionHeading("신청 내용과 처리 결과")
                    AppealResultContent(value)
                    value.text("internalNote").takeIf { it.isNotBlank() }?.let { Text("내부 메모: $it") }
                    if (value.get("canReview").asBoolean) {
                        if (value.text("status") == "RECEIVED") AdminPrimaryButton({ scope.launch { run {
                            detail = request("/$selected/claim", mapOf("expectedVersion" to value.get("version").asLong))
                        } } }, enabled = !busy) { Text("검토 시작") }
                        if (value.text("status") == "REVIEWING" || pending) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ACCEPTED" to "수용", "REJECTED" to "기각").forEach { (status, label) ->
                                    AdminFilterChip(decision == status, { decision = status }, label = { Text(label) }, enabled = !busy && !pending)
                                }
                            }
                            Text(if (decision == "ACCEPTED") "이 제재를 해제합니다. 다른 유효한 정지는 유지됩니다." else "제재를 유지하고 신청자에게 사유를 안내합니다.")
                            OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("신청자에게 보여줄 사유") }, minLines = 3, enabled = !busy && !pending, isError = reason.length > 500)
                            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("내부 메모 (선택)") }, enabled = !busy && !pending, isError = note.length > 500)
                            if (pending) Text("응답을 확인하지 못했습니다. 같은 요청으로 처리 결과를 확인하세요.")
                            AdminPrimaryButton({ if (pending) scope.launch { save() } else { version = value.get("version").asLong; confirm = true } },
                                enabled = !busy && reason.isNotBlank() && reason.length <= 500 && note.length <= 500) { Text(if (pending) "처리 결과 확인" else "검토 결과 저장") }
                        }
                    } else Text("이 신청을 처리할 권한이 없습니다.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ scope.launch { run { detail = request("/$selected") } } }, enabled = !busy && !pending) { Text("최신 내용 조회") }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("${if (decision == "ACCEPTED") "수용" else "기각"} 처리할까요?") },
        text = { Text(reason.trim()) }, confirmButton = { TextButton({ confirm = false; scope.launch { save() } }) { Text("처리") } },
        dismissButton = { TextButton({ confirm = false }) { Text("취소") } })
}
