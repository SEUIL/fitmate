package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
internal fun AdminCategoryScreen(api: FitMateApiClient, onExit: () -> Unit) {
    val repository = remember(api) { ServerAdminRepository(api) }
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<CategoryCatalogData?>(null) }
    var order by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var version by rememberSaveable { mutableLongStateOf(0) }
    var requestKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AdminError?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val blocked = error == AdminError.FORBIDDEN || error == AdminError.SESSION_EXPIRED
    suspend fun load() {
        busy = true; error = null; saved = false
        try {
            val fresh = repository.categories(); catalog = fresh
            if (!pending) { order = ArrayList(fresh.items.map { it.id }); version = fresh.version; requestKey = UUID.randomUUID().toString() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { catalog = null; error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN }
        finally { busy = false }
    }
    suspend fun save() {
        busy = true; error = null; pending = true
        try {
            val fresh = repository.reorderCategories(order, version, requestKey)
            catalog = fresh; order = ArrayList(fresh.items.map { it.id }); version = fresh.version
            pending = false; saved = true; requestKey = UUID.randomUUID().toString()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
            if (error in listOf(AdminError.VERSION_CONFLICT, AdminError.VALIDATION, AdminError.IDEMPOTENCY_CONFLICT)) {
                pending = false; requestKey = UUID.randomUUID().toString()
            }
        } finally { busy = false }
    }
    LaunchedEffect(repository) { load() }
    BackHandler(enabled = !busy, onBack = onExit)
    Scaffold(topBar = { AdminTopBar("카테고리 관리", onExit, enabled = !busy) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AdminPageHeading("운동 종목 순서", "화살표로 종목 선택 목록의 순서를 변경하세요.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (saved) Text("표시 순서를 저장했습니다.")
            if (blocked) TextButton(onExit) { Text("닫기") }
            else {
                val byId = catalog?.items?.associateBy { it.id }.orEmpty()
                AdminListGroup { order.forEachIndexed { index, id -> byId[id]?.let { item ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(if (item.active) "활성" else "비활성", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        fun move(offset: Int) {
                            val changed = ArrayList(order); val target = index + offset
                            changed[index] = changed[target]; changed[target] = id; order = changed; saved = false
                        }
                        IconButton({ move(-1) }, enabled = index > 0 && !busy && !pending) { Icon(Icons.Outlined.ArrowUpward, "${item.name} 위로") }
                        IconButton({ move(1) }, enabled = index < order.lastIndex && !busy && !pending) { Icon(Icons.Outlined.ArrowDownward, "${item.name} 아래로") }
                    }
                    if (index < order.lastIndex) AdminRowDivider()
                } } }
                if (pending) {
                    Text("저장 결과를 확인하지 못했습니다. 같은 요청으로 결과를 확인합니다.")
                    AdminPrimaryButton({ scope.launch { save() } }, enabled = !busy) { Text("저장 결과 확인") }
                } else {
                    AdminPrimaryButton({ confirm = true }, enabled = !busy && catalog != null && order != catalog?.items?.map { it.id } && error == null) { Text("순서 저장") }
                    TextButton({ scope.launch { load() } }, enabled = !busy) { Text("최신 순서 다시 조회") }
                }
                HorizontalDivider()
                AdminSectionHeading("기타 운동 입력 집계")
                Text("같은 운동을 등록한 회원 수 · 상위 50개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                catalog?.let { value ->
                    if (value.otherSports.isEmpty()) Text("등록된 기타 운동이 없습니다.")
                    value.otherSports.forEach { (name, count) -> Text("$name · ${count}명") }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("종목 순서 저장") },
        text = { Text("현재 순서를 모든 사용자의 종목 선택 목록에 반영합니다.") },
        confirmButton = { TextButton({ confirm = false; scope.launch { save() } }) { Text("저장") } },
        dismissButton = { TextButton({ confirm = false }) { Text("취소") } })
}
