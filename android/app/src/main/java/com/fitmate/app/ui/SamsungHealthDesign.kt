package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitmate.app.samsunghealth.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun samsungMetric(value: BigDecimal?, unit: String): String =
    value?.let { "${it.stripTrailingZeros().toPlainString()} $unit" } ?: "기록 없음"

internal fun samsungDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}초"
    seconds % 60 == 0L -> "${seconds / 60}분"
    else -> "${seconds / 60}분 ${seconds % 60}초"
}

internal fun samsungExerciseName(code: String) = when (code) {
    "BENCH_PRESS" -> "벤치프레스"
    "RUNNING" -> "러닝"
    else -> code
}

internal fun samsungConnectionLabel(state: SamsungHealthUiState): String = when {
    state.status == SamsungHealthStatus.LOADING -> "확인 중"
    state.status in setOf(SamsungHealthStatus.PLATFORM_ERROR, SamsungHealthStatus.AUTHORIZATION_ERROR, SamsungHealthStatus.UNKNOWN_ERROR) -> "확인 필요"
    state.permissions.none { it.label == "운동 읽기" && it.granted } -> "권한 필요"
    else -> "사용 가능"
}

internal fun samsungTime(time: Instant): String = time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))

@Composable internal fun SamsungSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun SamsungStep(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(number.toString(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 13.sp)
        }
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SamsungHealthConnectionScreen(
    state: SamsungHealthUiState, onRefresh: () -> Unit, onPermission: () -> Unit,
    onResolve: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, onDiagnostics: (() -> Unit)? = null,
) = WorkoutDesignTheme {
    BackHandler(onBack = onBack)
    val busy = state.status == SamsungHealthStatus.LOADING
    Scaffold(modifier, contentWindowInsets = WindowInsets(0), containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(windowInsets = WindowInsets(0), title = { Text("Samsung Health", fontSize = 20.sp) }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
        }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.align(Alignment.CenterHorizontally).size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("운동 기록을 더 자세하게", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Samsung Health의 기록을 리포트에 연결해요.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SamsungSurface {
                SamsungInfoRow("이용 상태", samsungConnectionLabel(state))
                HorizontalDivider()
                SamsungInfoRow("운동 읽기 권한", if (state.permissions.any { it.label == "운동 읽기" && it.granted }) "허용됨" else "허용 필요")
                SamsungInfoRow("마지막 기록 조회", state.lastLoadedAtMillis?.let { samsungTime(Instant.ofEpochMilli(it)) } ?: "아직 조회하지 않았어요")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.status in setOf(SamsungHealthStatus.PLATFORM_ERROR, SamsungHealthStatus.AUTHORIZATION_ERROR, SamsungHealthStatus.UNKNOWN_ERROR)) {
                SamsungSurface {
                    Text("Samsung Health 상태를 확인해 주세요.", fontWeight = FontWeight.SemiBold)
                    Text(if (state.status == SamsungHealthStatus.AUTHORIZATION_ERROR) "앱 이용 인증을 확인하지 못했어요. 테스트 환경에서는 Samsung Health 개발 모드를 확인해 주세요." else "Samsung Health 설치·업데이트와 이용 권한을 확인한 뒤 다시 시도해 주세요.", fontSize = 13.sp)
                    if (state.canResolve) OutlinedButton(onResolve) { Text("문제 해결 화면 열기") }
                }
            }
            Text("연결 방법", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            SamsungSurface {
                SamsungStep(1, "기기에서 운동 기록")
                SamsungStep(2, "Samsung Health에 동기화")
                SamsungStep(3, "FitMate에서 선택 후 연결")
            }
            Text("운동 기록은 확인 후에만 저장돼요.\n사용하지 않아도 기본 리포트는 제공돼요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onRefresh, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("상태 새로고침") }
            OutlinedButton(onPermission, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("운동 읽기 권한 관리") }
            Text("허용한 권한은 Samsung Health 앱에서 변경할 수 있어요. 이 화면은 워치·밴드의 실시간 연결 상태를 표시하지 않아요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (com.fitmate.app.BuildConfig.DEBUG && onDiagnostics != null) TextButton(onDiagnostics) { Text("개발자 진단 보기") }
        }
    }
}

@Composable private fun SamsungInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable internal fun SamsungPreparationOption(selected: Boolean, running: Boolean = false, onChange: (Boolean) -> Unit) {
    SamsungSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Samsung Health 사용", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("이번 운동에만 선택", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(selected, onChange)
        }
        if (selected) {
            Icon(Icons.Outlined.Watch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("기기에서 먼저 시작해 주세요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("워치·밴드에서 운동 시작을 누른 뒤 FitMate 운동을 시작해 주세요.", fontSize = 14.sp)
            Text(if (running) "현재 러닝 연결 지원: 단일 러닝 기록" else "현재 헬스 연결 지원: 단일 벤치프레스 기록", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("실시간 측정이 아니라 종료·동기화 후 기록을 불러와요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("기록 연결 없이도 기본 리포트를 볼 수 있어요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun SamsungEndGuidance(onLoad: () -> Unit, onSkip: () -> Unit, enabled: Boolean = true) {
    SamsungSurface {
        Text("기기 기록을 확인해 주세요", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        SamsungStep(1, "기기에서 운동 종료")
        SamsungStep(2, "Samsung Health 동기화 확인")
        SamsungStep(3, "FitMate에서 기록 불러오기")
        Text("동기화에 시간이 걸릴 수 있어요. 기본 FitMate 리포트는 그대로 유지돼요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onLoad, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) { Text("운동 기록 불러오기") }
        TextButton(onSkip, Modifier.fillMaxWidth(), enabled = enabled) { Text("기본 리포트 먼저 보기") }
    }
}

@Composable internal fun SamsungSummaryCard(summary: ExternalWorkoutSummary, linked: Boolean) {
    SamsungSurface {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Samsung Health", fontWeight = FontWeight.SemiBold)
            Text(if (linked) "연결 완료" else "선택한 기록", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(samsungExerciseName(summary.exerciseType), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("${samsungTime(summary.startedAt)} – ${samsungTime(summary.endedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SamsungStat("기기 활동 시간", samsungDuration(summary.activeDurationSeconds), Modifier.weight(1f))
            SamsungStat("소모 칼로리", samsungMetric(summary.caloriesKcal, "kcal"), Modifier.weight(1f))
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SamsungStat("평균 심박", samsungMetric(summary.meanHeartRate, "bpm"), Modifier.weight(1f))
            SamsungStat("최저 / 최고 심박", "${samsungMetric(summary.minHeartRate, "bpm")} / ${samsungMetric(summary.maxHeartRate, "bpm")}", Modifier.weight(1f))
        }
        if (summary.exerciseType == "RUNNING") SamsungStat("기기 측정 거리", samsungMetric(summary.distanceMeters?.divide(BigDecimal(1000)), "km"))
        Text("기기 측정값과 FitMate 기록은 따로 표시돼요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SamsungStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SamsungPickerPage(busy: Boolean, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onBack() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text("연결할 운동 선택", fontSize = 20.sp) }, navigationIcon = {
                IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "리포트로 돌아가기") }
            }) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}
