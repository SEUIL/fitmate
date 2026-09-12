package com.fitmate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import com.fitmate.app.data.remote.ServerHealthApi
import com.fitmate.app.data.remote.StoredExternalWorkout
import com.fitmate.app.data.remote.externalWorkoutContentMatches
import com.fitmate.app.samsunghealth.SamsungHealthStatus
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.fitmate.app.samsunghealth.SamsungWorkoutUiModel
import com.fitmate.app.samsunghealth.WorkoutMatchAnalysis
import com.fitmate.app.samsunghealth.WorkoutMatchEngine
import com.fitmate.app.samsunghealth.toExternalWorkoutSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ExternalWorkoutSection(
    api: FitMateApiClient,
    sessionId: String,
    sportCode: String,
    sessionExerciseNames: List<String> = emptyList(),
    sessionStartedAt: Instant,
    sessionEndedAt: Instant,
    samsungState: SamsungHealthUiState,
    loadWorkouts: (Instant, Instant, (SamsungHealthUiState) -> Unit) -> Unit,
    requestPermission: () -> Unit,
) {
    val server = remember(api) { ServerHealthApi(api) }
    val scope = rememberCoroutineScope()
    var stored by remember(sessionId) { mutableStateOf<StoredExternalWorkout?>(null) }
    var serverLoaded by remember(sessionId) { mutableStateOf(false) }
    var serverError by remember(sessionId) { mutableStateOf<String?>(null) }
    var selected by remember(sessionId) { mutableStateOf<SamsungWorkoutUiModel?>(null) }
    var saving by remember(sessionId) { mutableStateOf(false) }
    var deviceLoadedForSession by remember(sessionId) { mutableStateOf(false) }
    var message by remember(sessionId) { mutableStateOf<String?>(null) }
    var getAttempt by remember(sessionId) { mutableStateOf(0) }

    fun loadStored() {
        scope.launch {
            serverError = null
            try { stored = withContext(Dispatchers.IO) { server.get(sessionId) } }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                serverError = failure.message ?: "저장된 Samsung Health 요약을 불러오지 못했어요."
            } finally { serverLoaded = true }
        }
    }
    LaunchedEffect(sessionId, getAttempt) { loadStored() }

    val analysis = remember(samsungState.workouts, sessionStartedAt, sessionEndedAt, sportCode, sessionExerciseNames, deviceLoadedForSession) {
        WorkoutMatchEngine.analyze(
            sportCode,
            sessionStartedAt,
            sessionEndedAt,
            if (deviceLoadedForSession) samsungState.workouts else emptyList(),
            sessionExerciseNames,
        )
    }
    val candidates = analysis.candidates
    LaunchedEffect(candidates) {
        selected = selected?.let { current -> candidates.firstOrNull { it.uid == current.uid && it.sourceAppId == current.sourceAppId } }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Samsung Health 운동 요약", style = MaterialTheme.typography.titleMedium)
            if (sessionExerciseNames.isNotEmpty()) {
                Text("현재 FitMate 운동: ${sessionExerciseNames.distinct().joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
            }
            if (!serverLoaded) CircularProgressIndicator()
            serverError?.let {
                Text("외부 요약 조회 실패: $it", color = MaterialTheme.colorScheme.error)
                Text("기본 FitMate 리포트는 정상적으로 사용할 수 있어요.")
                OutlinedButton({ serverLoaded = false; getAttempt++ }) { Text("저장 여부 다시 확인") }
            }
            stored?.let { linked ->
                SummaryText(linked.summary.exerciseType, linked.summary.activeDurationSeconds, linked.summary.caloriesKcal?.toPlainString(), linked.summary.meanHeartRate?.toPlainString())
                Text("저장된 요약 · 버전 ${linked.version}", color = MaterialTheme.colorScheme.primary)
                OutlinedButton({
                    message = "기기 원본을 다시 읽는 중이에요."
                    loadWorkouts(sessionStartedAt, sessionEndedAt) { refreshed ->
                        deviceLoadedForSession = true
                        val same = WorkoutMatchEngine.candidates(sportCode, sessionStartedAt, sessionEndedAt, refreshed.workouts)
                            .firstOrNull { it.uid == linked.summary.sourceRecordId && it.sourceAppId == linked.summary.sourceAppId }
                        if (same == null) message = "같은 원본을 찾지 못했습니다. 기존 저장 요약을 유지합니다."
                        else selected = same
                    }
                }) { Text("원본 다시 확인") }
            }

            if (stored == null && serverLoaded && serverError == null) {
                when {
                    samsungState.status == SamsungHealthStatus.LOADING -> Text("기기 운동을 불러오는 중…")
                    samsungState.status in setOf(
                        SamsungHealthStatus.PLATFORM_ERROR,
                        SamsungHealthStatus.AUTHORIZATION_ERROR,
                        SamsungHealthStatus.UNKNOWN_ERROR,
                    ) -> {
                        Text(samsungState.message ?: "Samsung Health 기록을 확인하지 못했습니다.", color = MaterialTheme.colorScheme.error)
                        Text("Samsung Health 상태와 개발 모드를 확인한 뒤 다시 시도해 주세요.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton({ loadWorkouts(sessionStartedAt, sessionEndedAt) { deviceLoadedForSession = true } }) { Text("다시 시도") }
                    }
                    samsungState.permissions.none { it.label == "운동 읽기" && it.granted } -> {
                        Text("Samsung Health 운동 읽기 권한이 없어 기록을 확인할 수 없습니다.")
                        Text("권한을 허용한 뒤 운동을 다시 불러와 주세요.", style = MaterialTheme.typography.bodySmall)
                        Button(requestPermission) { Text("운동 읽기 권한 허용") }
                    }
                    candidates.isEmpty() -> {
                        Text("아직 이 세션과 일치하는 운동 후보가 없습니다.")
                        if (deviceLoadedForSession && samsungState.workouts.isEmpty()) {
                            Text("같은 시간대에 조회된 기록이 없습니다. Samsung Health에서 운동 기록을 확인한 뒤 새로고침해 주세요.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton({ loadWorkouts(sessionStartedAt, sessionEndedAt) { deviceLoadedForSession = true } }) {
                            Text(if (deviceLoadedForSession) "새로고침" else "Samsung Health 운동 불러오기")
                        }
                    }
                    else -> candidates.forEach { candidate ->
                        OutlinedButton({ selected = candidate }, Modifier.fillMaxWidth()) {
                            Text("${candidate.customTitle ?: candidate.exerciseType} · ${formatExternalTime(candidate.startTime)} · ${candidate.activeDurationSeconds / 60}분")
                        }
                    }
                }
                if (deviceLoadedForSession && samsungState.status in setOf(SamsungHealthStatus.READY, SamsungHealthStatus.DATA_EMPTY)) {
                    MatchExclusionGuidance(analysis, sportCode)
                }
            }

            selected?.let { candidate ->
                Text("선택한 후보")
                SummaryText(candidate.exerciseType, candidate.activeDurationSeconds, candidate.calories?.toString(), candidate.meanHeartRate?.toString())
                Text("선택한 운동의 최소 요약을 발표용 리포트에 저장합니다. 발표용 테스트 데이터는 발표 후 개발 DB에서 수동으로 정리합니다.", style = MaterialTheme.typography.bodySmall)
                Button({
                    saving = true; message = null
                    loadWorkouts(sessionStartedAt, sessionEndedAt) { refreshed ->
                        deviceLoadedForSession = true
                        val current = WorkoutMatchEngine.candidates(sportCode, sessionStartedAt, sessionEndedAt, refreshed.workouts)
                            .firstOrNull { it.uid == candidate.uid && it.sourceAppId == candidate.sourceAppId }
                        if (current == null) {
                            saving = false; message = "저장 직전 원본을 다시 확인하지 못했습니다. 후보를 새로 선택해 주세요."
                        } else {
                            val request = current.toExternalWorkoutSummary()
                            scope.launch {
                                try {
                                    stored = withContext(Dispatchers.IO) { server.put(sessionId, request, stored?.version) }
                                    selected = null; message = "Samsung Health 요약을 저장했습니다."
                                } catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    val recovered = runCatching { withContext(Dispatchers.IO) { server.get(sessionId) } }.getOrNull()
                                    if (recovered != null && recovered.sessionId == sessionId && externalWorkoutContentMatches(request, recovered)) {
                                        stored = recovered; selected = null; message = "저장 여부를 확인해 완료 상태를 복원했습니다."
                                    } else message = failure.message ?: "외부 요약 저장에 실패했습니다. 기본 리포트는 유지됩니다."
                                } finally { saving = false }
                            }
                        }
                    }
                }, enabled = !saving) { Text(if (saving) "원본 확인 및 저장 중…" else if (stored == null) "확인하고 연결" else "변경 내용 확인 후 저장") }
            }
            message?.let { Text(it, color = if (stored != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MatchExclusionGuidance(analysis: WorkoutMatchAnalysis, sportCode: String) {
    analysis.unsupported.distinctBy {
        Triple(it.workout.exerciseType, it.workout.customTitle, it.matchingSessionExerciseNames)
    }.forEach { unsupported ->
        val workout = unsupported.workout
        val title = workout.customTitle?.takeIf { it.isNotBlank() }
        Text("지원하지 않는 운동 코드: ${workout.exerciseType}${title?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        if (unsupported.matchingSessionExerciseNames.isNotEmpty()) {
            Text("현재 세션의 ${unsupported.matchingSessionExerciseNames.joinToString(" · ")}와 이름은 같지만 저장할 수 없는 참고 기록입니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (analysis.unsupported.isNotEmpty()) {
        Text(
            if (sportCode == "HEALTH") "발표에서는 지원되는 벤치프레스(BENCH_PRESS) 기록을 사용해 주세요."
            else "현재 지원되는 단일 운동 기록을 사용해 주세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (analysis.timeMismatchCount > 0) {
        Text("지원되는 운동 ${analysis.timeMismatchCount}건은 시작·종료 시각이 맞지 않아 제외했습니다. FitMate 세션과 같은 시간대의 기록을 준비해 주세요.", style = MaterialTheme.typography.bodySmall)
    }
    if (analysis.compoundCount > 0) {
        Text(
            "복합 운동 ${analysis.compoundCount}건은 제외했습니다. " +
                if (sportCode == "HEALTH") "Fit3에서 벤치프레스를 단일 운동으로 기록해 주세요."
                else "단일 운동으로 다시 기록해 주세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (analysis.incompleteCount > 0) {
        Text("활동 시간이 없는 기록 ${analysis.incompleteCount}건은 제외했습니다. Samsung Health에서 기록 내용을 확인해 주세요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryText(type: String, seconds: Long, calories: String?, heartRate: String?) {
    Text("$type · ${seconds / 60}분")
    Text("칼로리 ${calories?.let { "$it kcal" } ?: "기록 없음"} · 평균 심박 ${heartRate?.let { "$it bpm" } ?: "기록 없음"}")
}

private fun formatExternalTime(value: Instant) = value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
