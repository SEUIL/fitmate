package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.BuildConfig
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminCenter(stateOverride: AdminUiState? = null, apiClient: com.fitmate.app.data.remote.FitMateApiClient? = null, onExit: () -> Unit) {
    val api = apiClient ?: rememberFitMateApiClient()
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    if (tool != null) {
        when (tool) {
            "공지 관리" -> NoticeScreen(api, managing = true) { tool = null }
            "카테고리 관리" -> AdminCategoryScreen(api) { tool = null }
            else -> AdminSystemScreen(api) { tool = null }
        }
        return
    }
    val state = stateOverride ?: remember(api) { AdminUiState(ServerAdminRepository(api)) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(state) { state.load() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var sanction by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<AdminReportStatus?>(null) }
    val snapshot = state.snapshot
    val reports = snapshot?.reports.orEmpty()
    var firstSearch by remember { mutableStateOf(true) }
    LaunchedEffect(query, filter, selectedId) {
        if (selectedId != null) return@LaunchedEffect
        if (firstSearch) { firstSearch = false; if (query.isEmpty() && filter == null) return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        while (state.busy) kotlinx.coroutines.delay(50)
        state.search(query, filter)
    }
    LaunchedEffect(selectedId) {
        val id = selectedId
        if (id == null) state.closeDetail() else {
            focusManager.clearFocus()
            keyboard?.hide()
            while (state.busy) kotlinx.coroutines.delay(50)
            state.open(id)
        }
    }
    val selected = state.selected?.takeIf { it.id == selectedId }
    val scrollState = remember(tab, selectedId, sanction) { androidx.compose.foundation.ScrollState(0) }
    LaunchedEffect(state.error) { if (state.error != null) scrollState.animateScrollTo(0) }
    val back = { if (!state.busy) { if (sanction) sanction = false else if (selectedId != null) selectedId = null else onExit() } }
    BackHandler { back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(
            title = { Text(if (sanction) "회원 제재" else if (selected != null) "신고 상세" else "운영자 센터", fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") } },
            actions = { Text(snapshot?.role ?: if (stateOverride != null) "TEST" else "", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(end = 20.dp)) }
        ) },
        bottomBar = {
            if (selected == null) NavigationBar {
                val icons = listOf(Icons.Outlined.GridView, Icons.Outlined.Flag, Icons.Outlined.PeopleOutline, Icons.AutoMirrored.Outlined.Article, Icons.Outlined.MoreHoriz)
                listOf("현황", "신고", "회원", "콘텐츠", "더보기").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, enabled = !state.busy, icon = { Icon(icons[index], null, Modifier.size(23.dp)) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (snapshot?.remote == true) "서버 연결 · 조치는 실제 계정에 적용됩니다" else if (stateOverride != null) "테스트 데이터" else "운영자 권한 확인", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("불러오거나 처리하는 중…", fontSize = 13.sp)
            }
            state.error?.let { error -> AdminCard {
                Text(error.message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                if (state.accessBlocked) TextButton(onExit) { Text("운영자 센터 종료") }
                else TextButton({ scope.launch {
                    if (state.load()) { sanction = false; selectedId = null }
                } }, enabled = !state.busy) { Text("최신 상태 다시 조회") }
            } }
            when {
                snapshot == null -> if (!state.busy && state.error == null) Text("운영 데이터를 준비하고 있습니다.")
                selected != null && sanction -> key(selected.id) {
                    AdminSanctionForm(selected, enabled = !state.busy && !state.requiresRefresh, onCancel = { if (!state.busy) sanction = false }) { command ->
                        scope.launch { if (state.submit(command)) sanction = false }
                    }
                }
                selected != null -> {
                    AdminCard {
                        AdminStatus(selected)
                        Text(selected.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("${selected.id} · ${selected.type} · ${selected.time}", fontSize = 12.sp)
                        HorizontalDivider()
                        Text("신고 사유", fontSize = 12.sp)
                        Text(selected.reason, fontWeight = FontWeight.SemiBold)
                        Text("대상 회원 · ${selected.member}", fontSize = 14.sp)
                    }
                    AdminHeading("신고 근거")
                    AdminCard {
                        Text("신고 대상 본문", fontWeight = FontWeight.SemiBold)
                        Text(selected.evidence ?: "원문 근거를 제공할 수 없습니다.", fontSize = 14.sp)
                        selected.reporterStatement?.let { Text("신고자 설명: $it", fontSize = 14.sp) }
                    }
                    AdminHeading("처리 이력")
                    Text("• ${selected.time} 신고 접수", fontSize = 14.sp)
                    selected.history.forEach { Text(it, fontSize = 14.sp) }
                    selected.decision?.let { AdminCard { Text("처리 결과", fontWeight = FontWeight.SemiBold); Text(it) } }
                    when (selected.status) {
                        AdminReportStatus.RECEIVED -> Button({ scope.launch { state.claim(selected) } }, Modifier.fillMaxWidth(), enabled = !state.busy && !state.requiresRefresh) { Text("검토 시작") }
                        AdminReportStatus.REVIEWING -> Button({ sanction = true }, Modifier.fillMaxWidth(), enabled = !state.busy && !state.requiresRefresh && selected.allowedActions.isNotEmpty()) { Text("반려 / 조치 선택") }
                        else -> Text("완료된 신고는 다시 처리할 수 없습니다.", fontSize = 12.sp)
                    }
                }
                tab == 0 -> {
                    Text("최근 24시간 운영 현황", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("처리가 필요한 항목부터 확인하세요.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("대기 신고", snapshot.pendingCount.toString(), Modifier.weight(1f)) { tab = 1; filter = null; query = "" }
                        AdminMetric("신규 회원", snapshot.metrics?.getOrNull(1)?.toString() ?: "—", Modifier.weight(1f)) { tab = 2 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("운동 완료", snapshot.metrics?.getOrNull(2)?.toString() ?: "—", Modifier.weight(1f)) { tab = 4 }
                        AdminMetric("판독 실패", snapshot.metrics?.getOrNull(3)?.toString() ?: "—", Modifier.weight(1f)) { tab = 4 }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AdminHeading("최근 신고")
                        TextButton({ tab = 1; filter = null; query = "" }) { Text("전체 보기") }
                    }
                    snapshot.recentReports.forEach { report -> AdminReportRow(report) { selectedId = report.id } }
                    AdminHeading("최근 처리")
                    if (snapshot.history.isEmpty()) Text("아직 처리한 신고가 없습니다.", fontSize = 14.sp)
                    snapshot.history.take(3).forEach { Text(it, fontSize = 14.sp) }
                }
                tab == 1 -> {
                    AdminHeading("신고 관리")
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("신고 번호 · 닉네임 · 내용 검색") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == null, { filter = null }, label = { Text("전체") })
                        AdminReportStatus.entries.forEach { status -> FilterChip(filter == status, { filter = status }, label = { Text(status.label) }) }
                    }
                    Text("${reports.size}건", fontSize = 12.sp)
                    if (reports.isEmpty()) AdminCard { Text("조건에 맞는 신고가 없습니다."); TextButton({ query = ""; filter = null }) { Text("검색 초기화") } }
                    reports.forEach { report -> AdminReportRow(report) { selectedId = report.id } }
                    if (snapshot.nextCursor != null) TextButton({ scope.launch { state.more() } }, enabled = !state.busy) { Text("더 보기") }
                }
                tab == 2 -> {
                    AdminHeading("회원")
                    Text("신고에 연결된 회원입니다.", fontSize = 14.sp)
                    snapshot.members.forEach { member -> AdminCard { Text(member) } }
                    if (snapshot.members.isEmpty()) Text("신고에 연결된 회원이 없습니다.")
                }
                tab == 3 -> {
                    AdminHeading("콘텐츠")
                    Text("신고된 콘텐츠 미리보기입니다. 숨김·복구·삭제 기능은 아직 제공하지 않습니다.", fontSize = 14.sp)
                    snapshot.contents.forEach { report -> AdminReportRow(report) { selectedId = report.id } }
                }
                else -> {
                    AdminHeading("운영 도구")
                    listOf("공지 관리", "카테고리 관리", "이의신청", "운영 규칙", "시스템 현황", "운영자 권한").forEach { title -> AdminCard {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        if (title in listOf("공지 관리", "카테고리 관리", "시스템 현황") && snapshot.remote) {
                            TextButton({ tool = title }, enabled = snapshot.role in listOf("ADMIN", "SUPER_ADMIN")) { Text(if (title == "시스템 현황") "인바디 판독 현황 보기" else "열기") }
                            if (snapshot.role !in listOf("ADMIN", "SUPER_ADMIN")) Text("ADMIN 이상 조회 가능", fontSize = 12.sp)
                        } else Text("후속 화면 · 백엔드 연동 준비 중", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    AdminHeading("최근 처리 기록")
                    if (snapshot.history.isEmpty()) Text("처리 기록 없음")
                    snapshot.history.forEach { AdminCard { Text(it, fontSize = 14.sp) } }
                    Text("처리 기록은 서버에 저장됩니다. MFA와 운영 전용 세션은 아직 지원하지 않습니다.", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun AdminHeading(text: String) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }

@Composable private fun AdminCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun AdminMetric(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    AdminCard(modifier.clickable(onClick = onClick)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun AdminStatus(report: AdminReport) {
    Text("${report.type} · ${report.status.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun AdminReportRow(report: AdminReport, onClick: () -> Unit) {
    AdminCard(Modifier.clickable(onClick = onClick)) {
        AdminStatus(report)
        Text(report.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("${report.member} · ${report.time}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AdminSanctionForm(report: AdminReport, enabled: Boolean, onCancel: () -> Unit, onSave: (AdminDecisionCommand) -> Unit) {
    var action by rememberSaveable { mutableStateOf(report.allowedActions.firstOrNull { it == AdminAction.WARNING } ?: AdminAction.NONE) }
    var reasonCode by rememberSaveable { mutableStateOf(if (action == AdminAction.NONE) AdminDecisionReason.NO_VIOLATION else AdminDecisionReason.POLICY_VIOLATION) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val idempotencyKey = rememberSaveable(report.id, report.version, action, reasonCode, days, reason, note) { UUID.randomUUID().toString() }
    val command = AdminDecisionCommand(report.id, report.version, action, reasonCode, reason.trim(),
        days.takeIf { action == AdminAction.SUSPEND }, note.trim().takeIf { it.isNotBlank() }, idempotencyKey)
    AdminCard { Text(report.member, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("연결 신고 · ${report.id}", fontSize = 12.sp) }
    AdminHeading("조치 종류")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        report.allowedActions.forEach { value -> FilterChip(action == value, {
            action = value
            reasonCode = if (value == AdminAction.NONE) AdminDecisionReason.NO_VIOLATION else AdminDecisionReason.POLICY_VIOLATION
        }, enabled = enabled, label = { Text(value.label) }) }
    }
    if (action == AdminAction.SUSPEND) {
        Text("정지 기간", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 7, 30).forEach { value -> FilterChip(days == value, { days = value }, enabled = enabled, label = { Text("${value}일") }) }
        }
    }
    Text("처리 사유", fontWeight = FontWeight.SemiBold)
    val reasons = if (action == AdminAction.NONE) listOf(AdminDecisionReason.NO_VIOLATION, AdminDecisionReason.INSUFFICIENT_EVIDENCE)
        else listOf(AdminDecisionReason.POLICY_VIOLATION, AdminDecisionReason.SPAM, AdminDecisionReason.HARASSMENT)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEach { value -> FilterChip(reasonCode == value, { reasonCode = value }, enabled = enabled, label = { Text(value.label) }) }
    }
    OutlinedTextField(reason, { if (it.length <= 500) reason = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("처리 사유 및 사용자 안내 (필수)") }, minLines = 3, supportingText = { Text("${reason.length}/500 · 사용자에게 전달할 안내") })
    OutlinedTextField(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("내부 메모 (선택)") }, minLines = 2, supportingText = { Text("사용자에게 공개하지 않는 운영 메모") })
    Text(if (action == AdminAction.SUSPEND) "기간 동안 실제 회원의 로그인·서비스 이용이 제한됩니다." else "입력 내용을 확인한 뒤 신고에 적용합니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button({ confirm = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled && command.isValid()) { Text("처리 내용 확인") }
    TextButton(onCancel, Modifier.fillMaxWidth(), enabled = enabled) { Text("취소") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("조치를 적용할까요?") },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${report.member} · ${report.id}")
            Text("${action.label}${if (action == AdminAction.SUSPEND) " ${days}일" else ""}", fontWeight = FontWeight.Bold)
            Text("처리 사유: ${reasonCode.label}")
            Text(reason)
            Text("처리는 서버에 저장되며 기간 정지는 실제 계정에 적용됩니다. 알림 발송은 지원하지 않습니다.", fontSize = 12.sp)
        } },
        confirmButton = { TextButton({ confirm = false; onSave(command) }, enabled = enabled && command.isValid()) { Text("적용") } },
        dismissButton = { TextButton({ confirm = false }) { Text("돌아가기") } })
}
