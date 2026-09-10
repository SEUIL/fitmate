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

    val candidates = remember(samsungState.workouts, sessionStartedAt, sessionEndedAt, sportCode, deviceLoadedForSession) {
        if (deviceLoadedForSession) WorkoutMatchEngine.candidates(sportCode, sessionStartedAt, sessionEndedAt, samsungState.workouts) else emptyList()
    }
    LaunchedEffect(candidates) {
        selected = selected?.let { current -> candidates.firstOrNull { it.uid == current.uid && it.sourceAppId == current.sourceAppId } }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Samsung Health 운동 요약", style = MaterialTheme.typography.titleMedium)
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
                    samsungState.permissions.none { it.label == "운동 읽기" && it.granted } -> {
                        Text("운동 읽기 권한이 필요합니다.")
                        Button(requestPermission) { Text("운동 읽기 권한 허용") }
                    }
                    samsungState.status == SamsungHealthStatus.LOADING -> Text("기기 운동을 불러오는 중…")
                    candidates.isEmpty() -> {
                        Text("아직 이 세션과 일치하는 운동 후보가 없습니다.")
                        OutlinedButton({ loadWorkouts(sessionStartedAt, sessionEndedAt) { deviceLoadedForSession = true } }) { Text("Samsung Health 운동 불러오기") }
                        val unsupported = if (deviceLoadedForSession) samsungState.workouts.filter { it.sessionCount != 1 || !WorkoutMatchEngine.isSupported(sportCode, it.exerciseType) } else emptyList()
                        if (unsupported.isNotEmpty()) Text("지원하지 않는 종류 또는 복합 운동 ${unsupported.size}건은 제외했습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> candidates.forEach { candidate ->
                        OutlinedButton({ selected = candidate }, Modifier.fillMaxWidth()) {
                            Text("${candidate.customTitle ?: candidate.exerciseType} · ${formatExternalTime(candidate.startTime)} · ${candidate.activeDurationSeconds / 60}분")
                        }
                    }
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
private fun SummaryText(type: String, seconds: Long, calories: String?, heartRate: String?) {
    Text("$type · ${seconds / 60}분")
    Text("칼로리 ${calories?.let { "$it kcal" } ?: "기록 없음"} · 평균 심박 ${heartRate?.let { "$it bpm" } ?: "기록 없음"}")
}

private fun formatExternalTime(value: Instant) = value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
