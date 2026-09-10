package com.fitmate.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.data.remote.ApiExercise
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorkoutScreen(
    modifier: Modifier,
    session: WorkoutSession,
    history: List<WorkoutSummary>,
    availableExercises: List<ApiExercise>,
    serverWorkout: ServerWorkout?,
    completeServer: suspend (WorkoutSummary) -> Unit,
    saveRoutine: (SavedRoutine) -> Boolean,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    finish: (WorkoutSummary) -> Unit
) = with(session) {
    val uploadScope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // In landscape, the IME can leave less height than the two fixed action bars.
    val scrollActions = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var completing by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }
    var showWorkoutOverview by remember(session) { mutableStateOf(false) }
    BackHandler(enabled = showWorkoutOverview) { showWorkoutOverview = false }
    var recordConfirmation by remember(session) { mutableStateOf<String?>(null) }
    var showSetDetails by remember(session) { mutableStateOf(false) }
    val currentVolume = recordedSets.sumOf { it.weightKg * it.repetitions }
    val elapsedTime = if (elapsedSeconds >= 3600) "%02d:%02d:%02d".format(elapsedSeconds / 3600, elapsedSeconds / 60 % 60, elapsedSeconds % 60)
        else "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val displayedRestSeconds = if (remainingRestSeconds > 0) remainingRestSeconds else restPresetSeconds

    fun complete() {
        if (completing) return
        val summary = WorkoutSummary(elapsedSeconds / 60, recordedSets.toList(), useSamsung, targetSetCount, System.currentTimeMillis())
        completing = true; completionError = null
        uploadScope.launch {
            try { completeServer(summary); finish(summary) }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                syncError = failure.message; error = failure.message
                completionError = failure.message ?: "운동 기록을 저장하지 못했어요."
            } finally { completing = false }
        }
    }
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) { delay(1_000); elapsedSeconds += 1 }
    }
    LaunchedEffect(isRestTimerRunning) {
        while (isRestTimerRunning) {
            delay(1_000)
            if (remainingRestSeconds > 1) remainingRestSeconds -= 1
            else { remainingRestSeconds = 0; isRestTimerRunning = false }
        }
    }
    LaunchedEffect(recordConfirmation) {
        if (recordConfirmation != null) { delay(2_000); recordConfirmation = null }
    }
    @Composable fun topActions() {
        TopAppBar(title = { Text(if (showWorkoutOverview) "전체 운동" else "운동 진행", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = { IconButton({ if (showWorkoutOverview) showWorkoutOverview = false else onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기")
            } },
            actions = { TextButton(::complete, enabled = !completing) { Text(if (completing) "저장 중…" else "운동 종료") } })
    }
    @Composable fun recordAction() {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            recordConfirmation?.let { Text(it, Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.primary) }
            WorkoutAction(if (editingSetIndex >= 0) "세트 수정" else "이 세트 기록", enabled = !completing) {
                val wasEditing = editingSetIndex in recordedSets.indices
                val index = if (wasEditing) editingSetIndex else recordedSets.size
                if (recordSet()) {
                    focus.clearFocus()
                    recordConfirmation = "✓ ${index + 1}세트 ${if (wasEditing) "수정" else "기록"} 완료"
                } else recordConfirmation = null
            }
        }
    }
    Scaffold(modifier.imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (!scrollActions) topActions() },
        bottomBar = {
            if (!showWorkoutOverview && !scrollActions) Surface(color = MaterialTheme.colorScheme.background) { recordAction() }
        }
    ) { padding ->
        Screen(Modifier.padding(padding)) {
            if (scrollActions) topActions()
            if (showWorkoutOverview) {
                WorkoutOverviewContent(session, history, availableExercises, serverWorkout, saveRoutine) { showWorkoutOverview = false }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("운동 ${if (sessionExercises.isEmpty()) 0 else selectedExerciseIndex + 1} / ${sessionExercises.size}", Modifier.weight(1f))
                    Text(elapsedTime, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { if (sessionExercises.isEmpty()) 0f else ((selectedExerciseIndex + 1).toFloat() / sessionExercises.size).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer,
                    trackColor = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(exerciseName.ifBlank { "운동을 추가해 주세요" }, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    TextButton({ showWorkoutOverview = true }) { Text("전체 운동 보기") }
                }
                val previous = history.asReversed().firstNotNullOfOrNull { summary -> summary.sets.lastOrNull { it.exerciseName == exerciseName } }
                Text(previous?.let { "이전 기록 · ${it.weightKg} kg × ${it.repetitions}회" } ?: "이전 동일 운동 기록 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
                syncError?.takeIf { it != error }?.let { ErrorText(it) }
                WorkoutCard {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (editingSetIndex >= 0) "${editingSetIndex + 1}세트 수정 중" else "${recordedSets.count { it.exerciseName == exerciseName } + 1}세트 기록",
                            modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("kg", "lb").forEach { choice ->
                                FilterChip(unit == choice, { changeUnit(choice); recordConfirmation = null }, label = { Text(choice) })
                            }
                        }
                    }
                    BoxWithConstraints {
                        val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
                        @Composable fun fields(modifier: Modifier) {
                            WorkoutNumberField(weightText, { weightText = it; recordConfirmation = null }, "중량 ($unit)", modifier, KeyboardType.Decimal)
                            WorkoutNumberField(repetitionText, { repetitionText = it; recordConfirmation = null }, "횟수", modifier, KeyboardType.Number)
                            WorkoutNumberField(rpeText, { rpeText = it; recordConfirmation = null }, "RPE", modifier, KeyboardType.Number)
                        }
                        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier.fillMaxWidth()) }
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier.weight(1f)) }
                    }
                    if (showSetDetails || memo.isNotEmpty()) OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth(), label = { Text("세트 메모 (선택)") })
                    TextButton({ showSetDetails = !showSetDetails }) { Text(if (showSetDetails) "메모 닫기" else "+ 메모 추가") }
                    ErrorText(error)
                }
                if (scrollActions) recordAction()
                if (recordedSets.isNotEmpty()) WorkoutCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("기록한 세트", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("총 ${recordedSets.size}세트 · ${"%.0f".format(currentVolume)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ showWorkoutOverview = true }) { Text("전체 기록·삭제") }
                    }
                    recordedSets.withIndex().filter { it.value.exerciseName == exerciseName }.forEach { (index, set) ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}세트 · ${set.weightKg} kg × ${set.repetitions}회" + (set.rpe?.let { " · RPE $it" } ?: ""), Modifier.weight(1f))
                            TextButton({
                                weightText = (if (unit == "lb") set.weightKg * 2.2046226218 else set.weightKg).toString()
                                repetitionText = set.repetitions.toString(); rpeText = set.rpe?.toString().orEmpty()
                                memo = set.memo; editingSetIndex = index; recordConfirmation = null
                            }) { Text("수정") }
                        }
                    }
                }
                WorkoutCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("휴식 타이머", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (isRestTimerRunning) "휴식 중" else if (remainingRestSeconds > 0) "일시정지" else "다음 휴식", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%02d:%02d".format(displayedRestSeconds / 60, displayedRestSeconds % 60), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({
                            if (isRestTimerRunning) isRestTimerRunning = false
                            else { if (remainingRestSeconds == 0) remainingRestSeconds = restPresetSeconds; isRestTimerRunning = true }
                        }) { Text(if (isRestTimerRunning) "일시정지" else if (remainingRestSeconds > 0) "재개" else "휴식 시작") }
                        OutlinedButton({ remainingRestSeconds = (remainingRestSeconds.takeIf { it > 0 } ?: restPresetSeconds) + 30 }) { Text("+ 30초") }
                        TextButton({ restPresetSeconds = if (restPresetSeconds == 30) 60 else if (restPresetSeconds == 60) 90 else 30; remainingRestSeconds = 0; isRestTimerRunning = false }) { Text("설정 ${restPresetSeconds}초") }
                    }
                }
                OutlinedButton({ isSafetyNoticeVisible = !isSafetyNoticeVisible }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("통증·불편 시 운동 중단") }
                if (isSafetyNoticeVisible) WorkoutCard {
                    Text("안전 안내", style = MaterialTheme.typography.titleMedium)
                    Text("날카로운 통증, 어지러움, 호흡 곤란이 느껴지면 즉시 운동을 멈추고 필요한 도움을 받으세요.")
                    OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("운동 취소하고 홈으로") }
                }
            }
        }
    }
    if (completing) AlertDialog(onDismissRequest = {}, title = { Text("운동 기록을 저장하고 있어요") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    completionError?.let { message -> AlertDialog(onDismissRequest = { completionError = null },
        title = { Text("운동 기록을 저장하지 못했어요") }, text = { Text(message) },
        dismissButton = { TextButton({ completionError = null }) { Text("닫기") } },
        confirmButton = { TextButton(::complete) { Text("다시 저장") } }) }
}

@Composable private fun WorkoutNumberField(value: String, change: (String) -> Unit, label: String, modifier: Modifier, keyboard: KeyboardType) {
    OutlinedTextField(value, change, modifier, label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
