package com.fitmate.app.ui

import androidx.compose.runtime.*
import com.fitmate.app.data.model.WorkoutSet

/** The root snapshots this session into app-private local storage. */
internal class WorkoutSession(initialExercise: String, val useSamsung: Boolean) {
    var exerciseName by mutableStateOf(initialExercise)
    var exerciseDraft by mutableStateOf("")
    var selectedExerciseIndex by mutableStateOf(0)
    var weightText by mutableStateOf("30")
    var repetitionText by mutableStateOf("12")
    var rpeText by mutableStateOf("7")
    var memo by mutableStateOf("")
    var targetSetCount by mutableStateOf(6)
    var restPresetSeconds by mutableStateOf(90)
    var remainingRestSeconds by mutableStateOf(0)
    var isRestTimerRunning by mutableStateOf(false)
    var elapsedSeconds by mutableStateOf(0)
    var isTimerRunning by mutableStateOf(true)
    var editingSetIndex by mutableStateOf(-1)
    var isSafetyNoticeVisible by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var unit by mutableStateOf("kg")
    fun changeUnit(next: String) {
        if (next == unit) return
        weightText.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
            weightText = "%.2f".format(java.util.Locale.US, if (next == "lb") it * 2.2046226218 else it / 2.2046226218)
        }
        unit = next
    }
    val sessionExercises = mutableStateListOf<String>().apply { if (initialExercise.isNotBlank()) add(initialExercise) }
    val recordedSets = mutableStateListOf<WorkoutSet>()
    fun pause() { isTimerRunning = false; isRestTimerRunning = false }
    fun recordSet(): Boolean {
        val weight = weightText.toDoubleOrNull()
        val repetitions = repetitionText.toIntOrNull()
        val rpe = rpeText.toIntOrNull()
        if (exerciseName.isBlank()) { error = "운동을 먼저 추가해 주세요."; return false }
        if (weight == null || !weight.isFinite() || weight < 0 || repetitions == null || repetitions <= 0 ||
            (rpeText.isNotBlank() && (rpe == null || rpe !in 1..10))) {
            error = "중량·반복 횟수·RPE를 확인해 주세요."; return false
        }
        val set = WorkoutSet(exerciseName, if (unit == "lb") weight / 2.2046226218 else weight, repetitions, rpe, memo)
        if (editingSetIndex in recordedSets.indices) recordedSets[editingSetIndex] = set else recordedSets += set
        editingSetIndex = -1; error = null
        remainingRestSeconds = restPresetSeconds; isRestTimerRunning = true
        return true
    }
}
