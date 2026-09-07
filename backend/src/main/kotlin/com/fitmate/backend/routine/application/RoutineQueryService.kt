package com.fitmate.backend.routine.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.routine.application.dto.RoutineDetail
import com.fitmate.backend.routine.application.dto.RoutineItemResponse
import com.fitmate.backend.routine.application.dto.RoutineSummary
import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.repository.RoutineItemRepository
import com.fitmate.backend.routine.repository.RoutineRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RoutineQueryService(
    private val routineRepository: RoutineRepository,
    private val routineItemRepository: RoutineItemRepository,
    private val userGoalRepository: UserGoalRepository,
    private val userSportRepository: UserSportRepository,
    private val sportRepository: SportRepository,
) {
    @Transactional(readOnly = true)
    fun getRecommended(userId: UUID, sportCode: SportCode): RoutineDetail {
        if (sportCode != SportCode.HEALTH) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "추천 루틴은 헬스 종목만 지원합니다.")
        }
        val sport = sportRepository.findByCodeAndIsActiveTrue(sportCode)
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "활성 운동 종목만 추천받을 수 있습니다.")
        val sportId = requireNotNull(sport.id)
        val primaryGoal = userGoalRepository.findAllByIdUserId(userId).singleOrNull { it.isPrimary }?.id?.goalCode
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "주 운동 목표를 먼저 설정해야 합니다.")
        val skillLevel = userSportRepository.findAllByIdUserId(userId)
            .firstOrNull { it.id.sportId == sportId }?.skillLevel
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "선택한 운동의 숙련도를 먼저 설정해야 합니다.")
        val templates = activeTemplates(sportCode)
        val recommended = templates.firstOrNull { it.targetGoalCode == primaryGoal && it.targetLevel == skillLevel }
            ?: templates.firstOrNull { it.targetGoalCode == primaryGoal && it.targetLevel == null }
            ?: templates.firstOrNull { it.targetGoalCode == GoalCode.FITNESS && it.targetLevel == SkillLevel.BEGINNER }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "추천할 수 있는 기본 루틴이 없습니다.")
        return toDetail(recommended)
    }

    @Transactional(readOnly = true)
    fun getTemplates(): List<RoutineSummary> = routineRepository
        .findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()
        .map(::toSummary)

    @Transactional(readOnly = true)
    fun getMine(userId: UUID): List<RoutineSummary> = routineRepository
        .findAllByOwnerUserIdAndIsTemplateFalseAndIsActiveTrueOrderByUpdatedAtDesc(userId)
        .map(::toSummary)

    @Transactional(readOnly = true)
    fun getDetail(userId: UUID, routineId: UUID): RoutineDetail {
        val routine = routineRepository.findByIdAndIsActiveTrue(UuidBinaryConverter.toMysqlOrderedUuid(routineId))
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "루틴을 찾을 수 없습니다.")
        if (!routine.isTemplate && routine.ownerUserId != userId) {
            throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "루틴을 찾을 수 없습니다.")
        }
        return toDetail(routine)
    }

    private fun activeTemplates(sportCode: SportCode): List<Routine> = routineRepository
        .findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()
        .filter { it.sport?.code == sportCode }

    private fun toSummary(routine: Routine): RoutineSummary = RoutineSummary(
        id = toApiId(routine.id),
        ownerUserId = routine.ownerUserId,
        sportCode = requireNotNull(routine.sport).code,
        name = routine.name,
        sourceType = routine.sourceType,
        targetGoalCode = routine.targetGoalCode,
        targetLevel = routine.targetLevel,
        difficulty = routine.difficulty,
        template = routine.isTemplate,
        version = routine.version,
    )

    private fun toDetail(routine: Routine): RoutineDetail {
        val summary = toSummary(routine)
        val items = routineItemRepository.findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(routine.id)).map { item ->
            val exercise = requireNotNull(item.exercise)
            RoutineItemResponse(
                id = toApiId(item.id),
                exerciseId = toApiId(exercise.id),
                exerciseName = exercise.name,
                bodyPart = exercise.bodyPart,
                sequenceNo = item.sequenceNo,
                targetSets = item.targetSets,
                targetRepsMin = item.targetRepsMin,
                targetRepsMax = item.targetRepsMax,
                targetWeightKg = item.targetWeightKg,
                targetRpe = item.targetRpe,
                restSeconds = item.restSeconds,
                version = item.version,
            )
        }
        return RoutineDetail(
            id = summary.id,
            ownerUserId = summary.ownerUserId,
            sportCode = summary.sportCode,
            name = summary.name,
            sourceType = summary.sourceType,
            targetGoalCode = summary.targetGoalCode,
            targetLevel = summary.targetLevel,
            difficulty = summary.difficulty,
            template = summary.template,
            version = summary.version,
            items = items,
        )
    }

    private fun toApiId(id: UUID?): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(
        id ?: throw IllegalStateException("reference id must not be null"),
    )
}
