package com.fitmate.backend.recommendation.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.recommendation.api.*
import org.springframework.stereotype.Component
import java.time.LocalDate

data class WorkoutPlanContext(
    val conditions: CreateWorkoutPlanRequest,
    val goal: GoalCode,
    val skillLevel: SkillLevel,
    val catalog: List<PlanCatalogExercise>,
    val measuredDate: LocalDate? = null,
    val bodyFatPercentage: java.math.BigDecimal? = null,
    val skeletalMuscleMassKg: java.math.BigDecimal? = null,
    /** Minimal, server-derived history. It never contains routes, raw samples, IDs, times, or memo text. */
    val recentRunning: List<RecentRunningRecommendationContext> = emptyList(),
)

data class RecentRunningRecommendationContext(
    val goalType: String,
    val goalAchieved: Boolean?,
    val distanceMeters: Int,
    val activeDurationSeconds: Int,
    val averagePaceSecondsPerKm: Int?,
    val rpe: Int?,
    val conditionScore: Int?,
    val samsungSummary: SamsungRecommendationContext? = null,
)

data class SamsungRecommendationContext(
    val activeDurationSeconds: Long,
    val meanHeartRate: java.math.BigDecimal?,
    val peakHeartRate: java.math.BigDecimal?,
    val heartRateCoveragePercent: Int?,
    val heartRateLongGapCount: Int?,
    val paceSources: List<String>,
    val analysisVersion: String?,
    val limitations: List<String>,
)

@Component
class WorkoutPlanRules {
    fun eligible(context: WorkoutPlanContext): List<PlanCatalogExercise> = context.catalog.filter {
        it.id !in context.conditions.excludedExerciseIds &&
            (context.conditions.equipmentTypes.isEmpty() || it.equipmentType == EquipmentType.BODYWEIGHT ||
                it.equipmentType in context.conditions.equipmentTypes)
    }

    fun checkConditions(context: WorkoutPlanContext) {
        val request = context.conditions
        if (request.daysPerWeek !in 1..7 || request.sessionMinutes !in 20..180 ||
            request.preferredExerciseIds.size > 100 || request.excludedExerciseIds.size > 100) invalid("횟수·시간·운동 선택 범위를 확인해 주세요.")
        if (request.preferredExerciseIds.intersect(request.excludedExerciseIds).isNotEmpty()) invalid("선호 운동과 제외 운동이 겹칩니다.")
        val activeIds = context.catalog.map { it.id }.toSet()
        if (!(request.preferredExerciseIds + request.excludedExerciseIds).all { it in activeIds }) invalid("선택한 운동 중 현재 이용할 수 없는 운동이 있습니다.")
        if (!request.preferredExerciseIds.all { id -> eligible(context).any { it.id == id } }) invalid("선호 운동에 필요한 기구를 선택하거나 선호 조건을 수정해 주세요.")
    }

    fun beginner(context: WorkoutPlanContext) = context.skillLevel in setOf(SkillLevel.BEGINNER, SkillLevel.UNDER_6_MONTHS)
    fun cardioSeconds(context: WorkoutPlanContext): Int =
        if (context.goal == GoalCode.DIET) minOf(600, context.conditions.sessionMinutes * 60 / 6) else 0

    fun duration(exercise: PlanExercise): Int = exercise.sets * exercise.reps * 4 +
        (exercise.sets - 1) * exercise.restSeconds + 60 // Four seconds per repetition, one minute to change/setup equipment.

