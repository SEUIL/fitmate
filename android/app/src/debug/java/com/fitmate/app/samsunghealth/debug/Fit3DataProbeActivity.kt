package com.fitmate.app.samsunghealth.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.fitmate.app.ui.theme.FitmateTheme
import java.util.Locale
import kotlinx.coroutines.launch

class Fit3DataProbeActivity : ComponentActivity() {
    private lateinit var probe: Fit3DataProbe
    private var state by mutableStateOf<Fit3ProbeScreenState>(Fit3ProbeScreenState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probe = Fit3DataProbe(applicationContext)
        enableEdgeToEdge()
        setContent {
            FitmateTheme {
                Fit3DataProbeScreen(
                    state = state,
                    onRun = ::runProbe,
                    onPermission = ::requestPermission,
                    onResolve = { probe.resolve(this) },
                )
            }
        }
        runProbe()
    }

    private fun runProbe() {
        state = Fit3ProbeScreenState.Loading
        lifecycleScope.launch { state = probe.run().toScreenState() }
    }

    private fun requestPermission() {
        state = Fit3ProbeScreenState.Loading
        lifecycleScope.launch { state = probe.requestPermission(this@Fit3DataProbeActivity).toScreenState() }
    }
}

internal sealed interface Fit3ProbeScreenState {
    data object Loading : Fit3ProbeScreenState
    data object PermissionRequired : Fit3ProbeScreenState
    data object NoRecentRunning : Fit3ProbeScreenState
    data class Ready(val report: Fit3ProbeReport) : Fit3ProbeScreenState
    data class Failed(val message: String, val canResolve: Boolean) : Fit3ProbeScreenState
}

private fun Fit3ProbeResult.toScreenState(): Fit3ProbeScreenState = when (this) {
    is Fit3ProbeResult.Success -> Fit3ProbeScreenState.Ready(report)
    Fit3ProbeResult.PermissionRequired -> Fit3ProbeScreenState.PermissionRequired
    Fit3ProbeResult.NoRecentRunning -> Fit3ProbeScreenState.NoRecentRunning
    is Fit3ProbeResult.Failure -> Fit3ProbeScreenState.Failed(message, canResolve)
}

@Composable
internal fun Fit3DataProbeScreen(
    state: Fit3ProbeScreenState,
    onRun: () -> Unit,
    onPermission: () -> Unit,
    onResolve: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Fit3 실제 러닝 데이터 검증", style = MaterialTheme.typography.headlineSmall)
        Text("최근 14일의 가장 최근 RUNNING 1건을 두 번 조회합니다. 원본 ID, 정확한 시각, 기기 식별값과 원시 샘플 값은 표시·저장·로그하지 않습니다.")
        Text("판정 기준: 유효 샘플이 3개 이상이고, 누락·시간 순서 이상·긴 공백 없이 운동 시간의 80% 이상을 덮으면 제공됨입니다. 일부만 유효하면 일부 제공, 유효값이 없으면 미제공입니다.", style = MaterialTheme.typography.bodySmall)
        when (state) {
            Fit3ProbeScreenState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Samsung Health를 두 번 조회하는 중…")
            }
            Fit3ProbeScreenState.PermissionRequired -> {
                StatusCard("실제 Fit3 검증 상태", "미검증 · 운동 읽기 권한 필요")
                StatusCard("심박수", MetricAvailability.UNKNOWN.label)
                StatusCard("속도", MetricAvailability.UNKNOWN.label)
                Button(onPermission, Modifier.fillMaxWidth()) { Text("운동 읽기 권한 요청") }
            }
            Fit3ProbeScreenState.NoRecentRunning -> {
                StatusCard("실제 Fit3 검증 상태", "미검증 · 최근 14일 RUNNING 기록 없음")
                StatusCard("심박수", MetricAvailability.UNKNOWN.label)
                StatusCard("속도", MetricAvailability.UNKNOWN.label)
                Text("합성 데이터로 대체하지 않았습니다. Fit3에서 새 러닝을 기록하고 Samsung Health 동기화가 끝난 뒤 다시 조회하세요.")
            }
            is Fit3ProbeScreenState.Failed -> {
                StatusCard("실제 Fit3 검증 상태", "미검증 · SDK 조회 실패")
                StatusCard("심박수", MetricAvailability.UNKNOWN.label)
                StatusCard("속도", MetricAvailability.UNKNOWN.label)
                Text(state.message, color = MaterialTheme.colorScheme.error)
                if (state.canResolve) OutlinedButton(onResolve, Modifier.fillMaxWidth()) { Text("Samsung Health 문제 해결") }
            }
            is Fit3ProbeScreenState.Ready -> ProbeReport(state.report)
        }
        OutlinedButton(onRun, Modifier.fillMaxWidth(), enabled = state != Fit3ProbeScreenState.Loading) {
            Text("같은 조건으로 다시 2회 조회")
        }
    }
}

