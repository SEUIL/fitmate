package com.fitmate.backend.report.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.report.api.PreviousWorkoutComparisonResponse
import com.fitmate.backend.report.api.WorkoutExerciseReportResponse
import com.fitmate.backend.report.api.WorkoutReportResponse
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.domain.WorkoutSet
import com.fitmate.backend.workout.repository.WorkoutSessionExerciseRepository
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import com.fitmate.backend.workout.repository.WorkoutSetRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

@Service
class WorkoutReportService(
    private val sessions: WorkoutSessionRepository,
    private val sessionExercises: WorkoutSessionExerciseRepository,
    private val sets: WorkoutSetRepository,
) {
    @Transactional(readOnly = true)
    fun get(userId: UUID, sessionId: UUID): WorkoutReportResponse {
        val session = sessions.findByIdAndUserId(dbId(sessionId), userId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동 세션을 찾을 수 없습니다.")
        if (session.status != WorkoutSessionStatus.COMPLETED || session.endedAt == null) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "완료한 운동만 리포트를 조회할 수 있습니다.")
        }
        val exerciseReports = sessionExercises
            .findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
            .map { sessionExercise ->
                val exercise = requireNotNull(sessionExercise.exercise)
                val completed = sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(sessionExercise.id))
                    .filter(WorkoutSet::isCompleted)
                val currentVolume = volume(completed)
                val previous = sessionExercises.findPreviousCompleted(
                    requireNotNull(exercise.id), userId, WorkoutSessionStatus.COMPLETED,
                    session.startedAt, PageRequest.of(0, 1),
                ).firstOrNull()
                val comparison = previous?.let {
                    val previousSets = sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(it.id))
                        .filter(WorkoutSet::isCompleted)
                    val previousVolume = volume(previousSets)
                    PreviousWorkoutComparisonResponse(
                        previousWorkoutSessionId = apiId(requireNotNull(it.workoutSession?.id)),
                        previousMaxWeightKg = maxWeight(previousSets), currentMaxWeightKg = maxWeight(completed),
                        previousVolumeKg = previousVolume, currentVolumeKg = currentVolume,
                        volumeChangeKg = currentVolume.subtract(previousVolume),
                    )
                }
                WorkoutExerciseReportResponse(
                    exerciseId = apiId(requireNotNull(exercise.id)), exerciseName = sessionExercise.displayNameSnapshot,
                    bodyPart = sessionExercise.bodyPartSnapshot, completedSetCount = completed.size,
                    totalRepetitions = completed.sumOf { it.repetitions ?: 0 }, totalVolumeKg = currentVolume,
                    maxWeightKg = maxWeight(completed), previousComparison = comparison,
                )
            }
        return WorkoutReportResponse(
            workoutSessionId = apiId(requireNotNull(session.id)), reportStatus = "BASIC_READY",
            startedAt = session.startedAt, endedAt = requireNotNull(session.endedAt),
            durationSeconds = Duration.between(session.startedAt, session.endedAt).seconds.coerceAtLeast(0),
            exerciseCount = exerciseReports.size,
            completedSetCount = exerciseReports.sumOf { it.completedSetCount },
            totalRepetitions = exerciseReports.sumOf { it.totalRepetitions },
            totalVolumeKg = exerciseReports.fold(BigDecimal.ZERO) { total, item -> total.add(item.totalVolumeKg) },
            exercises = exerciseReports,
        )
    }

    private fun volume(items: List<WorkoutSet>): BigDecimal = items.fold(BigDecimal.ZERO) { total, set ->
        total.add((set.weightKg ?: BigDecimal.ZERO).multiply(BigDecimal.valueOf((set.repetitions ?: 0).toLong())))
    }

    private fun maxWeight(items: List<WorkoutSet>): BigDecimal? = items.mapNotNull(WorkoutSet::weightKg).maxOrNull()
    private fun dbId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun apiId(value: UUID): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(value)
}
