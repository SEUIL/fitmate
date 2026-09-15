package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
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
internal fun AppealAccessScreen(api: FitMateApiClient, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<JsonObject?>(null) }
    var history by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var statement by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun load(append: Boolean = false) {
        val cursor = if (append) page?.text("nextCursor").orEmpty() else ""
        val resource = (if (history) "appeals" else "sanctions") + if (cursor.isBlank()) "" else "?cursor=${URLEncoder.encode(cursor, "UTF-8") }"
        val result = withContext(Dispatchers.IO) { api.appealRequest("GET", resource, requireNotNull(token)).asJsonObject }
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
                if ((failure as? ApiFailure)?.status == 401) { token = null; page = null }
                if ((failure as? ApiFailure)?.status in listOf(400, 404, 409)) pending = false
            } finally { busy = false }
        }
    }
    val back = { if (!busy) { if (selected != null && token != null) { selected = null; pending = false; run { load() } } else onExit() } }
    BackHandler(onBack = back)
    Scaffold(topBar = { TopAppBar(title = { Text("제재 안내 · 이의신청") }, navigationIcon = {
        IconButton(back, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (token == null) {
                Text("제재 내용을 확인하세요", style = MaterialTheme.typography.headlineSmall)
                Text("정지 중에도 신청할 수 있어요. 계정을 확인하면 제재 내역과 이의신청 결과를 볼 수 있습니다.")
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("이메일") }, enabled = !busy)
                AuthSecretField(password, { password = it }, "비밀번호", !busy)
                Button({ run {
                    val result = withContext(Dispatchers.IO) { api.request("POST", "auth/appeal-sessions",
                        mapOf("email" to email.trim(), "password" to password), authenticated = false).asJsonObject }
                    token = result.text("token"); password = ""; load()
                } }, enabled = !busy && email.isNotBlank() && password.isNotBlank()) { Text("계정 확인") }
                Text("이 화면을 닫으면 다시 인증해야 합니다.", style = MaterialTheme.typography.bodySmall)
            } else if (selected != null) {
                val sanction = page?.rows("items")?.firstOrNull { it.text("id") == selected }
                sanction?.let { AppealSanctionContent(it) }
                Text("재검토가 필요한 이유", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(statement, { statement = it; key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(),
                    label = { Text("신청 내용") }, minLines = 5, enabled = !busy && !pending,
                    supportingText = { Text("${statement.length} / 2000") }, isError = statement.length > 2000)
                if (pending) Text("접수 결과를 확인하지 못했어요. 같은 신청으로 다시 확인해 주세요.")
                Button({ run {
                    pending = true
                    withContext(Dispatchers.IO) { api.appealRequest("POST", "appeals", requireNotNull(token), mapOf("sanctionId" to selected, "statement" to statement.trim()), key) }
                    pending = false; selected = null; statement = ""; key = UUID.randomUUID().toString(); load()
                    message = "이의신청이 접수되었습니다. 신청 내역에서 결과를 확인할 수 있어요."
                } }, enabled = !busy && statement.isNotBlank() && statement.length <= 2000) { Text(if (pending) "접수 결과 확인" else "이의신청 접수") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "제재 내역", true to "신청 내역").forEach { (value, label) ->
                        FilterChip(history == value, { history = value; page = null; run { load() } }, label = { Text(label) }, enabled = !busy)
                    }
                }
                if (page?.rows("items")?.isEmpty() == true) Text(if (history) "아직 신청 내역이 없어요." else "제재 내역이 없어요.")
                page?.rows("items")?.forEach { item ->
                    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (history) AppealResultContent(item)
                        else {
                            AppealSanctionContent(item)
                            item.get("latestAppeal")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { AppealResultContent(it) }
                            if (item.get("canAppeal").asBoolean) TextButton({ selected = item.text("id"); statement = ""; pending = false; key = UUID.randomUUID().toString() }, enabled = !busy) { Text("이의신청") }
                        }
                    } }
                }
                if (!page?.text("nextCursor").isNullOrBlank()) TextButton({ run { load(true) } }, enabled = !busy) { Text("더 불러오기") }
                TextButton({ run { load() } }, enabled = !busy) { Text("새로고침") }
            }
        }
    }
}

@Composable internal fun AppealSanctionContent(value: JsonObject) {
    Text(if (value.text("action") == "SUSPEND") "기간 정지" else "경고", style = MaterialTheme.typography.titleMedium)
    Text(value.text("reason"))
    Text("시작 ${appealTime(value.text("startsAt"))}", style = MaterialTheme.typography.bodySmall)
    value.text("endsAt").takeIf { it.isNotBlank() }?.let { Text("종료 ${appealTime(it)}", style = MaterialTheme.typography.bodySmall) }
    if (value.text("revokedAt").isNotBlank()) Text("제재 해제됨", color = MaterialTheme.colorScheme.primary)
}

@Composable internal fun AppealResultContent(value: JsonObject) {
    Text(appealStatusLabel(value.text("status")), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(value.text("statement"))
    value.text("publicReason").takeIf { it.isNotBlank() }?.let { Text("처리 결과: $it") }
    Text(appealTime(value.text("reviewedAt").ifBlank { value.text("createdAt") }), style = MaterialTheme.typography.bodySmall)
}

internal fun appealTime(value: String): String = runCatching { java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrDefault(value)

internal fun appealStatusLabel(value: String) = when (value) {
    "RECEIVED" -> "접수됨"; "REVIEWING" -> "검토 중"; "ACCEPTED" -> "수용 · 제재 해제"; "REJECTED" -> "기각 · 제재 유지"; else -> value
}