@Composable
private fun ProbeReport(report: Fit3ProbeReport) {
    StatusCard("실제 Fit3 검증 상태", "SDK 실데이터 조회됨 · ${report.fit3MetadataMatch.label}")
    StatusCard("심박수", report.heartRate.availability.label)
    StatusCard("속도", report.speed.availability.label)
    ProbeCard("운동 원본·시각") {
        ProbeLine("원본 ID", if (report.sourceRecordIdProvided) "제공됨 · 값 숨김" else "미제공")
        ProbeLine("운동 종류", report.exerciseType)
        ProbeLine("시작·종료", "둘 다 제공됨 · 시작 기준 +0초 ~ +${report.elapsedDurationSeconds}초")
        ProbeLine("수정 시각", if (report.sourceModifiedTimeProvided) "제공됨 · 종료 대비 ${signedSeconds(report.modifiedAfterEndSeconds)}" else "미제공")
        ProbeLine("ExerciseSession", "${report.exerciseSessionCount}개")
    }
    ProbeCard("ExerciseSession.log") {
        ProbeLine("존재 여부", if (report.exerciseLogExists) "있음" else "없음")
        ProbeLine("전체 샘플", "${report.exerciseLogSampleCount}개")
    }
    MetricCard("심박수 샘플", report.heartRate)
    MetricCard("속도 샘플", report.speed)
    ProbeCard("정지·재개 또는 누락 표현") {
        val pause = report.pauseEvidence
        ProbeLine("명시적 pause/resume 필드", if (pause.explicitPauseFieldAvailable) "있음" else "SDK ExerciseLog에 없음")
        ProbeLine("경과/활동 시간", "${pause.elapsedDurationSeconds}초 / ${pause.activeDurationSeconds}초")
        ProbeLine("비활동 시간 차이", "${pause.inactiveDifferenceSeconds}초")
        ProbeLine("속도 0 샘플", "${pause.zeroSpeedSampleCount}개")
        ProbeLine("전체 로그 긴 공백", "${pause.allLogGapCount}개 · 최대 ${formatMillis(pause.allLogMaxGapMillis)}")
        Text("시간 차이·0 속도·긴 공백은 관찰 근거이며 정지 원인을 단정하지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
    ProbeCard("동일 운동 반복 조회") {
        val value = report.consistency
        ProbeLine("조회 횟수", "${value.queryCount}회")
        ProbeLine("같은 원본", yesNo(value.sameSourceRecord))
        ProbeLine("수정 시각", yesNo(value.sameModifiedTime))
        ProbeLine("샘플 개수", yesNo(value.sameSampleCounts))
        ProbeLine("분석 입력 전체", yesNo(value.sameAnalyzedContent))
    }
}

@Composable
private fun MetricCard(title: String, value: MetricProbeSummary) = ProbeCard(title) {
    ProbeLine("판정", value.availability.label)
    ProbeLine("유효/누락·무효", "${value.sampleCount}개 / ${value.missingOrInvalidCount}개")
    ProbeLine("샘플 시작·종료", "시작 기준 ${formatSignedMillis(value.firstSampleOffsetMillis)} ~ ${formatSignedMillis(value.lastSampleOffsetMillis)}")
    ProbeLine("운동 시간 범위 포함률", value.coveragePercent?.let { "$it%" } ?: "계산 불가")
    ProbeLine("시간 범위 밖/순서 이상", "${value.outsideSessionCount}개 / ${value.nonIncreasingTimestampCount}개")
    val intervals = value.intervals
    ProbeLine("간격 min/중앙/p90/max", listOf(intervals.minMillis, intervals.medianMillis, intervals.p90Millis, intervals.maxMillis).joinToString(" / ") { formatMillis(it) })
    ProbeLine("간격 분포", "<1초 ${intervals.underOneSecond} · 1~5초 ${intervals.oneToFiveSeconds} · 5~15초 ${intervals.fiveToFifteenSeconds} · 15~60초 ${intervals.fifteenToSixtySeconds} · 60초+ ${intervals.atLeastSixtySeconds}")
    ProbeLine("긴 공백", "${intervals.gapCount}개 · 기준 ${formatMillis(intervals.gapThresholdMillis)} · 최대 ${formatMillis(intervals.maxGapMillis)}")
}

@Composable
private fun StatusCard(title: String, value: String) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProbeCard(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ProbeLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun yesNo(value: Boolean) = if (value) "일치" else "불일치"
private fun signedSeconds(value: Long?) = value?.let { if (it >= 0) "+${it}초" else "${it}초" } ?: "계산 불가"
private fun formatSignedMillis(value: Long?) = value?.let { if (it >= 0) "+${formatMillis(it)}" else "-${formatMillis(-it)}" } ?: "없음"
private fun formatMillis(value: Long?): String = value?.let {
    if (it < 1_000L) "${it}ms" else String.format(Locale.US, "%.1f초", it / 1_000.0)
} ?: "없음"
