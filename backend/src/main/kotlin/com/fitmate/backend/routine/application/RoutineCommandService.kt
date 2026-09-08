package com.fitmate.backend.routine.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.routine.api.CreateRoutineRequest
import com.fitmate.backend.routine.api.RoutineItemWriteRequest
import com.fitmate.backend.routine.api.UpdateRoutineRequest
import com.fitmate.backend.routine.application.dto.RoutineDetail
import com.fitmate.backend.routine.domain.DifficultyLevel
import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.domain.RoutineItem
import com.fitmate.backend.routine.domain.RoutineSourceType
import com.fitmate.backend.routine.repository.RoutineItemRepository
import com.fitmate.backend.routine.repository.RoutineRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RoutineCommandService(
    private val routines: RoutineRepository,
    private val routineItems: RoutineItemRepository,
    private val sports: SportRepository,
    private val exercises: ExerciseCatalogRepository,
    private val queryService: RoutineQueryService,
) {
    @Transactional
    fun create(userId: UUID, request: CreateRoutineRequest): RoutineDetail = when (request.sourceType) {
        RoutineSourceType.MANUAL -> createManual(userId, request)
        RoutineSourceType.USER_COPY -> copyTemplate(userId, request)
        RoutineSourceType.SYSTEM_TEMPLATE -> invalid("시스템 템플릿은 사용자가 생성할 수 없습니다.")
    }

    @Transactional
    fun update(userId: UUID, routineId: UUID, request: UpdateRoutineRequest): RoutineDetail {
        val routine = ownedRoutine(userId, routineId)
        if (routine.version != request.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        val previousName = routine.name
        val previousSplitCount = routine.splitCount
        request.name?.let { routine.name = validName(it) }
        if (request.splitCount != null && request.splitCount != routine.splitCount && request.items == null) {
            invalid("분할 수를 바꿀 때는 전체 운동 항목을 함께 보내야 합니다.")
        }
        request.splitCount?.let { routine.splitCount = it }
        request.items?.let {
            validateItems(it, routine.splitCount)
            replaceItems(routine, it)
        }
        if (request.name == null && request.splitCount == null && request.items == null) invalid("수정할 값을 하나 이상 보내야 합니다.")
        if (request.items != null && routine.name == previousName && routine.splitCount == previousSplitCount) {
            val updated = routines.incrementVersion(requireNotNull(routine.id), request.version, Instant.now())
            if (updated != 1) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            return queryService.toDetail(ownedRoutine(userId, routineId))
        }
        routines.saveAndFlush(routine)
        return queryService.toDetail(routine)
    }

    @Transactional
    fun delete(userId: UUID, routineId: UUID, version: Long) {
        val routine = ownedRoutine(userId, routineId)
        if (routine.version != version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        routine.isActive = false
        routines.saveAndFlush(routine)
    }

    private fun createManual(userId: UUID, request: CreateRoutineRequest): RoutineDetail {
        if (request.sourceRoutineId != null) invalid("직접 구성에는 sourceRoutineId를 보낼 수 없습니다.")
        if (request.sportCode != SportCode.HEALTH) invalid("직접 구성 루틴은 헬스 종목만 지원합니다.")
        val requestedItems = request.items ?: invalid("직접 구성에는 운동 항목이 필요합니다.")
        validateItems(requestedItems, request.splitCount)
        val sport = sports.findByCodeAndIsActiveTrue(SportCode.HEALTH)
            ?: invalid("활성 헬스 종목을 찾을 수 없습니다.")
        val routine = routines.saveAndFlush(Routine(
            id = dbId(UUID.randomUUID()), ownerUserId = userId, sport = sport, name = validName(request.name),
            sourceType = RoutineSourceType.MANUAL, difficulty = DifficultyLevel.BEGINNER,
            splitCount = request.splitCount,
            isTemplate = false, isActive = true,
        ))
        saveItems(routine, requestedItems)
        return queryService.toDetail(routine)
    }

    private fun copyTemplate(userId: UUID, request: CreateRoutineRequest): RoutineDetail {
        if (request.sportCode != null || request.items != null) invalid("템플릿 복사에는 sportCode나 items를 보낼 수 없습니다.")
        val sourceId = request.sourceRoutineId ?: invalid("템플릿 복사에는 sourceRoutineId가 필요합니다.")
        val source = routines.findByIdAndIsActiveTrue(dbId(sourceId))
            ?.takeIf { it.isTemplate }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "복사할 템플릿을 찾을 수 없습니다.")
        val copy = routines.saveAndFlush(Routine(
            id = dbId(UUID.randomUUID()), ownerUserId = userId, sport = source.sport, name = validName(request.name),
            sourceType = RoutineSourceType.USER_COPY, difficulty = source.difficulty,
            splitCount = source.splitCount,
            isTemplate = false, isActive = true,
        ))
        val sourceItems = routineItems.findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(source.id))
        routineItems.saveAllAndFlush(sourceItems.map { item ->
            RoutineItem(
                id = dbId(UUID.randomUUID()), routine = copy, exercise = item.exercise, sequenceNo = item.sequenceNo,
                splitDayNo = item.splitDayNo,
                targetSets = item.targetSets, targetRepsMin = item.targetRepsMin, targetRepsMax = item.targetRepsMax,
                targetWeightKg = item.targetWeightKg, targetRpe = item.targetRpe, restSeconds = item.restSeconds,
            )
        })
        return queryService.toDetail(copy)
    }

    private fun replaceItems(routine: Routine, requests: List<RoutineItemWriteRequest>) {
        val existing = routineItems.findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(routine.id))
        routineItems.deleteAllInBatch(existing)
        routineItems.flush()
        saveItems(routine, requests)
    }

    private fun saveItems(routine: Routine, requests: List<RoutineItemWriteRequest>) {
        val resolved = requests.map { it to activeExercise(it) }
        val sportId = requireNotNull(routine.sport?.id)
        if (resolved.any { (_, exercise) -> exercise.sport?.id != sportId }) invalid("루틴과 운동 항목의 종목이 일치해야 합니다.")
        routineItems.saveAllAndFlush(resolved.map { (request, exercise) -> request.toEntity(routine, exercise) })
    }

    private fun activeExercise(request: RoutineItemWriteRequest): ExerciseCatalog =
        exercises.findActiveExerciseById(dbId(request.exerciseId))
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동을 찾을 수 없습니다.")

    private fun ownedRoutine(userId: UUID, routineId: UUID): Routine {
        val routine = routines.findByIdAndIsActiveTrue(dbId(routineId))
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "루틴을 찾을 수 없습니다.")
        if (routine.isTemplate || routine.ownerUserId != userId) {
            throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "루틴을 찾을 수 없습니다.")
        }
        return routine
    }

    private fun validateItems(items: List<RoutineItemWriteRequest>, splitCount: Int) {
        if (items.isEmpty()) invalid("운동 항목을 하나 이상 보내야 합니다.")
        if (items.map { it.sequenceNo }.toSet().size != items.size) invalid("운동 순서는 중복될 수 없습니다.")
        if (items.any { it.splitDayNo !in 1..splitCount }) invalid("운동의 분할 번호는 전체 분할 수 안에 있어야 합니다.")
        if (items.map { it.splitDayNo }.toSet() != (1..splitCount).toSet()) invalid("모든 분할에 운동이 하나 이상 필요합니다.")
        if (items.any { it.targetRepsMin != null && it.targetRepsMax != null && it.targetRepsMin > it.targetRepsMax }) {
            invalid("최소 반복 수는 최대 반복 수보다 클 수 없습니다.")
        }
    }

    private fun RoutineItemWriteRequest.toEntity(routine: Routine, exercise: ExerciseCatalog) = RoutineItem(
        id = dbId(UUID.randomUUID()), routine = routine, exercise = exercise, sequenceNo = sequenceNo,
        splitDayNo = splitDayNo,
        targetSets = targetSets, targetRepsMin = targetRepsMin, targetRepsMax = targetRepsMax,
        targetWeightKg = targetWeightKg, targetRpe = targetRpe, restSeconds = restSeconds,
    )

    private fun validName(value: String): String = value.trim().takeIf { it.isNotEmpty() }
        ?: invalid("루틴 이름은 공백일 수 없습니다.")

    private fun dbId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
