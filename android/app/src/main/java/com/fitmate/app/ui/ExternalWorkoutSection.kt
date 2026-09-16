package com.fitmate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
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
import com.fitmate.app.samsunghealth.ExternalWorkoutRecheck
import com.fitmate.app.samsunghealth.ExternalWorkoutLookupResult
import com.fitmate.app.samsunghealth.ExternalWorkoutLookupState
import com.fitmate.app.samsunghealth.MatchConfidence
import com.fitmate.app.samsunghealth.WorkoutMatchCandidate
import com.fitmate.app.samsunghealth.WorkoutMatchAnalysis
import com.fitmate.app.samsunghealth.WorkoutMatchEngine
import com.fitmate.app.samsunghealth.WorkoutSourceKey
import com.fitmate.app.samsunghealth.recheckExternalWorkoutSnapshot
import com.fitmate.app.samsunghealth.classifyExternalWorkoutLookup
import com.fitmate.app.samsunghealth.toExternalWorkoutSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonObject
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
    sessionActiveDurationSeconds: Long? = null,
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
    var pickerOpen by remember(sessionId) { mutableStateOf(false) }
    var deferred by remember(sessionId) { mutableStateOf(false) }
    var showExcluded by remember(sessionId) { mutableStateOf(false) }
    var analysisPayload by remember(sessionId) { mutableStateOf<JsonObject?>(null) }
    var analysisLoaded by remember(sessionId) { mutableStateOf(false) }
    var analysisError by remember(sessionId) { mutableStateOf<String?>(null) }
    var analysisAttempt by remember(sessionId) { mutableStateOf(0) }
    var storedSourceLookup by remember(sessionId) { mutableStateOf<ExternalWorkoutLookupResult?>(null) }
    var reviewingStoredModification by remember(sessionId) { mutableStateOf(false) }

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
    LaunchedEffect(sessionId, sportCode, stored?.version, analysisAttempt) {
        if (sportCode != "RUNNING" || stored == null) {
            analysisPayload = null
            analysisError = null
            analysisLoaded = true
        } else {
            analysisLoaded = false
            analysisError = null
            try {
                analysisPayload = withContext(Dispatchers.IO) { server.analysis(sessionId) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                analysisError = failure.message ?: "심박수·페이스 분석을 불러오지 못했어요."
            } finally {
                analysisLoaded = true
            }
        }
    }

    val linkedSourceKeys = stored?.summary?.let {
        setOf(WorkoutSourceKey(it.sourceAppId ?: "", it.sourceRecordId))
    }.orEmpty()
    val analysis = remember(
        samsungState.workouts,
        sessionStartedAt,
        sessionEndedAt,
        sportCode,
        sessionExerciseNames,
        deviceLoadedForSession,
        linkedSourceKeys,
        sessionActiveDurationSeconds,
    ) {
        WorkoutMatchEngine.analyze(
            sportCode = sportCode,
            sessionStartedAt = sessionStartedAt,
            sessionEndedAt = sessionEndedAt,
            workouts = if (deviceLoadedForSession) samsungState.workouts else emptyList(),
            sessionExerciseNames = sessionExerciseNames,
            excludedSourceKeys = linkedSourceKeys,
            sessionActiveDurationSeconds = sessionActiveDurationSeconds,
        )
    }
    val candidates = analysis.candidates
    LaunchedEffect(candidates, stored) {
        selected = selected?.takeIf { current ->
            candidates.any { it.uid == current.uid && it.sourceAppId == current.sourceAppId } ||
                stored?.summary?.let { it.sourceRecordId == current.uid && it.sourceAppId == current.sourceAppId } == true
        }
    }

    fun unlinkStored(successMessage: String) {
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { server.delete(sessionId) }
                stored = null
                selected = null
                pickerOpen = false
                storedSourceLookup = null
                reviewingStoredModification = false
                message = successMessage
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                message = failure.message ?: "Samsung Health 연결을 삭제하지 못했습니다."
            } finally {
                saving = false
            }
        }
    }

    fun recheckStoredSource(linked: StoredExternalWorkout) {
        selected = null
        pickerOpen = false
        reviewingStoredModification = false
        message = "기기 원본을 다시 읽는 중이에요. 기존 연결은 확인이 끝날 때까지 유지됩니다."
        loadWorkouts(sessionStartedAt, sessionEndedAt) { refreshed ->
            deviceLoadedForSession = true
            val lookup = classifyExternalWorkoutLookup(
                refreshed,
                linked.summary.sourceRecordId,
                linked.summary.sourceAppId,
            )
            storedSourceLookup = lookup
            when (lookup.state) {
                ExternalWorkoutLookupState.FOUND -> {
                    val found = requireNotNull(lookup.workout)
                    if (linked.summary.normalized() == found.toExternalWorkoutSummary().normalized()) {
                        message = "Samsung Health 원본을 확인했습니다. 기존 연결을 유지합니다."
                    } else {
                        val eligible = WorkoutMatchEngine.candidates(
                            sportCode,
                            sessionStartedAt,
                            sessionEndedAt,
                            listOf(found),
                            sessionActiveDurationSeconds = sessionActiveDurationSeconds,
                        ).firstOrNull()
                        if (eligible == null) {
                            message = "원본 기록이 변경되어 현재 연결 조건에서 벗어났습니다. 기존 연결은 유지됩니다."
                        } else {
                            selected = eligible
                            pickerOpen = true
                            reviewingStoredModification = true
                            message = "원본 기록이 변경되었습니다. 변경 내용을 확인하고 다시 확정해 주세요. 취소하면 기존 연결을 유지합니다."
                        }
                    }
                }
                ExternalWorkoutLookupState.NOT_FOUND -> {
                    message = "Samsung Health 원본을 찾지 못했습니다. 기존 요약과 시계열은 보존됩니다. 원본 삭제가 확실하면 직접 연결을 해제할 수 있습니다."
                }
                ExternalWorkoutLookupState.PERMISSION_ERROR -> {
                    message = "Samsung Health 권한을 확인하지 못했습니다. 기존 요약과 시계열은 보존됩니다. 권한 확인 후 다시 시도해 주세요."
                }
                ExternalWorkoutLookupState.PLATFORM_ERROR -> {
                    message = "Samsung Health 플랫폼을 사용할 수 없어 원본을 확인하지 못했습니다. 기존 기록은 보존됩니다. 다시 시도해 주세요."
                }
                ExternalWorkoutLookupState.TEMPORARY_FAILURE -> {
                    message = "Samsung Health 원본을 일시적으로 확인하지 못했습니다. 기존 기록은 보존됩니다. 잠시 후 다시 시도해 주세요."
                }
            }
        }
    }

        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                SamsungSummaryCard(linked.summary, linked = true)
                if (sportCode == "RUNNING") {
                    RunningAnalysisCard(
                        payload = analysisPayload,
                        totalSampleCount = linked.timeSeriesSampleCount,
                        loading = !analysisLoaded,
                        error = analysisError,
                    )
                    if (analysisError != null) TextButton({ analysisAttempt++ }) { Text("분석 다시 불러오기") }
                }
                OutlinedButton(
                    { recheckStoredSource(linked) },
                    enabled = !saving && samsungState.status != SamsungHealthStatus.LOADING,
                ) { Text("원본 다시 확인") }
                when (storedSourceLookup?.state) {
                    ExternalWorkoutLookupState.PERMISSION_ERROR -> {
                        Text("기존 서버 기록은 삭제하지 않았습니다.", style = MaterialTheme.typography.bodySmall)
                        Button(requestPermission) { Text("운동 읽기 권한 확인") }
                        OutlinedButton({ recheckStoredSource(linked) }, enabled = !saving) { Text("원본 조회 다시 시도") }
                    }
                    ExternalWorkoutLookupState.PLATFORM_ERROR,
                    ExternalWorkoutLookupState.TEMPORARY_FAILURE,
                    -> {
                        Text("기존 서버 기록은 삭제하지 않았습니다.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton({ recheckStoredSource(linked) }, enabled = !saving) { Text("원본 조회 다시 시도") }
                    }
                    ExternalWorkoutLookupState.NOT_FOUND -> {
                        Text("연결 해제 전까지 기존 서버 기록을 계속 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
                if (storedSourceLookup?.state !in setOf(
                        ExternalWorkoutLookupState.PERMISSION_ERROR,
                        ExternalWorkoutLookupState.PLATFORM_ERROR,
                        ExternalWorkoutLookupState.TEMPORARY_FAILURE,
                    )
                ) {
                    OutlinedButton({
                        val lookup = storedSourceLookup
                        if (lookup?.state != ExternalWorkoutLookupState.NOT_FOUND || lookup.allowsExplicitUnlink(userConfirmed = true)) {
                            unlinkStored("Samsung Health 연결과 시계열을 삭제했습니다. FitMate 직접 기록은 유지됩니다.")
                        }
                    }, enabled = !saving) {
                        Text(if (storedSourceLookup?.state == ExternalWorkoutLookupState.NOT_FOUND) "찾을 수 없는 원본 연결 해제" else "Samsung Health 연결 해제")
                    }
                }
            }

            if (stored == null && serverLoaded && serverError == null && !pickerOpen) {
                if (deferred) SamsungSurface {
                    Text("기본 리포트를 먼저 확인하세요", fontWeight = FontWeight.SemiBold)
                    Text("기기 기록은 동기화가 끝난 뒤 연결할 수 있어요.")
                    OutlinedButton({ deferred = false; pickerOpen = true }) { Text("Samsung Health 기록 연결") }
                } else SamsungEndGuidance(onLoad = {
                    pickerOpen = true
                    if (samsungState.permissions.any { it.label == "운동 읽기" && it.granted }) {
                        loadWorkouts(sessionStartedAt, sessionEndedAt) { deviceLoadedForSession = true }
                    }
                }, onSkip = { deferred = true }, enabled = !saving && samsungState.status != SamsungHealthStatus.LOADING)
            }
            if (!pickerOpen) message?.let { Text(it) }
            if (pickerOpen) SamsungPickerPage(saving, {
                pickerOpen = false
                selected = null
                if (reviewingStoredModification) {
                    reviewingStoredModification = false
                    message = "변경된 원본 연결을 취소했습니다. 기존 연결을 유지합니다."
                }
            }) {
            Text("FitMate · ${samsungTime(sessionStartedAt)} – ${samsungTime(sessionEndedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("운동 종류와 시간을 확인해 주세요. 선택한 기록만 리포트에 연결돼요.")
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
                        TextButton({
                            selected = null
                            pickerOpen = false
                            message = "Samsung Health 기록을 연결하지 않고 기본 리포트를 유지합니다."
                        }) { Text("연결하지 않기") }
                    }
                    else -> {
                        Text("같은 시간대의 운동 ${candidates.size}건을 찾았어요.")
                        analysis.recommendations.forEach { recommendation ->
                        val candidate = recommendation.workout
                        OutlinedButton({ selected = candidate; message = null }, Modifier.fillMaxWidth(), enabled = !saving) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected?.uid == candidate.uid && selected?.sourceAppId == candidate.sourceAppId, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.customTitle ?: samsungExerciseName(candidate.exerciseType), fontWeight = FontWeight.SemiBold)
                                    Text("${samsungTime(candidate.startTime)} – ${samsungTime(candidate.endTime)}", style = MaterialTheme.typography.bodySmall)
                                    Text("활동 ${samsungDuration(candidate.activeDurationSeconds)}", style = MaterialTheme.typography.bodySmall)
                                    RecommendationEvidence(recommendation)
                                }
                            }
                        }
                    }
                        TextButton({ loadWorkouts(sessionStartedAt, sessionEndedAt) { deviceLoadedForSession = true } }, enabled = !saving) { Text("다시 불러오기") }
                        OutlinedButton({
                            selected = null
                            pickerOpen = false
                            message = "Samsung Health 기록을 연결하지 않고 기본 리포트를 유지합니다."
                        }, Modifier.fillMaxWidth(), enabled = !saving) { Text("연결하지 않기") }
                    }
                }
                if (deviceLoadedForSession && samsungState.status in setOf(SamsungHealthStatus.READY, SamsungHealthStatus.DATA_EMPTY)) {
                    val excluded = analysis.unsupported.size + analysis.timeMismatchCount + analysis.compoundCount +
                        analysis.incompleteCount + analysis.alreadyLinkedCount
                    if (excluded > 0) {
                        TextButton({ showExcluded = !showExcluded }) { Text(if (showExcluded) "제외된 기록 안내 접기" else "연결할 수 없는 기록 안내 ($excluded)") }
                        if (showExcluded) MatchExclusionGuidance(analysis, sportCode)
                    }
                }
            }

            selected?.let { candidate ->
                SamsungSummaryCard(candidate.toExternalWorkoutSummary(), linked = false)
                val selectedRecommendation = WorkoutMatchEngine.analyze(
                    sportCode = sportCode,
                    sessionStartedAt = sessionStartedAt,
                    sessionEndedAt = sessionEndedAt,
                    workouts = listOf(candidate),
                    sessionExerciseNames = sessionExerciseNames,
                    sessionActiveDurationSeconds = sessionActiveDurationSeconds,
                ).recommendations.firstOrNull()
                selectedRecommendation?.let { RecommendationEvidence(it) }
                if (selectedRecommendation?.requiresExplicitConfirmation == true) {
                    Text(
                        "낮은 신뢰 후보입니다. 자동으로 연결되지 않으며 아래 버튼을 눌러야 최종 확정됩니다.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("확인한 운동 요약만 저장해요. FitMate의 직접 기록은 변경하지 않아요.", style = MaterialTheme.typography.bodySmall)
                Button({
                    saving = true; message = null
                    loadWorkouts(sessionStartedAt, sessionEndedAt) { refreshed ->
                        deviceLoadedForSession = true
                        val lookup = classifyExternalWorkoutLookup(refreshed, candidate.uid, candidate.sourceAppId)
                        when (lookup.state) {
                            ExternalWorkoutLookupState.NOT_FOUND -> {
                                selected = null
                                saving = false
                                message = "저장 직전 Samsung Health 원본이 삭제된 것을 확인했습니다. 다른 후보를 선택하거나 연결하지 않기를 선택해 주세요."
                            }
                            ExternalWorkoutLookupState.PERMISSION_ERROR -> {
                                saving = false
                                message = "권한 문제로 원본을 재확인하지 못했습니다. 기존 연결은 보존되며 권한 확인 후 다시 시도할 수 있습니다."
                            }
                            ExternalWorkoutLookupState.PLATFORM_ERROR -> {
                                saving = false
                                message = "Samsung Health 플랫폼 오류로 원본을 재확인하지 못했습니다. 기존 연결은 보존되며 다시 시도할 수 있습니다."
                            }
                            ExternalWorkoutLookupState.TEMPORARY_FAILURE -> {
                                saving = false
                                message = "일시적인 오류로 원본을 재확인하지 못했습니다. 기존 연결은 보존되며 다시 시도할 수 있습니다."
                            }
                            ExternalWorkoutLookupState.FOUND -> {
                                val refreshedSource = requireNotNull(lookup.workout)
                                val eligibleCurrent = WorkoutMatchEngine.candidates(
                                    sportCode,
                                    sessionStartedAt,
                                    sessionEndedAt,
                                    listOf(refreshedSource),
                                    sessionActiveDurationSeconds = sessionActiveDurationSeconds,
                                ).firstOrNull()
                                when (recheckExternalWorkoutSnapshot(candidate, refreshedSource)) {
                                    ExternalWorkoutRecheck.MISSING -> error("FOUND lookup must include its source workout")
                                    ExternalWorkoutRecheck.MODIFIED -> {
                                        selected = eligibleCurrent
                                        saving = false
                                        reviewingStoredModification = stored != null && eligibleCurrent != null
                                        message = if (eligibleCurrent == null) {
                                            "원본 기록이 변경되어 연결 조건에서 벗어났습니다. 기존 연결은 유지됩니다."
                                        } else {
                                            "원본 기록이 선택 후 변경되었습니다. 변경된 시간과 요약을 확인한 뒤 다시 확정해 주세요. 취소하면 기존 연결을 유지합니다."
                                        }
                                    }
                                    ExternalWorkoutRecheck.UNCHANGED -> {
                                        val request = refreshedSource.toExternalWorkoutSummary()
                            scope.launch {
                                try {
                                    stored = withContext(Dispatchers.IO) { server.put(sessionId, request, refreshedSource.timeSeries, stored?.version) }
                                    selected = null
                                    pickerOpen = false
                                    reviewingStoredModification = false
                                    storedSourceLookup = ExternalWorkoutLookupResult(ExternalWorkoutLookupState.FOUND, refreshedSource)
                                    message = "Samsung Health 요약을 저장했습니다."
                                } catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    val recovered = try { withContext(Dispatchers.IO) { server.get(sessionId) } }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { null }
                                    if (recovered != null && recovered.sessionId == sessionId && externalWorkoutContentMatches(request, recovered)) {
                                        stored = recovered; selected = null; pickerOpen = false; message = "저장 여부를 확인해 완료 상태를 복원했습니다."
                                    } else message = failure.message ?: "외부 요약 저장에 실패했습니다. 기본 리포트는 유지됩니다."
                                } finally { saving = false }
                            }
                                    }
                                }
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !saving && samsungState.status != SamsungHealthStatus.LOADING) {
                    Text(when {
                        saving -> "원본 확인 및 저장 중…"
                        selectedRecommendation?.requiresExplicitConfirmation == true -> "낮은 신뢰 후보 확인 후 연결"
                        stored == null -> "확인하고 리포트에 연결"
                        else -> "변경 내용 확인 후 저장"
                    })
                }
            }
            if (stored != null && selected == null) {
                if (samsungState.status == SamsungHealthStatus.LOADING) CircularProgressIndicator()
                else TextButton({ pickerOpen = false }) { Text("리포트로 돌아가기") }
            }
            message?.let { Text(it) }
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
            if (sportCode == "HEALTH") "FitMate 운동과 직접 대응하는 지원 코드의 단일 기록만 연결할 수 있어요."
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
                if (sportCode == "HEALTH") "Fit3에서 연결할 운동을 단일 운동으로 기록해 주세요."
                else "단일 운동으로 다시 기록해 주세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (analysis.incompleteCount > 0) {
        Text("활동 시간이 없는 기록 ${analysis.incompleteCount}건은 제외했습니다. Samsung Health에서 기록 내용을 확인해 주세요.", style = MaterialTheme.typography.bodySmall)
    }
    if (analysis.alreadyLinkedCount > 0) {
        Text("이미 이 세션에 연결된 원본 ${analysis.alreadyLinkedCount}건은 중복 후보에서 제외했습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecommendationEvidence(recommendation: WorkoutMatchCandidate) {
    Text("추천 ${recommendation.score}점 · 신뢰 ${recommendation.confidence.label}", style = MaterialTheme.typography.bodySmall)
    Text(
        "시간 겹침 ${samsungDuration(recommendation.overlapSeconds)} (${recommendation.overlapPercent}%)",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("운동 종류 ${recommendation.typeMatch.label}", style = MaterialTheme.typography.bodySmall)
    Text(
        "활동 시간 차이 ${samsungDuration(recommendation.activeDurationDifferenceSeconds)} · ${recommendation.fitMateDurationBasis.label}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("제목 ${recommendation.titleMatch.label} · 보조 근거", style = MaterialTheme.typography.bodySmall)
    recommendation.reasons.forEach { reason -> Text("• $reason", style = MaterialTheme.typography.bodySmall) }
    recommendation.cautions.forEach { caution ->
        Text("주의: $caution", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (recommendation.confidence == MatchConfidence.LOW && recommendation.cautions.isEmpty()) {
        Text("주의: 추천 근거가 약합니다. 연결하지 않기를 선택할 수 있어요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryText(type: String, seconds: Long, calories: String?, heartRate: String?) {
    Text("$type · ${seconds / 60}분")
    Text("칼로리 ${calories?.let { "$it kcal" } ?: "기록 없음"} · 평균 심박 ${heartRate?.let { "$it bpm" } ?: "기록 없음"}")
}

private fun formatExternalTime(value: Instant) = value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