    fun validate(context: WorkoutPlanContext, proposal: ProposedWorkoutPlan): List<WorkoutPlanDay> {
        checkConditions(context)
        val allowed = eligible(context).associateBy { it.id }
        if (proposal.days.size != context.conditions.daysPerWeek || proposal.days.map { it.day }.sorted() != (1..context.conditions.daysPerWeek).toList())
            invalid("주 운동 횟수에 맞는 날짜별 구성이 필요합니다.")
        val days = proposal.days.sortedBy { it.day }.map { day ->
            if (day.exercises.size !in 2..6 || day.exercises.map { it.exerciseId }.distinct().size != day.exercises.size)
                invalid("하루 운동은 중복 없이 2~6개로 구성해 주세요.")
            val items = day.exercises.map { item ->
                val exercise = allowed[item.exerciseId] ?: invalid("허용되지 않은 운동이 포함되어 있습니다.")
                if (item.sets !in 1..(if (beginner(context)) 3 else 4) || item.reps !in 6..15 || item.restSeconds !in 45..150)
                    invalid("세트·반복·휴식이 추천 허용 범위를 벗어났습니다.")
                WorkoutPlanItem(exercise.id, exercise.name, exercise.bodyPart, exercise.equipmentType, item.sets, item.reps, item.restSeconds, duration(item))
            }
            val groups = items.groupBy { group(it.bodyPart) }
            if (groups.values.any { group -> group.sumOf { it.sets } > 8 } || items.sumOf { it.sets } > if (beginner(context)) 12 else 20)
                invalid("특정 부위에 운동이 몰려 있습니다. 기구나 제외 운동 조건을 조정해 주세요.")
            val seconds = 300 + cardioSeconds(context) + items.sumOf { it.estimatedSeconds }
            if (seconds > context.conditions.sessionMinutes * 60) invalid("준비운동·휴식·유산소를 포함하면 설정한 시간을 초과합니다. 시간을 늘리거나 조건을 조정해 주세요.")
            WorkoutPlanDay(day.day, items, 300, cardioSeconds(context), seconds)
        }
        if (days.maxOf { it.estimatedSeconds } > days.minOf { it.estimatedSeconds } * 1.5)
            invalid("날짜별 운동량 차이가 너무 큽니다. 균형 있는 구성이 필요합니다.")
        val weekly = days.flatMap { it.exercises }.groupBy { group(it.bodyPart) }.mapValues { (_, items) -> items.sumOf { it.sets } }
        val major = listOf("PUSH", "PULL", "LEGS").map { weekly[it] ?: 0 }
        if (major.any { it == 0 }) invalid("주간 구성에 밀기·당기기·하체 운동이 모두 필요합니다. 기구나 제외 조건을 조정해 주세요.")
        if (major.max() > major.min() * 3) invalid("주간 운동량이 특정 부위에 과도하게 몰려 있습니다. 분할별 세트 수를 조정해 주세요.")
        return days
    }

    /** Template is a preference, not a mandatory split. The whole proposed week is always validated. */
    fun suggestedDayGroups(context: WorkoutPlanContext): List<Set<String>> {
        val pools = eligible(context).groupBy { group(it.bodyPart) }
        val full = setOf("PUSH", "PULL", "LEGS", "CORE")
        val cycle = when {
            beginner(context) || context.conditions.daysPerWeek <= 3 -> listOf(full)
            context.conditions.daysPerWeek >= 6 && listOf("PUSH", "PULL", "LEGS").all { pools[it].orEmpty().size >= 2 } ->
                listOf(setOf("PUSH", "CORE"), setOf("PULL", "CORE"), setOf("LEGS", "CORE"))
            pools["PUSH"].orEmpty().isNotEmpty() && pools["PULL"].orEmpty().isNotEmpty() &&
                pools["LEGS"].orEmpty().isNotEmpty() && pools["LEGS"].orEmpty().size + pools["CORE"].orEmpty().size >= 2 ->
                listOf(setOf("PUSH", "PULL"), setOf("LEGS", "CORE"))
            else -> listOf(full)
        }
        return (0 until context.conditions.daysPerWeek).map { cycle[it % cycle.size] }
    }

    fun fallback(context: WorkoutPlanContext): ProposedWorkoutPlan {
        checkConditions(context)
        val ordered = eligible(context).sortedWith(compareBy<PlanCatalogExercise> { it.id !in context.conditions.preferredExerciseIds }
            .thenBy { if (it.bodyPart in setOf(BodyPart.CHEST, BodyPart.BACK, BodyPart.LEGS, BodyPart.QUADRICEPS, BodyPart.HAMSTRINGS, BodyPart.GLUTES)) 0 else 1 }
            .thenBy { if (beginner(context) && it.equipmentType in setOf(EquipmentType.MACHINE, EquipmentType.CABLE)) 0 else 1 }.thenBy { it.id })
        val grouped = ordered.groupBy { group(it.bodyPart) }
        if (!listOf("PUSH", "PULL", "LEGS").all { grouped[it].orEmpty().isNotEmpty() })
            invalid("현재 조건으로 균형 있는 추천을 만들 수 없습니다. 사용 기구를 늘리거나 제외 운동을 줄여 주세요.")
        fun build(schedule: List<Set<String>>): ProposedWorkoutPlan = ProposedWorkoutPlan(schedule.mapIndexed { index, focus ->
            val day = index + 1
            val primary = focus.filter { it != "CORE" }.mapNotNull { key -> grouped[key]?.let { pool ->
                pool.firstOrNull { it.id in context.conditions.preferredExerciseIds } ?: pool[index % pool.size]
            } }
            val target = if (focus.containsAll(setOf("PUSH", "PULL", "LEGS"))) 4 else if (focus.containsAll(setOf("PUSH", "PULL"))) 4 else 3
            val choices = (primary + ordered.filter { group(it.bodyPart) in focus }).distinctBy { it.id }.take(target)
            val items = mutableListOf<PlanExercise>()
            val budget = context.conditions.sessionMinutes * 60 - 300 - cardioSeconds(context)
            val sets = if (beginner(context) || context.conditions.daysPerWeek >= 5 || !focus.containsAll(setOf("PUSH", "PULL", "LEGS"))) 2 else 3
            for (exercise in choices) {
                var candidate = PlanExercise(exercise.id, sets, 10, if (beginner(context)) 60 else 90)
                while (candidate.sets > 1 && items.sumOf(::duration) + duration(candidate) > budget) candidate = candidate.copy(sets = candidate.sets - 1)
                if (items.sumOf(::duration) + duration(candidate) <= budget && items.sumOf { it.sets } + candidate.sets <= if (beginner(context)) 12 else 20) items.add(candidate)
            }
            PlanDay(day, items)
        })
        val suggested = build(suggestedDayGroups(context))
        try { validate(context, suggested); return suggested } catch (_: ApiException) {
            // Limited catalogs or short sessions can make a split infeasible; use only a validated full-body alternative.
            return build(List(context.conditions.daysPerWeek) { setOf("PUSH", "PULL", "LEGS", "CORE") }).also { validate(context, it) }
        }
    }

