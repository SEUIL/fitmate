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
    AdminDesignTheme { AdminCenterContent(stateOverride, apiClient, onExit) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCenterContent(stateOverride: AdminUiState?, apiClient: com.fitmate.app.data.remote.FitMateApiClient?, onExit: () -> Unit) {
    val api = apiClient ?: rememberFitMateApiClient()
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable("admin-four-tabs") { mutableIntStateOf(0) }
    val state = stateOverride ?: remember(api) { AdminUiState(ServerAdminRepository(api)) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(state) { state.load() }
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
    val scrollState = remember(tab, tool, selectedId, sanction) { androidx.compose.foundation.ScrollState(0) }
    LaunchedEffect(state.error) { if (state.error != null) scrollState.animateScrollTo(0) }
    val back = { if (!state.busy) { if (sanction) sanction = false else if (selectedId != null) selectedId = null else if (tool != null) tool = null else onExit() } }
    if (tool != null && tool !in listOf("신고 관련 회원", "신고 콘텐츠")) {
        when (tool) {
            "공지 관리" -> NoticeScreen(api, managing = true) { tool = null }
            "카테고리 관리" -> AdminCategoryScreen(api) { tool = null }
            "운영자 권한" -> AdminOperatorScreen(api) { tool = null }
            "운영 규칙" -> AdminRuleScreen(api) { tool = null }
            else -> AdminSystemScreen(api) { tool = null }
        }
        return
    }
    if (tab == 2 && !state.accessBlocked && snapshot != null) {
        AdminAppealScreen(api, onNavigate = { tab = it }) { tab = 0 }
        return
    }
    BackHandler { back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AdminTopBar(if (sanction) "검토 결과" else if (selectedId != null) "신고 상세" else tool ?: "운영자 센터",
            back, enabled = !state.busy, role = if (selectedId == null && tool == null) snapshot?.role else null) },
        bottomBar = {
            if (selectedId == null && tool == null) AdminNavigation(tab, enabled = !state.busy && snapshot != null && !state.accessBlocked) { tab = it }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AdminStatus(selected)
                        Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                        Text("${selected.member} · ${selected.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("신고 번호 · ${selected.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AdminHeading("신고 사유")
                    Text(selected.reason, style = MaterialTheme.typography.bodyMedium)
                    AdminHeading("신고 근거")
                    AdminCard {
                        Text("신고 대상 본문", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selected.evidence ?: "원문 근거를 제공할 수 없습니다.", fontSize = 14.sp)
                    }
                    selected.reporterStatement?.let { AdminHeading("신고자 설명"); Text(it, style = MaterialTheme.typography.bodyMedium) }
                    AdminHeading("처리 이력")
                    Text("• ${selected.time} 신고 접수", fontSize = 14.sp)
                    selected.history.forEach { Text(it, fontSize = 14.sp) }
                    selected.decision?.let { AdminCard { Text("처리 결과", fontWeight = FontWeight.SemiBold); Text(it) } }
                    when (selected.status) {
                        AdminReportStatus.RECEIVED -> AdminPrimaryButton({ scope.launch { state.claim(selected) } }, Modifier.fillMaxWidth(), enabled = !state.busy && !state.requiresRefresh) { Text("검토 시작") }
                        AdminReportStatus.REVIEWING -> AdminPrimaryButton({ sanction = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = !state.busy && !state.requiresRefresh && selected.allowedActions.isNotEmpty()) { Text("반려 / 조치 선택") }
                        else -> Text("완료된 신고는 다시 처리할 수 없습니다.", fontSize = 12.sp)
                    }
                }
                selectedId != null -> if (!state.busy && state.error == null) Text("신고 내용을 확인할 수 없습니다.")
                tool == "신고 관련 회원" -> {
                    AdminPageHeading("신고 관련 회원", "신고에 연결된 회원입니다.")
                    if (snapshot.members.isEmpty()) Text("신고에 연결된 회원이 없습니다.")
                    else AdminListGroup { snapshot.members.forEachIndexed { index, member ->
                        AdminListRow(member, "연결된 신고 확인", icon = Icons.Outlined.PersonOutline) { query = member; filter = null; tool = null; tab = 1 }
                        if (index < snapshot.members.lastIndex) AdminRowDivider()
                    } }
                }
                tool == "신고 콘텐츠" -> {
                    AdminPageHeading("신고 콘텐츠", "신고된 원문과 처리 내역을 확인하세요.")
                    if (snapshot.contents.isEmpty()) Text("신고된 콘텐츠가 없습니다.")
                    else AdminListGroup { snapshot.contents.forEachIndexed { index, report ->
                        AdminReportRow(report) { selectedId = report.id }
                        if (index < snapshot.contents.lastIndex) AdminRowDivider()
                    } }
                }
                tab == 0 -> {
                    AdminPageHeading("처리할 일을 한눈에", "최근 24시간 운영 현황")
                    AdminCard {
                        Text("대기 신고", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("접수 · 검토 중인 전체 신고", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${snapshot.pendingCount}건", fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
                        AdminPrimaryButton({ tab = 1; filter = null; query = "" }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("신고 목록 보기") }
                    }
                    AdminCard { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("신규 회원", snapshot.metrics?.getOrNull(1)?.toString() ?: "—", Modifier.weight(1f))
                        AdminMetric("운동 완료", snapshot.metrics?.getOrNull(2)?.toString() ?: "—", Modifier.weight(1f))
                        AdminMetric("판독 실패", snapshot.metrics?.getOrNull(3)?.toString() ?: "—", Modifier.weight(1f))
                    } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AdminHeading("최근 신고")
                        TextButton({ tab = 1; filter = null; query = "" }) { Text("전체 보기") }
                    }
                    if (snapshot.recentReports.isEmpty()) Text("최근 신고가 없습니다.", style = MaterialTheme.typography.bodySmall)
                    else AdminListGroup { snapshot.recentReports.forEachIndexed { index, report ->
                        AdminReportRow(report) { selectedId = report.id }
                        if (index < snapshot.recentReports.lastIndex) AdminRowDivider()
                    } }
                    AdminHeading("최근 처리")
                    AdminRecentActivity(snapshot.recentActivity ?: snapshot.history.map { AdminActivity("", it, "") })
                }
                tab == 1 -> {
                    AdminPageHeading("신고 관리", "신고 사유와 근거를 확인하고 처리하세요.")
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), label = { Text("신고 번호 · 닉네임 · 내용 검색") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminFilterChip(filter == null, { filter = null }, label = { Text("전체") })
                        AdminReportStatus.entries.forEach { status -> AdminFilterChip(filter == status, { filter = status }, label = { Text(status.label) }) }
                    }
                    Text("${reports.size}건", fontSize = 12.sp)
                    if (reports.isEmpty()) AdminCard { Text("조건에 맞는 신고가 없습니다."); TextButton({ query = ""; filter = null }) { Text("검색 초기화") } }
                    if (reports.isNotEmpty()) AdminListGroup { reports.forEachIndexed { index, report ->
                        AdminReportRow(report) { selectedId = report.id }
                        if (index < reports.lastIndex) AdminRowDivider()
                    } }
                    if (snapshot.nextCursor != null) TextButton({ scope.launch { state.more() } }, enabled = !state.busy) { Text("더 보기") }
                }
                else -> AdminToolsMenu(snapshot, enabled = !state.busy) { tool = it }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun AdminHeading(text: String) { AdminSectionHeading(text) }

@Composable private fun AdminRecentActivity(items: List<AdminActivity>) {
    AdminPanel {
        if (items.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("아직 처리한 신고가 없습니다.", style = MaterialTheme.typography.titleMedium)
                    Text("새로운 운영 기록이 여기에 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items.take(3).forEachIndexed { index, item ->
            val title = when (item.action) {
                "REPORT_VIEWED" -> "신고 열람"; "REPORT_CLAIMED" -> "신고 검토 시작"; "REPORT_DECIDED" -> "신고 처리 완료"
                "APPEAL_VIEWED" -> "이의신청 열람"; "APPEAL_CLAIMED" -> "이의신청 검토 시작"; "APPEAL_DECIDED" -> "이의신청 처리 완료"
                "NOTICE_CREATED" -> "공지 작성"; "NOTICE_EDITED" -> "공지 수정"; "NOTICE_PUBLISHED" -> "공지 게시"; "NOTICE_ENDED" -> "공지 종료"
                "CATEGORY_ORDER_CHANGED" -> "종목 순서 변경"; "OPERATOR_ROLE_CHANGED" -> "운영자 권한 변경"
                "RULE_CHANGED" -> "운영 규칙 변경"; "RULE_CANDIDATE_VIEWED" -> "검토 후보 열람"; "RULE_CANDIDATE_REVIEWED" -> "후보 검토 완료"
                else -> "운영 기록"
            }
            val description = when {
                item.summary == item.action -> ""
                item.action == "REPORT_DECIDED" -> when (item.summary) {
                    "WARNING 조치를 완료했습니다." -> "경고 조치를 적용했습니다."
                    "SUSPEND 조치를 완료했습니다." -> "기간 정지를 적용했습니다."
                    "NONE 조치를 완료했습니다." -> "신고를 반려했습니다."
                    else -> item.summary
                }
                item.action == "APPEAL_DECIDED" && item.summary.startsWith("ACCEPTED: ") -> "수용 · ${item.summary.removePrefix("ACCEPTED: ")}"
                item.action == "APPEAL_DECIDED" && item.summary.startsWith("REJECTED: ") -> "기각 · ${item.summary.removePrefix("REJECTED: ")}"
                else -> item.summary
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(when {
                            item.action.endsWith("VIEWED") -> Icons.Outlined.Visibility
                            item.action.endsWith("CLAIMED") -> Icons.Outlined.RateReview
                            item.action.startsWith("NOTICE") -> Icons.Outlined.Campaign
                            item.action == "OPERATOR_ROLE_CHANGED" -> Icons.Outlined.AdminPanelSettings
                            else -> Icons.Outlined.TaskAlt
                        }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    if (item.occurredAt.isNotBlank()) Text(appealTime(item.occurredAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (index < minOf(items.size, 3) - 1) HorizontalDivider(Modifier.padding(start = 46.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
        }
    }
}

@Composable private fun AdminCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AdminPanel(modifier, content)
}

@Composable private fun AdminMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun AdminStatus(report: AdminReport) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdminBadge(report.status.label, attention = report.status == AdminReportStatus.RECEIVED,
            complete = report.status !in listOf(AdminReportStatus.RECEIVED, AdminReportStatus.REVIEWING))
        Text(report.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AdminReportRow(report: AdminReport, onClick: () -> Unit) {
    AdminListRow(report.title, "${report.member} · ${report.time}", status = { AdminStatus(report) }, onClick = onClick)
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
        report.allowedActions.forEach { value -> AdminFilterChip(action == value, {
            action = value
            reasonCode = if (value == AdminAction.NONE) AdminDecisionReason.NO_VIOLATION else AdminDecisionReason.POLICY_VIOLATION
        }, enabled = enabled, label = { Text(value.label) }) }
    }
    if (action == AdminAction.SUSPEND) {
        Text("정지 기간", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 7, 30).forEach { value -> AdminFilterChip(days == value, { days = value }, enabled = enabled, label = { Text("${value}일") }) }
        }
    }
    Text("처리 사유", fontWeight = FontWeight.SemiBold)
    val reasons = if (action == AdminAction.NONE) listOf(AdminDecisionReason.NO_VIOLATION, AdminDecisionReason.INSUFFICIENT_EVIDENCE)
        else listOf(AdminDecisionReason.POLICY_VIOLATION, AdminDecisionReason.SPAM, AdminDecisionReason.HARASSMENT)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEach { value -> AdminFilterChip(reasonCode == value, { reasonCode = value }, enabled = enabled, label = { Text(value.label) }) }
    }
    OutlinedTextField(reason, { if (it.length <= 500) reason = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("처리 사유 및 사용자 안내 (필수)") }, minLines = 3, supportingText = { Text("${reason.length}/500 · 사용자에게 전달할 안내") })
    OutlinedTextField(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("내부 메모 (선택)") }, minLines = 2, supportingText = { Text("사용자에게 공개하지 않는 운영 메모") })
    Text(if (action == AdminAction.SUSPEND) "기간 동안 실제 회원의 로그인·서비스 이용이 제한됩니다." else "입력 내용을 확인한 뒤 신고에 적용합니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AdminPrimaryButton({ confirm = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled && command.isValid()) { Text("처리 내용 확인") }
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
