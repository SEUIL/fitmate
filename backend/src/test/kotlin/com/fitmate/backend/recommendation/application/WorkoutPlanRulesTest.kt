package com.fitmate.backend.recommendation.application

import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.domain.*
import com.fitmate.backend.member.domain.*
import com.fitmate.backend.recommendation.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class WorkoutPlanRulesTest {
    private val rules = WorkoutPlanRules()
    private val catalog = listOf(BodyPart.CHEST, BodyPart.BACK, BodyPart.LEGS, BodyPart.CORE, BodyPart.SHOULDERS).mapIndexed { i, part ->
        PlanCatalogExercise(UUID(0, i + 1L), "합성 운동 $i", part, if (i == 4) EquipmentType.DUMBBELL else EquipmentType.MACHINE)
    }
    private fun context(days: Int = 3, minutes: Int = 45, skill: SkillLevel = SkillLevel.BEGINNER) = WorkoutPlanContext(
        CreateWorkoutPlanRequest(UUID.randomUUID(), days, minutes), GoalCode.FITNESS, skill, catalog)

    @Test fun `fallback respects all days time levels and cardio budgets`() {
        for (days in 1..7) for (minutes in listOf(20, 30, 45, 60, 180)) for (skill in SkillLevel.entries) for (goal in listOf(GoalCode.FITNESS, GoalCode.DIET)) {
            val ctx = context(days, minutes, skill).copy(goal = goal)
            val result = rules.validate(ctx, rules.fallback(ctx))
            assertEquals((1..days).toList(), result.map { it.day })
            assertTrue(result.all { it.estimatedSeconds <= minutes * 60 })
            assertTrue(result.all { it.exercises.map { ex -> ex.exerciseId }.distinct().size == it.exercises.size })
            assertTrue(result.all { it.estimatedSeconds == it.warmupSeconds + it.cardioSeconds + it.exercises.sumOf { ex -> ex.estimatedSeconds } })
        }
    }
    @Test fun `unknown ID excluded exercise and disallowed equipment are rejected`() {
        val ctx = context()
        val valid = rules.fallback(ctx)
        fun substitute(id: UUID) = valid.copy(days = valid.days.map { it.copy(exercises = it.exercises.mapIndexed { i, ex -> if (i == 0) ex.copy(exerciseId = id) else ex }) })
        assertThrows(ApiException::class.java) { rules.validate(ctx, substitute(UUID.randomUUID())) }
        val firstId = valid.days.first().exercises.first().exerciseId
        assertThrows(ApiException::class.java) { rules.validate(ctx.copy(conditions = ctx.conditions.copy(excludedExerciseIds = setOf(firstId))), valid) }
        assertThrows(ApiException::class.java) { rules.validate(ctx.copy(conditions = ctx.conditions.copy(equipmentTypes = setOf(EquipmentType.BODYWEIGHT))), valid) }
    }
    @Test fun `infeasible fallback asks for conditions instead of inventing exercises`() {
        val ctx = context().copy(catalog = catalog.take(2))
        assertThrows(ApiException::class.java) { rules.fallback(ctx) }
    }
    @Test fun `sets repetitions and rest are bounded before calculating duration`() {
        val ctx = context()
        val valid = rules.fallback(ctx)
        val old = valid.days.first().exercises.first()
        for (invalid in listOf(old.copy(sets = Int.MAX_VALUE), old.copy(sets = 0), old.copy(reps = 100), old.copy(restSeconds = 0))) {
            assertThrows(ApiException::class.java) { rules.validate(ctx, valid.copy(days = valid.days.map { it.copy(exercises = listOf(invalid) + it.exercises.drop(1)) })) }
        }
    }
    @Test fun `duplicate exercises days and concentrated load fail validation`() {
        val ctx = context()
        val valid = rules.fallback(ctx)
        assertThrows(ApiException::class.java) { rules.validate(ctx, valid.copy(days = valid.days.map { it.copy(day = 1) })) }
        assertThrows(ApiException::class.java) { rules.validate(ctx, valid.copy(days = valid.days.map { it.copy(exercises = List(3) { _ -> it.exercises.first() }) })) }
        val pushCatalog = (1..3).map { PlanCatalogExercise(UUID(0, it.toLong()), "합성", BodyPart.CHEST, EquipmentType.MACHINE) }
        assertThrows(ApiException::class.java) { rules.validate(ctx.copy(catalog = pushCatalog), ProposedWorkoutPlan((1..3).map { PlanDay(it, pushCatalog.map { ex -> PlanExercise(ex.id, 3, 10, 60) }) })) }
    }
    @Test fun `oversized day including warmup and cardio is rejected`() {
        val ctx = context(minutes = 20, skill = SkillLevel.OVER_THREE_YEARS).copy(goal = GoalCode.DIET)
        val plan = ProposedWorkoutPlan((1..3).map { PlanDay(it, catalog.take(4).map { ex -> PlanExercise(ex.id, 4, 15, 150) }) })
        assertThrows(ApiException::class.java) { rules.validate(ctx, plan) }
    }
    @Test fun `preferences are optional but exclusions and contradictory preferences are hard constraints`() {
        val ctx = context()
        val preferred = catalog.last().id
        val changed = ctx.copy(conditions = ctx.conditions.copy(preferredExerciseIds = setOf(preferred)))
        assertTrue(rules.fallback(changed).days.first().exercises.any { it.exerciseId == preferred })
        assertThrows(ApiException::class.java) { rules.fallback(changed.copy(conditions = changed.conditions.copy(excludedExerciseIds = setOf(preferred)))) }
        assertThrows(ApiException::class.java) { rules.fallback(changed.copy(conditions = changed.conditions.copy(equipmentTypes = setOf(EquipmentType.MACHINE)))) }
    }
    @Test fun `optional inbody preserves measured date without prescribing load`() {
        val ctx = context().copy(measuredDate = LocalDate.of(2025, 1, 1), bodyFatPercentage = java.math.BigDecimal("25"))
        val result = rules.result(ctx, rules.fallback(ctx), "RULE_BASED")
        assertEquals(ctx.measuredDate, result.inbodyMeasuredDate)
        assertTrue(result.notices.any { it.contains("중량은 추천하지") })
        assertTrue(rules.result(context(), rules.fallback(context()), "RULE_BASED").notices.any { it.contains("인바디 미사용") })
    }

    private fun splitCatalog() = listOf(BodyPart.CHEST, BodyPart.SHOULDERS, BodyPart.TRICEPS, BodyPart.BACK, BodyPart.BICEPS, BodyPart.BACK,
        BodyPart.QUADRICEPS, BodyPart.HAMSTRINGS, BodyPart.GLUTES, BodyPart.CORE).mapIndexed { i, part ->
        PlanCatalogExercise(UUID(1, i.toLong()), "분할 합성 $i", part, EquipmentType.MACHINE)
    }
    @Test fun `experienced four day fallback alternates upper and lower while beginner remains full body`() {
        val ctx = context(4, 45, SkillLevel.ONE_TO_THREE_YEARS).copy(catalog = splitCatalog())
        val days = rules.validate(ctx, rules.fallback(ctx))
        val groups = days.map { it.exercises.map { e -> rules.group(e.bodyPart) }.toSet() }
        assertTrue(groups[0].all { it in setOf("PUSH", "PULL") })
        assertTrue(groups[1].all { it in setOf("LEGS", "CORE") })
        assertEquals(groups[0], groups[2]); assertEquals(groups[1], groups[3])
        val novice = ctx.copy(skillLevel = SkillLevel.BEGINNER)
        assertTrue(rules.validate(novice, rules.fallback(novice)).all { day -> day.exercises.map { rules.group(it.bodyPart) }.containsAll(listOf("PUSH", "PULL", "LEGS")) })
    }
    @Test fun `six day split can focus on push pull and legs without daily full body`() {
        val ctx = context(6, 45, SkillLevel.OVER_THREE_YEARS).copy(catalog = splitCatalog())
        val days = rules.validate(ctx, rules.fallback(ctx))
        assertEquals(listOf("PUSH", "PULL", "LEGS", "PUSH", "PULL", "LEGS"), days.map { day ->
            day.exercises.map { rules.group(it.bodyPart) }.distinct().filter { it != "CORE" }.single()
        })
    }
    @Test fun `core cannot replace legs and weekly group imbalance is rejected`() {
        val ctx = context(4, 60, SkillLevel.OVER_THREE_YEARS).copy(catalog = splitCatalog())
        fun plan(parts: List<Pair<BodyPart, Int>>) = ProposedWorkoutPlan((1..4).map { day -> PlanDay(day, parts.map { (part, count) ->
            PlanExercise(ctx.catalog.first { it.bodyPart == part }.id, count, 10, 60)
        }) })
        assertThrows(ApiException::class.java) { rules.validate(ctx, plan(listOf(BodyPart.CHEST to 2, BodyPart.BACK to 2, BodyPart.CORE to 2))) }
        assertThrows(ApiException::class.java) { rules.validate(ctx, plan(listOf(BodyPart.CHEST to 4, BodyPart.BACK to 1, BodyPart.QUADRICEPS to 1))) }
    }
    @Test fun `split fallback stays valid under short time and equipment restrictions`() {
        for (days in 4..7) for (minutes in listOf(20, 30, 60)) for (skill in SkillLevel.entries) for (goal in GoalCode.entries) {
            val ctx = context(days, minutes, skill).copy(catalog = splitCatalog(), goal = goal)
            val result = rules.validate(ctx, rules.fallback(ctx))
            assertTrue(result.all { it.estimatedSeconds <= minutes * 60 })
        }
    }
}