    fun result(context: WorkoutPlanContext, proposal: ProposedWorkoutPlan, source: String, fallbackReason: String? = null): WorkoutPlanResult {
        val days = validate(context, proposal)
        val used = days.flatMap { it.exercises }.map { it.exerciseId }.toSet()
        return WorkoutPlanResult(
            name = "주 ${context.conditions.daysPerWeek}일 맞춤 운동", source = source, goal = context.goal, skillLevel = context.skillLevel,
            conditions = context.conditions, inbodyMeasuredDate = context.measuredDate,
            reasons = listOf("프로필 목표·숙련도와 사용 가능한 기구를 반영했습니다.", "각 회차를 준비운동·휴식·이동·유산소 포함 ${context.conditions.sessionMinutes}분 이내로 검증했습니다.",
                "주간 전체에 밀기·당기기·하체를 포함하고 일별·주간 세트 편중을 검증했습니다.",
                "일차별 구성: " + days.joinToString(" / ") { day -> "${day.day}일차 ${day.exercises.map { group(it.bodyPart) }.distinct().joinToString("·") { when (it) { "PUSH" -> "밀기"; "PULL" -> "당기기"; "LEGS" -> "하체"; else -> "코어" } }}" }) + listOfNotNull(fallbackReason),
            notices = buildList {
                add("예상 시간은 반복당 4초·운동 전환 1분 기준이며 실제 속도에 따라 달라집니다. 남는 시간을 억지로 채우지 않습니다.")
                add("준비운동 5분: 가벼운 움직임과 해당 부위의 편안한 가동 범위 연습을 하세요.")
                if (cardioSeconds(context) > 0) add("유산소는 대화 가능한 편안한 강도로 진행하세요.")
                add("중량은 추천하지 않습니다. 통증이 있으면 중단하고 회복 상태에 맞춰 날짜 사이 휴식을 확보하세요.")
                if (context.measuredDate == null) add("인바디 미사용: 체성분 정보 없이 프로필과 운동 조건으로 구성했습니다.")
                else if (source == "AI") add("선택한 ${context.measuredDate} 인바디의 체지방률·골격근량만 참고합니다. 측정 이후 상태는 다를 수 있으며 질환·적정 중량·교정 운동을 판단하지 않습니다.")
                else add("선택 인바디 측정일: ${context.measuredDate}. 기본 추천은 체성분 수치로 운동을 바꾸지 않고 프로필·횟수·시간·기구 조건을 사용합니다.")
                add("현재 추천은 반복 횟수로 기록하는 운동을 지원합니다. 시간 유지형 플랭크는 제외됩니다.")
                if (source == "AI" && context.recentRunning.isNotEmpty()) {
                    add("최근 러닝은 목표·달성 여부·거리·활동 시간·평균 페이스·RPE·컨디션과 동의한 Samsung 요약만 참고했습니다. 심박수만으로 중량이나 강도를 정하지 않습니다.")
                }
                if (context.conditions.preferredExerciseIds.any { it !in used }) add("일부 선호 운동은 시간·균형 조건으로 포함되지 않았습니다.")
            }, days = days,
        )
    }

    fun proposal(result: WorkoutPlanResult) = ProposedWorkoutPlan(result.days.map { day -> PlanDay(day.day, day.exercises.map { PlanExercise(it.exerciseId, it.sets, it.reps, it.restSeconds) }) })
    fun group(part: BodyPart): String = when (part) {
        BodyPart.CHEST, BodyPart.SHOULDERS, BodyPart.TRICEPS -> "PUSH"
        BodyPart.BACK, BodyPart.BICEPS -> "PULL"
        BodyPart.LEGS, BodyPart.HAMSTRINGS, BodyPart.GLUTES, BodyPart.QUADRICEPS -> "LEGS"
        BodyPart.CORE -> "CORE"
    }
    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
