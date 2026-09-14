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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminCenter(onExit: () -> Unit) {
    if (!BuildConfig.DEBUG) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text("운영자 인증 연동 준비 중")
            TextButton(onExit) { Text("돌아가기") }
        }
        return
    }
    val repository: AdminRepository = remember { FakeAdminRepository() }
    var revision by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var sanction by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<AdminReportStatus?>(null) }
    val reports = remember(revision, query, filter) { repository.search(query, filter) }
    val selected = remember(revision, selectedId) { repository.reports.find { it.id == selectedId } }
    val back = { if (sanction) sanction = false else if (selectedId != null) selectedId = null else onExit() }
    BackHandler { back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(
            title = { Text(if (sanction) "회원 제재" else if (selected != null) "신고 상세" else "운영자 센터", fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") } },
            actions = { Text("ADMIN", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(end = 20.dp)) }
        ) },
        bottomBar = {
            if (selected == null) NavigationBar {
                val icons = listOf(Icons.Outlined.GridView, Icons.Outlined.Flag, Icons.Outlined.PeopleOutline, Icons.AutoMirrored.Outlined.Article, Icons.Outlined.MoreHoriz)
                listOf("현황", "신고", "회원", "콘텐츠", "더보기").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null, Modifier.size(23.dp)) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("개발용 데모 · 가상 데이터 · 실제 계정에 반영되지 않습니다", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            when {
                selected != null && sanction -> key(selected.id) {
                    AdminSanctionForm(selected, onCancel = { sanction = false }) { action, reason, days, note ->
                        val saved = repository.decide(selected.id, action, reason, days, note)
                        if (saved) { revision++; sanction = false }
                        saved
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
                        Text("가상 신고 내용", fontWeight = FontWeight.SemiBold)
                        Text("${selected.title}에 대한 검토 요청입니다. 신고된 내용과 운영 기준을 함께 확인해 주세요.", fontSize = 14.sp)
                        Text("실제 게시물·DM 원문이 아닙니다. 근거 조회 API는 아직 연결되지 않았습니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AdminHeading("처리 이력")
                    Text("• ${selected.time} 신고 접수", fontSize = 14.sp)
                    if (selected.status != AdminReportStatus.RECEIVED) Text("• 운영자 데모에서 검토 시작", fontSize = 14.sp)
                    selected.decision?.let { AdminCard { Text("데모 처리 완료", fontWeight = FontWeight.SemiBold); Text(it) } }
                    when (selected.status) {
                        AdminReportStatus.RECEIVED -> Button({ if (repository.startReview(selected.id)) revision++ }, Modifier.fillMaxWidth()) { Text("검토 시작") }
                        AdminReportStatus.REVIEWING -> Button({ sanction = true }, Modifier.fillMaxWidth()) { Text("반려 / 조치 선택") }
                        else -> Text("완료된 신고는 다시 처리할 수 없습니다.", fontSize = 12.sp)
                    }
                }
                tab == 0 -> {
                    Text("오늘의 운영 현황", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("처리가 필요한 항목부터 확인하세요.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("대기 신고", repository.pendingCount.toString(), Modifier.weight(1f)) { tab = 1; filter = null; query = "" }
                        AdminMetric("신규 회원", "8", Modifier.weight(1f)) { tab = 2 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("운동 완료", "24", Modifier.weight(1f)) { tab = 4 }
                        AdminMetric("판독 실패", "2", Modifier.weight(1f)) { tab = 4 }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AdminHeading("최근 신고")
                        TextButton({ tab = 1; filter = null; query = "" }) { Text("전체 보기") }
                    }
                    repository.reports.take(3).forEach { report -> AdminReportRow(report) { selectedId = report.id } }
                    AdminHeading("최근 처리")
                    if (repository.history.isEmpty()) Text("아직 처리한 신고가 없습니다.", fontSize = 14.sp)
                    repository.history.take(3).forEach { Text(it, fontSize = 14.sp) }
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
                }
                tab == 2 -> {
                    AdminHeading("회원")
                    Text("신고와 연결된 가상 회원입니다. 회원 검색·상세 API는 연동 예정입니다.", fontSize = 14.sp)
                    repository.reports.forEach { report -> AdminCard(Modifier.clickable { selectedId = report.id }) {
                        Text(report.member, fontWeight = FontWeight.SemiBold)
                        Text("관련 신고 ${report.id} · ${report.status.label}", fontSize = 13.sp)
                        Text("관련 신고 보기 →", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    } }
                }
                tab == 3 -> {
                    AdminHeading("콘텐츠")
                    Text("신고된 콘텐츠 미리보기입니다. 숨김·복구·삭제 기능은 아직 제공하지 않습니다.", fontSize = 14.sp)
                    repository.reports.filter { it.type in listOf("커뮤니티", "댓글", "메이트") }.forEach { report -> AdminReportRow(report) { selectedId = report.id } }
                }
                else -> {
                    AdminHeading("운영 도구")
                    listOf("공지 관리", "카테고리 관리", "이의신청", "운영 규칙", "시스템 현황", "운영자 권한").forEach { title -> AdminCard {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text("후속 화면 · 백엔드 연동 준비 중", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    AdminHeading("이번 데모의 처리 기록")
                    if (repository.history.isEmpty()) Text("처리 기록 없음")
                    repository.history.forEach { AdminCard { Text(it, fontSize = 14.sp) } }
                    Text("데모 기록은 운영자 센터 종료 시 초기화됩니다. 실제 권한 검사·MFA·감사 로그는 백엔드 연동이 필요합니다.", fontSize = 12.sp)
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

@Composable private fun AdminSanctionForm(report: AdminReport, onCancel: () -> Unit, onSave: (String, String, Int, String) -> Boolean) {
    var action by rememberSaveable { mutableStateOf("경고") }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AdminCard { Text(report.member, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("연결 신고 · ${report.id}", fontSize = 12.sp) }
    AdminHeading("조치 종류")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("반려", "경고", "기간 정지").forEach { value -> FilterChip(action == value, { action = value }, label = { Text(value) }) }
    }
    if (action == "기간 정지") {
        Text("정지 기간", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 7, 30).forEach { value -> FilterChip(days == value, { days = value }, label = { Text("${value}일") }) }
        }
    }
    OutlinedTextField(reason, { if (it.length <= 500) reason = it }, Modifier.fillMaxWidth(), label = { Text("처리 사유 및 사용자 안내 (필수)") }, minLines = 3, supportingText = { Text("${reason.length}/500 · 사용자에게 전달할 안내") })
    OutlinedTextField(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), label = { Text("내부 메모 (선택)") }, minLines = 2, supportingText = { Text("사용자에게 공개하지 않는 운영 메모") })
    Text(if (action == "기간 정지") "실연동 시 기간 동안 로그인·서비스 이용이 제한됩니다. 현재는 가상 데이터만 변경합니다." else "입력 내용을 확인한 뒤 데모 신고에 적용합니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (failed) Text("이미 처리되었거나 입력이 유효하지 않습니다.", color = MaterialTheme.colorScheme.error)
    Button({ confirm = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = reason.isNotBlank()) { Text("처리 내용 확인") }
    TextButton(onCancel, Modifier.fillMaxWidth()) { Text("취소") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("데모 조치를 적용할까요?") },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${report.member} · ${report.id}")
            Text("$action${if (action == "기간 정지") " ${days}일" else ""}", fontWeight = FontWeight.Bold)
            Text(reason)
            Text("실제 회원에게 적용되거나 알림이 발송되지 않습니다.", fontSize = 12.sp)
        } },
        confirmButton = { TextButton({ confirm = false; failed = !onSave(action, reason, days, note) }) { Text("데모 적용") } },
        dismissButton = { TextButton({ confirm = false }) { Text("돌아가기") } })
}
