package com.fitmate.backend.recommendation.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.inbody.domain.InbodyRecord
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.recommendation.api.CreateWorkoutRecommendationRequest
import com.fitmate.backend.recommendation.api.WorkoutRecommendationResponse
import com.fitmate.backend.recommendation.infrastructure.WorkoutRecommendationNarrator
import com.fitmate.backend.routine.application.RoutineQueryService
import com.fitmate.backend.routine.application.dto.RoutineItemResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class WorkoutRecommendationService(
    private val routines: RoutineQueryService,
    private val exercises: ExerciseCatalogRepository,
    private val records: InbodyRecordRepository,
    private val userGoals: UserGoalRepository,
    private val narrator: WorkoutRecommendationNarrator,
) {
    @Transactional(readOnly = true)
    fun recommend(userId: UUID, request: CreateWorkoutRecommendationRequest): WorkoutRecommendationResponse {
        val base = routines.getRecommended(userId, com.fitmate.backend.fitness.domain.SportCode.HEALTH)
        val goal = userGoals.findAllByIdUserId(userId).singleOrNull { it.isPrimary }?.id?.goalCode ?: base.targetGoalCode
        val inbody = request.inbodyRecordId?.let { recordId ->
            records.findByIdAndUserId(recordId, userId)
                ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "선택한 인바디 기록을 찾을 수 없습니다.")
        }
        val allowed = allowedExerciseIds(request.equipmentTypes)
        val items = base.items.filter { allowed == null || it.exerciseId in allowed }
        if (items.isEmpty()) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "선택한 기구로 구성할 수 있는 운동이 없습니다.")

        val splitCount = minOf(base.splitCount, request.daysPerWeek)
        val reducedVolume = request.daysPerWeek <= 2 || base.targetLevel == SkillLevel.BEGINNER
        val adjustedItems = items.map { item -> item.copy(
            splitDayNo = ((item.splitDayNo - 1) % splitCount) + 1,
            targetSets = if (reducedVolume) minOf(item.targetSets ?: 2, 2) else item.targetSets,
            targetRpe = if (reducedVolume) minOf(item.targetRpe ?: 6, 6) else item.targetRpe,
        ) }
        val cardioMinutes = cardioMinutes(goal, inbody, request.sessionMinutes)
        val reasons = reasons(goal, base.targetLevel, request, inbody, reducedVolume, cardioMinutes, base.splitCount, splitCount)
        val summary = narrator.narrate(
            name = base.name,
            reasons = reasons,
            cardioMinutes = cardioMinutes,
            inbody = inbody,
        )
        return WorkoutRecommendationResponse(
            name = if (reducedVolume) "${base.name} · 적응형" else base.name,
            daysPerWeek = request.daysPerWeek,
            sessionMinutes = request.sessionMinutes,
            splitCount = splitCount,
            cardioMinutes = cardioMinutes,
            items = adjustedItems,
            reasons = reasons,
            inbodyUsed = inbody != null,
            inbodyMeasuredDate = inbody?.measuredDate,
            aiSummary = summary,
        )
    }

    private fun allowedExerciseIds(equipment: Set<EquipmentType>): Set<UUID>? {
        if (equipment.isEmpty()) return null
        val all = exercises.findActiveExercises(null, null, null, org.springframework.data.domain.PageRequest.of(0, 100))
        return all.filter { it.equipmentType in equipment || it.equipmentType == EquipmentType.BODYWEIGHT }
            .map { UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(it.id)) }.toSet()
    }

    private fun cardioMinutes(goal: GoalCode?, inbody: InbodyRecord?, sessionMinutes: Int): Int {
        val bodyFatNeedsCardio = inbody?.bodyFatPercentage?.let { it >= BigDecimal("30") } == true
        val bmiNeedsCardio = inbody?.optionalValue("BMI")?.let { it >= BigDecimal("25") } == true
        if (goal != GoalCode.DIET && !bodyFatNeedsCardio && !bmiNeedsCardio) return 0
        return minOf(20, sessionMinutes / 3)
    }

    private fun reasons(
        goal: GoalCode?, level: SkillLevel?, request: CreateWorkoutRecommendationRequest,
        inbody: InbodyRecord?, reducedVolume: Boolean, cardioMinutes: Int, baseSplitCount: Int, splitCount: Int,
    ): List<String> = buildList {
        add("${request.daysPerWeek}일, 회당 ${request.sessionMinutes}분으로 운동 계획을 구성했어요.")
        if (reducedVolume) add("처음 시작하거나 주 2회 이하로 운동하는 조건이라 각 운동을 최대 2세트로 시작해요.")
        else add("현재 숙련도에 맞는 기본 루틴의 세트와 반복 범위를 유지했어요.")
        if (splitCount < baseSplitCount) add("운동 가능 일수에 맞춰 ${baseSplitCount}분할 구성을 ${splitCount}분할로 묶었어요.")
        if (request.equipmentTypes.isNotEmpty()) add("선택한 기구와 맨몸으로 할 수 있는 운동만 포함했어요.")
        if (cardioMinutes > 0) add("목표와 선택한 체성분 기록을 함께 고려해 운동 뒤 ${cardioMinutes}분의 편한 강도 유산소를 제안해요.")
        if (inbody != null) {
            add("${inbody.measuredDate}에 측정한 체성분 기록을 참고했어요. 수치는 현재 상태를 단정하지 않아요.")
            balanceNote(inbody)?.let(::add)
        } else add("인바디를 선택하지 않아 목표와 운동 경험을 중심으로 추천했어요.")
    }

    private fun balanceNote(record: InbodyRecord): String? {
        // Recommendation must not diagnose asymmetry. It only prompts the user to check perceived difficulty or pain.
        val bodyPart = listOf(
            "팔" to (record.optionalValue("LEFT_ARM_MUSCLE_KG") to record.optionalValue("RIGHT_ARM_MUSCLE_KG")),
            "다리" to (record.optionalValue("LEFT_LEG_MUSCLE_KG") to record.optionalValue("RIGHT_LEG_MUSCLE_KG")),
        ).firstOrNull { (_, pair) -> pair.relativeDifference() >= BigDecimal("0.10") }?.first
        return bodyPart?.let { "좌우 $it 측정값 차이가 보여요. 실제 운동 중 한쪽의 난이도나 통증을 확인해 주세요." }
    }

    private fun InbodyRecord.optionalValue(code: String): BigDecimal? = optionalValuesJson
        ?.let { Regex("\\\"$code\\\"\\s*:\\s*([0-9.]+)").find(it)?.groupValues?.get(1)?.toBigDecimalOrNull() }

    private fun Pair<BigDecimal?, BigDecimal?>.relativeDifference(): BigDecimal {
        val (left, right) = this
        if (left == null || right == null || left == BigDecimal.ZERO || right == BigDecimal.ZERO) return BigDecimal.ZERO
        return left.subtract(right).abs().divide(left.max(right), 2, java.math.RoundingMode.HALF_UP)
    }
}
