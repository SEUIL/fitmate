package com.fitmate.app.ui

import android.content.Context
import android.util.AtomicFile
import com.fitmate.app.data.model.UserProfile
import com.fitmate.app.data.model.WorkoutSet
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.running.RunRoutePoint
import com.google.gson.Gson
import java.io.File

internal data class SavedRoutine(val name: String, val exercises: List<String>)
internal data class SessionSnapshot(
    val exercises: List<String>, val selected: Int, val exercise: String,
    val draft: String, val weight: String, val repetitions: String, val rpe: String, val memo: String,
    val target: Int, val restPreset: Int, val restRemaining: Int, val elapsed: Int,
    val editing: Int, val sets: List<WorkoutSet>, val samsung: Boolean, val unit: String = "kg", val serverState: String? = null
) {
    fun restore() = WorkoutSession(exercise, samsung).also {
        it.sessionExercises.clear(); it.sessionExercises.addAll(exercises)
        it.selectedExerciseIndex = selected.coerceIn(0, exercises.lastIndex.coerceAtLeast(0))
        it.exerciseDraft = draft; it.weightText = weight; it.repetitionText = repetitions
        it.rpeText = rpe; it.memo = memo; it.targetSetCount = target
        it.restPresetSeconds = restPreset; it.remainingRestSeconds = restRemaining
        it.elapsedSeconds = elapsed; it.editingSetIndex = editing
        it.recordedSets.addAll(sets); it.unit = unit; it.pause()
        it.serverState = serverState
    }
}

internal fun WorkoutSession.snapshot() = SessionSnapshot(sessionExercises.toList(), selectedExerciseIndex,
    exerciseName, exerciseDraft, weightText, repetitionText, rpeText, memo, targetSetCount,
    restPresetSeconds, remainingRestSeconds, elapsedSeconds, editingSetIndex, recordedSets.toList(), useSamsung, unit, serverState)

/** Per-account local feature snapshot. Passwords and tokens are never stored here. */
internal data class FrontendSnapshot(
    val version: Int = 1, val profile: UserProfile? = null, val signedIn: Boolean = false,
    val workout: SessionSnapshot? = null, val history: List<WorkoutSummary> = emptyList(),
    val routines: List<SavedRoutine> = emptyList(), val photo: String? = null,
    val healthConsent: Boolean = false, val locationConsent: Boolean = false,
    val running: RunningSnapshot? = null,
    val social: SocialSnapshot? = null,
    val body: List<BodySnapshot>? = null,
    val mates: MateSnapshot? = null,
    val sportRecord: SportDraft? = null
)

internal data class RunningSnapshot(
    val phase: String, val goal: String, val distance: String, val minutes: String, val pace: String,
    val interval: String, val auto: Boolean, val route: Boolean, val visibility: String,
    val samsung: Boolean, val signal: String, val demo: Boolean, val stationary: Boolean,
    val seconds: Int, val meters: Int, val target: Int, val laps: List<RunLap>,
    val rpe: String, val condition: String, val memo: String, val history: List<RunResult>, val pendingUpload: String? = null,
    val completedAt: Long? = null, val startedAt: Long? = null,
    val elapsedSeconds: Int = seconds, val routePoints: List<RunRoutePoint> = emptyList(),
    val savedRecordId: String? = null,
) {
    fun restore() = RunningState().also {
        it.phase = phase; it.goal = goal; it.targetDistance = distance; it.targetMinutes = minutes
        it.targetPace = pace; it.intervalMinutes = interval; it.autoPause = auto; it.recordRoute = route
        it.visibility = visibility; it.samsung = samsung; it.signal = signal; it.demo = demo
        it.stationary = stationary; it.seconds = seconds; it.meters = meters; it.target = target
        it.laps = laps; it.rpe = rpe; it.condition = condition; it.memo = memo
        it.history.addAll(history); it.paused = true
        it.pendingUpload = pendingUpload
        it.completedAt = completedAt
        it.startedAt = startedAt
        it.elapsedSeconds = elapsedSeconds.coerceAtLeast(seconds)
        it.routePoints = if (route) routePoints else emptyList()
        it.savedRecordId = savedRecordId
    }
}
internal fun RunningState.snapshot() = RunningSnapshot(
    phase = phase, goal = goal, distance = targetDistance, minutes = targetMinutes, pace = targetPace,
    interval = intervalMinutes, auto = autoPause, route = recordRoute, visibility = visibility,
    samsung = samsung, signal = signal, demo = demo, stationary = stationary, seconds = seconds,
    meters = meters, target = target, laps = laps, rpe = rpe, condition = condition, memo = memo,
    history = history.toList(), pendingUpload = pendingUpload, completedAt = completedAt, startedAt = startedAt,
    elapsedSeconds = elapsedSeconds, routePoints = if (recordRoute) routePoints else emptyList(),
    savedRecordId = savedRecordId,
)

/** App-private, excluded from cloud/device backup by noBackupFilesDir. */
internal class FrontendLocalStore(context: Context, userId: String? = null) {
    private val file = AtomicFile(File(context.noBackupFilesDir, if (userId == null) "frontend-demo-v1.json" else "frontend-${java.util.UUID.fromString(userId)}.json"))
    private val gson = Gson()
    private var closed = false
    private var issued = 0L
    private var written = 0L
    @Synchronized fun revision(): Long = ++issued
    var readFailed = false
        private set
    @Synchronized fun load(): FrontendSnapshot {
        if (!file.baseFile.exists()) return FrontendSnapshot()
        return runCatching {
            gson.fromJson(file.openRead().bufferedReader().use { it.readText() }, FrontendSnapshot::class.java)
                .also { value -> require(value.version == 1); value.body?.forEach { it.restore() } }
        }.getOrElse { readFailed = true; FrontendSnapshot() }
    }
    @Synchronized fun save(value: FrontendSnapshot, revision: Long = revision()): Boolean {
        if (closed || revision < written) return true
        if (readFailed) return false
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = file.startWrite(); stream.write(gson.toJson(value).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream); written = revision; true
        } catch (_: Exception) { stream?.let { file.failWrite(it) }; false }
    }
    @Synchronized fun clearAndClose(): Boolean {
        val wasReadFailed = readFailed
        readFailed = false
        val success = save(FrontendSnapshot())
        if (success) closed = true else readFailed = wasReadFailed
        return success
    }
}
