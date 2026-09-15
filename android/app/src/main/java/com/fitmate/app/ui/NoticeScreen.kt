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
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeScreen(api: FitMateApiClient, managing: Boolean, onExit: () -> Unit) {
    val repo = remember(api) { ServerAdminRepository(api) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var draftVersion by rememberSaveable { mutableLongStateOf(0) }
    var commandKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<NoticeData>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<NoticeData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AdminError?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val blocked = error == AdminError.FORBIDDEN || error == AdminError.SESSION_EXPIRED

    suspend fun refresh() {
        busy = true; error = null; detail = null
        try {
            if (selected != null) detail = repo.noticeDetail(managing, requireNotNull(selected))
            else { val page = repo.notices(managing); items = page.items; cursor = page.nextCursor }
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN; items = emptyList(); cursor = null }
        finally { busy = false }
    }
    LaunchedEffect(repo, selected) { if (!editing) refresh() }
    suspend fun submit(action: String) {
        busy = true; error = null; pendingAction = action
        try {
            val result = if (action == "save") repo.saveNotice(selected, title, body, draftVersion, commandKey)
                else repo.transitionNotice(requireNotNull(selected), action, draftVersion, commandKey)
            selected = result.id; detail = result; editing = false; pendingAction = null
            commandKey = UUID.randomUUID().toString()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
            if (error in listOf(AdminError.VALIDATION, AdminError.VERSION_CONFLICT, AdminError.ALREADY_PROCESSED, AdminError.NOT_FOUND, AdminError.IDEMPOTENCY_CONFLICT)) {
                pendingAction = null; commandKey = UUID.randomUUID().toString()
            }
        } finally { busy = false }
    }
    val back = {
        if (!busy) {
            if (pendingAction != null || blocked) onExit()
            else if (editing) { editing = false; scope.launch { refresh() } }
            else if (selected != null) { selected = null; detail = null }
            else onExit()
        }
    }
    BackHandler(onBack = back)
    Scaffold(topBar = { if (managing) AdminTopBar("공지 관리", back, enabled = !busy) else TopAppBar(title = { Text("공지사항") }, navigationIcon = {
        IconButton(back, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (blocked) TextButton(onExit) { Text("닫기") }
            else if (pendingAction != null && !busy) {
                Text("요청 결과를 확인하지 못했습니다. 같은 요청으로 결과를 확인해 주세요.")
                Button({ scope.launch { submit(requireNotNull(pendingAction)) } }) { Text("처리 결과 확인") }
            }
            if (!blocked && editing) {
                Text(if (detail?.status == "PUBLISHED") "게시 중 공지입니다. 저장하면 모든 사용자에게 즉시 반영됩니다." else "전체 사용자에게 게시할 공지를 작성하세요.")
                OutlinedTextField(title, { title = it }, label = { Text("제목 (${title.length}/100)") },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy && pendingAction == null, singleLine = true, isError = title.length > 100)
                OutlinedTextField(body, { body = it }, label = { Text("본문 (${body.length}/5000)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 6, enabled = !busy && pendingAction == null, isError = body.length > 5000)
                Text("미리보기", style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleLarge); Text(body)
                Button({ confirmation = "save" }, enabled = !busy && pendingAction == null && title.isNotBlank() && title.length <= 100 && body.isNotBlank() && body.length <= 5000) { Text("저장") }
                if (error == AdminError.VERSION_CONFLICT) TextButton({ editing = false; scope.launch { refresh() } }) { Text("최신 공지 다시 조회") }
            } else if (!blocked && selected != null) {
                detail?.let { value ->
                    Text(value.title, style = MaterialTheme.typography.headlineSmall)
                    value.publishedAt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (managing) Text(noticeStatus(value.status))
                    Text(value.body)
                    if (managing && value.status != "ENDED" && pendingAction == null) {
                        OutlinedButton({ title = value.title; body = value.body; draftVersion = value.version; editing = true; commandKey = UUID.randomUUID().toString() }, enabled = !busy) { Text("수정") }
                        Button({ draftVersion = value.version; confirmation = if (value.status == "DRAFT") "publish" else "end" }, enabled = !busy) {
                            Text(if (value.status == "DRAFT") "전체 사용자에게 게시" else "게시 종료")
                        }
                    }
                }
                if (!busy && pendingAction == null) TextButton({ scope.launch { refresh() } }) { Text("최신 공지 조회") }
            } else if (!blocked) {
                if (managing) AdminPageHeading("사용자에게 전할 소식", "공지를 작성하고 게시 상태를 관리하세요.")
                if (managing) Button({ title = ""; body = ""; draftVersion = 0; editing = true; error = null; commandKey = UUID.randomUUID().toString() }, enabled = !busy) { Text("새 공지 작성") }
                if (loaded && items.isEmpty() && !busy && error == null) Text("공지사항이 없습니다.")
                if (managing && items.isNotEmpty()) AdminListGroup { items.forEachIndexed { index, value ->
                    AdminListRow(value.title, value.publishedAt?.let(::appealTime) ?: "아직 게시하지 않은 공지", enabled = !busy,
                        status = { AdminBadge(noticeStatus(value.status), complete = value.status != "PUBLISHED") }) { selected = value.id }
                    if (index < items.lastIndex) AdminRowDivider()
                } }
                if (!managing) items.forEach { value ->
                    OutlinedCard(onClick = { selected = value.id }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(value.title, style = MaterialTheme.typography.titleMedium)
                            if (managing) Text(noticeStatus(value.status))
                            value.publishedAt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                if (cursor != null) TextButton({ scope.launch {
                    busy = true; error = null
                    try { val page = repo.notices(managing, cursor); items = (items + page.items).distinctBy { it.id }; cursor = page.nextCursor }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN }
                    finally { busy = false }
                } }, enabled = !busy) { Text("더 불러오기") }
                TextButton({ scope.launch { refresh() } }, enabled = !busy) { Text("새로고침") }
            }
        }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("공지 " + when (action) { "publish" -> "게시"; "end" -> "종료"; else -> "저장" }) },
        text = { Text(when (action) { "publish" -> "전체 사용자 공지 목록에 즉시 공개합니다."; "end" -> "사용자 공지 목록과 상세에서 더 이상 조회할 수 없습니다."; else -> "작성한 내용을 저장합니다. 게시 중이면 변경 내용이 즉시 공개됩니다." }) },
        confirmButton = { TextButton({ confirmation = null; scope.launch { submit(action) } }) { Text("확인") } },
        dismissButton = { TextButton({ confirmation = null }) { Text("취소") } }) }
}

private fun noticeStatus(value: String) = when (value) { "DRAFT" -> "초안"; "PUBLISHED" -> "게시 중"; "ENDED" -> "종료"; else -> value }
