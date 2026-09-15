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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import java.util.Locale

internal data class AdminSystemData(val from: String, val to: String, val asOf: String,
    val statuses: List<Pair<String, Long>>, val failures: List<Pair<String, Long>>, val failureRate: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminSystemScreen(api: FitMateApiClient, onExit: () -> Unit) {
    val repository = remember(api) { ServerAdminRepository(api) }
    var days by rememberSaveable { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<AdminSystemData?>(null) }
    var error by remember { mutableStateOf<AdminError?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(repository, days, refresh) {
        loading = true; data = null; error = null
        try {
            data = repository.systemSummary(days)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
        } finally { loading = false }
    }
    BackHandler(onBack = onExit)
    Scaffold(topBar = { TopAppBar(title = { Text("시스템 현황") }, navigationIcon = {
        IconButton(onExit) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("인바디 판독 현황", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 7, 30).forEach { value ->
                    FilterChip(selected = days == value, onClick = { days = value }, enabled = !loading,
                        label = { Text(if (value == 1) "최근 24시간" else "최근 ${value}일") })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
                if (it == AdminError.FORBIDDEN || it == AdminError.SESSION_EXPIRED) {
                    TextButton(onExit) { Text("뒤로가기") }
                } else TextButton({ refresh++ }) { Text("다시 조회") }
            }
            data?.let { value ->
                Text("${value.from} ~ ${value.to}", style = MaterialTheme.typography.bodySmall)
                Text("이 기간에 접수된 판독의 현재 상태입니다. 재시도 횟수는 별도로 집계하지 않습니다.")
                if (value.statuses.sumOf { it.second } == 0L) Text("접수된 판독이 없습니다.")
                val labels = mapOf("UPLOADED" to "접수", "PROCESSING" to "판독 중", "COMPLETED" to "완료",
                    "REVIEW_REQUIRED" to "확인 필요", "FAILED" to "실패")
                value.statuses.forEach { (code, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labels[code] ?: code); Text("${count}건", fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider()
                Text("종료된 판독 중 실패율: " + (value.failureRate?.let { String.format(Locale.KOREA, "%.1f%%", it * 100) }
                    ?: "계산할 종료 건수 없음"))
                Text("실패 코드", style = MaterialTheme.typography.titleMedium)
                if (value.failures.isEmpty()) Text("실패 기록 없음")
                value.failures.forEach { (code, count) -> Text("$code · ${count}건") }
                HorizontalDivider()
                Text("API 오류율 · 응답 시간 · 활성 사용자: 미집계")
                Text("집계 시각: ${value.asOf}", style = MaterialTheme.typography.bodySmall)
                TextButton({ refresh++ }, enabled = !loading) { Text("새로고침") }
            }
        }
    }
}
