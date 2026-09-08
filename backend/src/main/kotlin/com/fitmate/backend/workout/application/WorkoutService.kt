package com.fitmate.backend.workout.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.routine.repository.RoutineItemRepository
import com.fitmate.backend.routine.repository.RoutineRepository
import com.fitmate.backend.workout.api.CreateWorkoutSessionRequest
import com.fitmate.backend.workout.api.AddWorkoutSessionExerciseRequest
import com.fitmate.backend.workout.api.PreviousExerciseRecordResponse
import com.fitmate.backend.workout.api.UpsertWorkoutSetRequest
import com.fitmate.backend.workout.api.UpdateWorkoutSessionExerciseRequest
import com.fitmate.backend.workout.api.WorkoutExercisePlanRequest
import com.fitmate.backend.workout.api.WorkoutSessionExerciseResponse
import com.fitmate.backend.workout.api.WorkoutSessionResponse
import com.fitmate.backend.workout.api.WorkoutSessionSummaryResponse
import com.fitmate.backend.workout.api.WorkoutSetResponse
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionExercise
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.domain.WorkoutSet
import com.fitmate.backend.workout.repository.WorkoutSessionExerciseRepository
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import com.fitmate.backend.workout.repository.WorkoutSetRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class WorkoutService(
    private val sessions: WorkoutSessionRepository,
    private val sessionExercises: WorkoutSessionExerciseRepository,
    private val sets: WorkoutSetRepository,
    private val routines: RoutineRepository,
    private val routineItems: RoutineItemRepository,
    private val exercises: ExerciseCatalogRepository,
) {
    @Transactional
    fun create(userId: UUID, request: CreateWorkoutSessionRequest): WorkoutSessionResponse {
        if ((request.routineId == null) == (request.exercises == null)) {
            invalid("routineId와 exercises 중 하나만 보내야 합니다.")
        }
        val now = Instant.now()
        val routine = request.routineId?.let { id ->
            routines.findByIdAndIsActiveTrue(dbId(id))
                ?.takeIf { it.isTemplate || it.ownerUserId == userId }
                ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "루틴을 찾을 수 없습니다.")
        }
        val completedRoutineSessionCount = routine?.let {
            sessions.countByUserIdAndRoutineIdAndStatus(
                userId, requireNotNull(it.id), WorkoutSessionStatus.COMPLETED,
            )
        }
        val workoutDayNumber = completedRoutineSessionCount?.let { Math.toIntExact(it + 1) }
        val splitDayNo = routine?.let { ((workoutDayNumber!! - 1) % it.splitCount) + 1 }
        val plans = if (routine != null) {
            routineItems.findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(routine.id))
                .filter { it.splitDayNo == splitDayNo }
                .map { item ->
                val exercise = requireNotNull(item.exercise)
                Plan(
                    exercise, item.sequenceNo, item.targetSets ?: 1, item.targetWeightKg,
                    item.targetRepsMin,
                )
            }
        } else {
            val requested = requireNotNull(request.exercises)
            validateSequence(requested.map { it.sequenceNo })
            requested.map { item ->
                val exercise = activeExercise(item.exerciseId)
                Plan(exercise, item.sequenceNo, item.targetSets, item.targetWeightKg, item.targetRepetitions)
            }
        }
        if (plans.isEmpty()) invalid("운동을 하나 이상 선택해야 합니다.")
        if (plans.any { it.exercise.sport?.code != SportCode.HEALTH }) invalid("현재 운동 기록은 헬스만 지원합니다.")

        val session = sessions.saveAndFlush(WorkoutSession(
            id = dbId(UUID.randomUUID()), userId = userId, routine = routine,
            routineSplitDayNo = splitDayNo, routineSplitCount = routine?.splitCount,
            routineWorkoutDayNumber = workoutDayNumber,
            status = WorkoutSessionStatus.READY, startedAt = now, clientUpdatedAt = request.clientUpdatedAt,
        ))
        plans.sortedBy { it.sequenceNo }.forEach { plan ->
            val sessionExercise = sessionExercises.saveAndFlush(WorkoutSessionExercise(
                id = dbId(UUID.randomUUID()), workoutSession = session, exercise = plan.exercise,
                displayNameSnapshot = plan.exercise.name, bodyPartSnapshot = plan.exercise.bodyPart,
                sequenceNo = plan.sequenceNo, clientUpdatedAt = request.clientUpdatedAt,
            ))
            sets.saveAllAndFlush((1..plan.targetSets).map { setNo ->
                WorkoutSet(
                    id = dbId(UUID.randomUUID()), sessionExercise = sessionExercise, setNo = setNo,
                    weightKg = plan.weightKg, repetitions = plan.repetitions,
                    isCompleted = false, clientUpdatedAt = request.clientUpdatedAt,
                )
            })
        }
        return toResponse(session, userId)
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID, sessionId: UUID): WorkoutSessionResponse = toResponse(ownedSession(userId, sessionId), userId)

    @Transactional(readOnly = true)
    fun list(
        userId: UUID,
        status: WorkoutSessionStatus?,
        cursor: Int,
        size: Int,
    ): CursorPageResponse<WorkoutSessionSummaryResponse> {
        if (cursor < 0 || size !in 1..100) invalid("cursor는 0 이상, size는 1부터 100까지 가능합니다.")
        val pageable = PageRequest.of(cursor, size)
        val found = if (status == null) {
            sessions.findAllByUserIdOrderByStartedAtDescIdDesc(userId, pageable)
        } else {
            sessions.findAllByUserIdAndStatusOrderByStartedAtDescIdDesc(userId, status, pageable)
        }
        return CursorPageResponse(
            items = found.content.map(::toSummary),
            nextCursor = (cursor + 1).toString().takeIf { found.hasNext() },
            hasNext = found.hasNext(),
        )
    }

    @Transactional
    fun changeState(
        userId: UUID,
        sessionId: UUID,
        version: Long,
        clientUpdatedAt: Instant,
        action: String,
        cancelReason: String? = null,
    ): WorkoutSessionResponse {
        val session = ownedSession(userId, sessionId)
        if (session.version != version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        val target = when (action) {
            "start" -> if (session.status == WorkoutSessionStatus.READY) WorkoutSessionStatus.IN_PROGRESS else null
            "pause" -> if (session.status == WorkoutSessionStatus.IN_PROGRESS) WorkoutSessionStatus.PAUSED else null
            "resume" -> if (session.status == WorkoutSessionStatus.PAUSED) WorkoutSessionStatus.IN_PROGRESS else null
            "complete" -> if (session.status == WorkoutSessionStatus.IN_PROGRESS || session.status == WorkoutSessionStatus.PAUSED) WorkoutSessionStatus.COMPLETED else null
            "cancel" -> if (session.status in setOf(WorkoutSessionStatus.READY, WorkoutSessionStatus.IN_PROGRESS, WorkoutSessionStatus.PAUSED)) WorkoutSessionStatus.CANCELED else null
            else -> null
        } ?: throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        if (target == WorkoutSessionStatus.COMPLETED && !hasCompletedSet(session)) {
            invalid("완료한 세트가 하나 이상 있어야 운동을 완료할 수 있습니다.")
        }
        session.status = target
        session.clientUpdatedAt = clientUpdatedAt
        if (action == "start") session.startedAt = Instant.now()
        if (target == WorkoutSessionStatus.CANCELED) {
            session.cancelReason = cancelReason?.trim()?.ifEmpty { null }
            session.endedAt = endTime(session)
        }
        if (target == WorkoutSessionStatus.COMPLETED) {
            val endedAt = endTime(session)
            session.endedAt = endedAt
            sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id)).forEach {
                if (sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(it.id)).any(WorkoutSet::isCompleted)) {
                    it.completedAt = endedAt
                }
            }
        }
        sessions.saveAndFlush(session)
        return toResponse(session, userId)
    }

    @Transactional
    fun addExercise(
        userId: UUID,
        sessionId: UUID,
        request: AddWorkoutSessionExerciseRequest,
    ): WorkoutSessionExerciseResponse {
        val session = mutableSession(userId, sessionId)
        val current = sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
        if (current.size >= 100) invalid("세션 운동은 최대 100개까지 추가할 수 있습니다.")
        if (request.sequenceNo > current.size + 1) invalid("추가할 운동 순서는 현재 운동 수보다 한 칸 뒤까지만 가능합니다.")
        val exercise = activeExercise(request.exerciseId)
        if (exercise.sport?.code != SportCode.HEALTH) invalid("현재 운동 기록은 헬스만 지원합니다.")
        val added = sessionExercises.saveAndFlush(WorkoutSessionExercise(
            id = dbId(UUID.randomUUID()), workoutSession = session, exercise = exercise,
            displayNameSnapshot = exercise.name, bodyPartSnapshot = exercise.bodyPart,
            sequenceNo = 2000, clientUpdatedAt = request.clientUpdatedAt,
        ))
        sets.saveAllAndFlush((1..request.targetSets).map { setNo ->
            WorkoutSet(
                id = dbId(UUID.randomUUID()), sessionExercise = added, setNo = setNo,
                weightKg = request.targetWeightKg, repetitions = request.targetRepetitions,
                isCompleted = false, clientUpdatedAt = request.clientUpdatedAt,
            )
        })
        reorder(current.toMutableList().also { it.add(request.sequenceNo - 1, added) }, request.clientUpdatedAt)
        return toExerciseResponse(added, userId, session.startedAt)
    }

    @Transactional
    fun updateExercise(
        userId: UUID,
        sessionId: UUID,
        sessionExerciseId: UUID,
        request: UpdateWorkoutSessionExerciseRequest,
    ): WorkoutSessionExerciseResponse {
        val session = mutableSession(userId, sessionId)
        val target = ownedExercise(session, userId, sessionExerciseId)
        if (target.version != request.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        if (request.exerciseId == null && request.sequenceNo == null) invalid("수정할 값을 하나 이상 보내야 합니다.")

        request.exerciseId?.let { exerciseId ->
            val replacement = activeExercise(exerciseId)
            if (replacement.sport?.id != target.exercise?.sport?.id) invalid("대체 운동은 기존 운동과 같은 종목이어야 합니다.")
            target.exercise = replacement
            target.displayNameSnapshot = replacement.name
            target.bodyPartSnapshot = replacement.bodyPart
        }
        target.clientUpdatedAt = request.clientUpdatedAt

        val ordered = sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
            .toMutableList()
        val changedOrder = request.sequenceNo != null && request.sequenceNo != target.sequenceNo
        request.sequenceNo?.takeIf { changedOrder }?.let { sequenceNo ->
            if (sequenceNo > ordered.size) invalid("운동 순서는 현재 운동 수 안에서 선택해야 합니다.")
            ordered.remove(target)
            ordered.add(sequenceNo - 1, target)
        }
        if (changedOrder) {
            reorder(ordered, request.clientUpdatedAt)
        } else {
            sessionExercises.saveAndFlush(target)
        }
        return toExerciseResponse(target, userId, session.startedAt)
    }

    @Transactional
    fun deleteExercise(userId: UUID, sessionId: UUID, sessionExerciseId: UUID, version: Long) {
        val session = mutableSession(userId, sessionId)
        val target = ownedExercise(session, userId, sessionExerciseId)
        if (target.version != version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        val remaining = sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
            .filterNot { it.id == target.id }
        if (remaining.isEmpty()) invalid("세션에는 운동이 하나 이상 필요합니다.")
        sets.deleteAllBySessionExerciseId(requireNotNull(target.id))
        sessionExercises.delete(target)
        sessionExercises.flush()
        reorder(remaining, Instant.now())
    }

    @Transactional
    fun upsertSet(
        userId: UUID,
        sessionId: UUID,
        sessionExerciseId: UUID,
        setNo: Int,
        request: UpsertWorkoutSetRequest,
    ): WorkoutSetResponse {
        if (setNo !in 1..100) invalid("세트 번호는 1부터 100까지 가능합니다.")
        validateSet(request)
        val session = ownedSession(userId, sessionId)
        if (session.status != WorkoutSessionStatus.IN_PROGRESS) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "운동 진행 중에만 세트를 기록할 수 있습니다.")
        }
        val exercise = sessionExercises.findByIdAndWorkoutSessionUserId(dbId(sessionExerciseId), userId)
            ?.takeIf { it.workoutSession?.id == session.id }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "세션 운동을 찾을 수 없습니다.")
        val existing = sets.findBySessionExerciseIdAndSetNo(requireNotNull(exercise.id), setNo)
        if (existing != null && request.version != existing.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        if (existing == null && request.version != null) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        val entity = existing ?: WorkoutSet(
            id = dbId(UUID.randomUUID()), sessionExercise = exercise, setNo = setNo,
        )
        entity.weightKg = request.weightKg
        entity.repetitions = request.repetitions
        entity.durationSeconds = request.durationSeconds
        entity.rpe = request.rpe
        entity.isCompleted = request.completed
        entity.memo = request.memo?.trim()?.ifEmpty { null }
        entity.clientUpdatedAt = request.clientUpdatedAt
        return toSetResponse(sets.saveAndFlush(entity))
    }

    @Transactional(readOnly = true)
    fun previous(userId: UUID, exerciseId: UUID, beforeSessionId: UUID?): PreviousExerciseRecordResponse? {
        val before = beforeSessionId?.let { ownedSession(userId, it).startedAt } ?: Instant.now()
        return previousRecord(userId, dbId(exerciseId), before)
    }

    private fun toResponse(session: WorkoutSession, userId: UUID): WorkoutSessionResponse {
        val workoutExercises = sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
        return WorkoutSessionResponse(
            id = apiId(session.id), sportCode = session.sportCode, routineId = session.routine?.id?.let(::apiId),
            routineSplitDayNo = session.routineSplitDayNo, routineSplitCount = session.routineSplitCount,
            routineWorkoutDayNumber = session.routineWorkoutDayNumber,
            focusBodyParts = workoutExercises.mapNotNull { it.bodyPartSnapshot }.distinct(),
            status = session.status, startedAt = session.startedAt, endedAt = session.endedAt, version = session.version,
            exercises = workoutExercises.map { toExerciseResponse(it, userId, session.startedAt) },
        )
    }

    private fun toExerciseResponse(
        item: WorkoutSessionExercise,
        userId: UUID,
        before: Instant,
    ): WorkoutSessionExerciseResponse {
        val exercise = requireNotNull(item.exercise)
        return WorkoutSessionExerciseResponse(
            id = apiId(item.id), exerciseId = apiId(exercise.id), exerciseName = item.displayNameSnapshot,
            bodyPart = item.bodyPartSnapshot, sequenceNo = item.sequenceNo, version = item.version,
            sets = sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(item.id)).map(::toSetResponse),
            previousRecord = previousRecord(userId, requireNotNull(exercise.id), before),
        )
    }

    private fun previousRecord(userId: UUID, exerciseId: UUID, before: Instant): PreviousExerciseRecordResponse? {
        val previous = sessionExercises.findPreviousCompleted(
            exerciseId, userId, WorkoutSessionStatus.COMPLETED, before, PageRequest.of(0, 1),
        ).firstOrNull() ?: return null
        val session = requireNotNull(previous.workoutSession)
        return PreviousExerciseRecordResponse(
            workoutSessionId = apiId(session.id), performedAt = session.endedAt ?: session.startedAt,
            sets = sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(previous.id))
                .filter { it.isCompleted }.map(::toSetResponse),
        )
    }

    private fun hasCompletedSet(session: WorkoutSession): Boolean =
        sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id)).any { exercise ->
            sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(exercise.id)).any(WorkoutSet::isCompleted)
        }

    private fun toSummary(session: WorkoutSession): WorkoutSessionSummaryResponse {
        val workoutExercises = sessionExercises.findAllByWorkoutSessionIdOrderBySequenceNoAsc(requireNotNull(session.id))
        return WorkoutSessionSummaryResponse(
            id = apiId(session.id), sportCode = session.sportCode,
            routineId = session.routine?.id?.let(::apiId), routineName = session.routine?.name,
            routineSplitDayNo = session.routineSplitDayNo, routineSplitCount = session.routineSplitCount,
            routineWorkoutDayNumber = session.routineWorkoutDayNumber,
            status = session.status, startedAt = session.startedAt, endedAt = session.endedAt,
            exerciseCount = workoutExercises.size,
            completedSetCount = workoutExercises.sumOf { exercise ->
                sets.findAllBySessionExerciseIdOrderBySetNoAsc(requireNotNull(exercise.id)).count { it.isCompleted }
            },
            version = session.version,
        )
    }

    private fun endTime(session: WorkoutSession): Instant =
        Instant.now().let { if (it.isAfter(session.startedAt)) it else session.startedAt.plusMillis(1) }

    private fun ownedSession(userId: UUID, sessionId: UUID): WorkoutSession =
        sessions.findByIdAndUserId(dbId(sessionId), userId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동 세션을 찾을 수 없습니다.")

    private fun mutableSession(userId: UUID, sessionId: UUID): WorkoutSession =
        ownedSession(userId, sessionId).also {
            if (it.status !in setOf(WorkoutSessionStatus.READY, WorkoutSessionStatus.IN_PROGRESS, WorkoutSessionStatus.PAUSED)) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "진행 가능한 세션에서만 운동을 편집할 수 있습니다.")
            }
        }

    private fun ownedExercise(
        session: WorkoutSession,
        userId: UUID,
        sessionExerciseId: UUID,
    ): WorkoutSessionExercise = sessionExercises.findByIdAndWorkoutSessionUserId(dbId(sessionExerciseId), userId)
        ?.takeIf { it.workoutSession?.id == session.id }
        ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "세션 운동을 찾을 수 없습니다.")

    private fun reorder(items: List<WorkoutSessionExercise>, clientUpdatedAt: Instant) {
        items.forEachIndexed { index, item -> item.sequenceNo = 1000 + index }
        sessionExercises.saveAllAndFlush(items)
        items.forEachIndexed { index, item ->
            item.sequenceNo = index + 1
            item.clientUpdatedAt = clientUpdatedAt
        }
        sessionExercises.saveAllAndFlush(items)
    }

    private fun activeExercise(exerciseId: UUID): ExerciseCatalog =
        exercises.findActiveExerciseById(dbId(exerciseId))
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동을 찾을 수 없습니다.")

    private fun validateSequence(sequence: List<Int>) {
        if (sequence.toSet().size != sequence.size) invalid("운동 순서는 중복될 수 없습니다.")
    }

    private fun validateSet(request: UpsertWorkoutSetRequest) {
        if (request.completed && (request.weightKg == null || request.repetitions == null)) {
            invalid("완료 세트에는 무게와 횟수가 필요합니다.")
        }
        if (request.weightKg != null && request.weightKg < BigDecimal.ZERO) invalid("무게는 음수일 수 없습니다.")
        if (request.repetitions != null && request.repetitions !in 1..10000) invalid("횟수는 1부터 10000까지 가능합니다.")
        if (request.durationSeconds != null && request.durationSeconds < 0) invalid("운동 시간은 음수일 수 없습니다.")
        if (request.rpe != null && request.rpe !in 1..10) invalid("RPE는 1부터 10까지 가능합니다.")
    }

    private fun toSetResponse(set: WorkoutSet) = WorkoutSetResponse(
        id = apiId(set.id), setNo = set.setNo, weightKg = set.weightKg, repetitions = set.repetitions,
        durationSeconds = set.durationSeconds, rpe = set.rpe,
        completed = set.isCompleted, memo = set.memo, version = set.version,
    )

    private fun dbId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun apiId(value: UUID?): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(value))
    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)

    private data class Plan(
        val exercise: ExerciseCatalog,
        val sequenceNo: Int,
        val targetSets: Int,
        val weightKg: BigDecimal?,
        val repetitions: Int?,
    )
}
