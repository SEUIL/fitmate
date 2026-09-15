package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminOperatorScreen(api: FitMateApiClient, onExit: () -> Unit) {
    val repository = remember(api) { ServerAdminRepository(api) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<OperatorPageData?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var accountActive by rememberSaveable { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf("MODERATOR") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var reason by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableLongStateOf(0) }
    var key by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var lookup by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var authDialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AdminError?>(null) }
    val blocked = error in listOf(AdminError.SESSION_EXPIRED, AdminError.FORBIDDEN)

    suspend fun execute(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
            if (error == AdminError.REAUTH_REQUIRED) { token = null; authDialog = true }
            if (error in listOf(AdminError.SESSION_EXPIRED, AdminError.FORBIDDEN)) { page = null; token = null; authDialog = false }
        } finally { busy = false }
    }
    fun edit(value: OperatorData) {
        selected = value.userId; name = value.nickname; accountActive = value.accountStatus == "ACTIVE"
        role = value.role ?: "MODERATOR"; enabled = value.enabled || value.role == null
        reason = ""; version = requireNotNull(page).version; key = UUID.randomUUID().toString(); pending = false
    }
    suspend fun save() {
        if (token == null) { authDialog = true; return }
        pending = true
        execute {
            repository.changeOperator(requireNotNull(selected), role, enabled, reason.trim(), version, key, requireNotNull(token))
            pending = false; selected = null
            page = repository.operators()
        }
        if (error in listOf(AdminError.VERSION_CONFLICT, AdminError.VALIDATION, AdminError.ALREADY_PROCESSED, AdminError.IDEMPOTENCY_CONFLICT)) pending = false
    }
    val back = {
        if (!busy) {
            if (blocked || selected == null) onExit()
            else { selected = null; pending = false; scope.launch { execute { page = repository.operators() } } }
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(repository) { execute { page = repository.operators() } }
    Scaffold(topBar = { AdminTopBar("운영자 권한", back, enabled = !busy) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (blocked) TextButton(onExit) { Text("닫기") }
            else if (selected != null) {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Text(requireNotNull(selected), style = MaterialTheme.typography.bodySmall)
                if (!accountActive) Text("활성 회원이 아니므로 권한 부여는 불가능합니다.")
                Text("역할", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MODERATOR", "ADMIN", "SUPER_ADMIN").forEach { value ->
                        AdminFilterChip(role == value, { role = value; key = UUID.randomUUID().toString() },
                            enabled = !busy && !pending, label = { Text(operatorRoleLabel(value)) })
                    }
                }
                Text(when (role) { "SUPER_ADMIN" -> "운영자 권한 부여·변경·회수까지 관리합니다."; "ADMIN" -> "기간 정지와 서비스 운영 도구를 관리합니다."; else -> "신고 검토·반려·경고를 처리합니다." },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("운영자 권한 사용")
                    Switch(enabled, { enabled = it; key = UUID.randomUUID().toString() }, enabled = !busy && !pending)
                }
                OutlinedTextField(reason, { reason = it; key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(),
                    enabled = !busy && !pending, label = { Text("변경 사유 (필수)") }, minLines = 2, isError = reason.length > 300)
                Text("저장하면 대상 회원의 기존 로그인 세션이 종료됩니다. 본인 권한은 변경할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
                if (pending) {
                    Text("처리 결과를 확인해야 합니다. 입력을 유지한 채 같은 요청으로 확인합니다.")
                    AdminPrimaryButton({ scope.launch { save() } }, enabled = !busy) { Text("처리 결과 확인") }
                } else AdminPrimaryButton({ if (token == null) authDialog = true else confirm = true },
                    enabled = !busy && page != null && selected != page?.actorId && reason.isNotBlank() && reason.length <= 300 && (!enabled || accountActive) && error != AdminError.VERSION_CONFLICT) { Text("권한 변경") }
                if (error == AdminError.VERSION_CONFLICT) TextButton(back) { Text("최신 목록 다시 조회") }
            } else {
                AdminPageHeading("운영팀 관리", "회원 ID로 대상을 확인한 뒤 역할을 부여하세요.")
                OutlinedTextField(lookup, { lookup = it }, Modifier.fillMaxWidth(), label = { Text("회원 ID") }, singleLine = true, enabled = !busy)
                OutlinedButton({ scope.launch { execute { edit(repository.operatorCandidate(lookup.trim())) } } },
                    enabled = !busy && page != null && runCatching { UUID.fromString(lookup.trim()) }.isSuccess) { Text("회원 확인") }
                page?.items?.takeIf { it.isNotEmpty() }?.let { items -> AdminListGroup {
                    items.forEachIndexed { index, value ->
                        AdminListRow(value.nickname + if (value.userId == page?.actorId) " · 나" else "",
                            if (value.enabled) operatorRoleLabel(value.role.orEmpty()) else "권한 회수됨",
                            enabled = !busy && value.userId != page?.actorId, icon = androidx.compose.material.icons.Icons.Outlined.PersonOutline) { edit(value) }
                        if (index < items.lastIndex) AdminRowDivider()
                    }
                } }
                if (page?.nextCursor != null) TextButton({ scope.launch { execute {
                    val current = requireNotNull(page)
                    val next = repository.operators(current.nextCursor)
                    if (next.version != current.version) page = repository.operators()
                    else page = next.copy(items = (current.items + next.items).distinctBy { it.userId })
                } } }, enabled = !busy) { Text("더 불러오기") }
                TextButton({ scope.launch { execute { page = repository.operators() } } }, enabled = !busy) { Text("새로고침") }
            }
        }
    }
    if (authDialog) AlertDialog(onDismissRequest = { if (!busy) { authDialog = false; code = "" } },
        title = { Text("테스트 인증") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("개발 환경의 고정 코드입니다. 인증 후 5분 동안 권한 변경을 확인할 수 있습니다.")
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6자리 테스트 코드") },
                enabled = !busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton({ scope.launch { execute { token = repository.authenticateOperator(code); code = ""; authDialog = false } } },
            enabled = !busy && code.length == 6) { Text("인증") } }, dismissButton = { TextButton({ authDialog = false; code = "" }, enabled = !busy) { Text("취소") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("권한을 변경할까요?") },
        text = { Text("$name · ${if (enabled) operatorRoleLabel(role) else "권한 회수"}\n${reason.trim()}\n대상 회원은 다시 로그인해야 합니다.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { save() } }) { Text("변경 적용") } },
        dismissButton = { TextButton({ confirm = false }) { Text("취소") } })
}

private fun operatorRoleLabel(value: String) = when (value) { "SUPER_ADMIN" -> "최고 관리자"; "ADMIN" -> "관리자"; "MODERATOR" -> "검토자"; else -> value }
