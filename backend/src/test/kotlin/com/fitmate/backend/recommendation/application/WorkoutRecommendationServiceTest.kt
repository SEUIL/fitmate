package com.fitmate.backend.recommendation.application

import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.inbody.domain.InbodyRecord
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.domain.UserGoal
import com.fitmate.backend.member.domain.UserGoalId
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.recommendation.api.CreateWorkoutRecommendationRequest
import com.fitmate.backend.recommendation.infrastructure.WorkoutRecommendationNarrator
import com.fitmate.backend.routine.application.RoutineQueryService
import com.fitmate.backend.routine.application.dto.RoutineDetail
import com.fitmate.backend.routine.application.dto.RoutineItemResponse
import com.fitmate.backend.routine.domain.DifficultyLevel
import com.fitmate.backend.routine.domain.RoutineSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WorkoutRecommendationServiceTest {
    private val routines = mock(RoutineQueryService::class.java)
    private val exercises = mock(ExerciseCatalogRepository::class.java)
    private val records = mock(InbodyRecordRepository::class.java)
    private val userGoals = mock(UserGoalRepository::class.java)
    private val narrator = mock(WorkoutRecommendationNarrator::class.java)
    private val service = WorkoutRecommendationService(routines, exercises, records, userGoals, narrator)
    private val userId = UUID.randomUUID()

    @Test
    fun `beginner plan starts with two sets without requiring an inbody record`() {
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.BEGINNER, GoalCode.FITNESS, 3))

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(daysPerWeek = 3, sessionMinutes = 45))

        assertEquals("초급 전신 · 적응형", result.name)
        assertEquals(2, result.items.single().targetSets)
        assertEquals(6, result.items.single().targetRpe)
        assertEquals(1, result.splitCount)
        assertFalse(result.inbodyUsed)
        assertEquals(0, result.cardioMinutes)
    }

    @Test
    fun `selected owned inbody is reflected in a diet recommendation`() {
        val recordId = UUID.randomUUID()
        val record = InbodyRecord(
            id = recordId, userId = userId, measuredDate = LocalDate.of(2026, 9, 1),
            weightKg = BigDecimal("72.0"), skeletalMuscleMassKg = BigDecimal("30.0"), bodyFatPercentage = BigDecimal("22.0"),
        )
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.ONE_TO_THREE_YEARS, GoalCode.DIET, 3))
        `when`(records.findByIdAndUserId(recordId, userId)).thenReturn(record)

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(3, 45, inbodyRecordId = recordId))

        assertEquals(15, result.cardioMinutes)
        assertEquals(record.measuredDate, result.inbodyMeasuredDate)
        assertEquals(3, result.items.single().targetSets)
    }

    @Test
    fun `high body fat percentage can add brief cardio`() {
        val recordId = UUID.randomUUID()
        val record = InbodyRecord(
            id = recordId, userId = userId, measuredDate = LocalDate.of(2026, 9, 1),
            weightKg = BigDecimal("72.0"), skeletalMuscleMassKg = BigDecimal("30.0"), bodyFatPercentage = BigDecimal("31.0"),
        )
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.ONE_TO_THREE_YEARS, GoalCode.FITNESS, 3))
        `when`(records.findByIdAndUserId(recordId, userId)).thenReturn(record)

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(3, 45, inbodyRecordId = recordId))

        assertEquals(15, result.cardioMinutes)
    }

    @Test
    fun `high recorded BMI can add brief cardio`() {
        val recordId = UUID.randomUUID()
        val record = InbodyRecord(
            id = recordId, userId = userId, measuredDate = LocalDate.of(2026, 9, 1),
            weightKg = BigDecimal("72.0"), skeletalMuscleMassKg = BigDecimal("30.0"), bodyFatPercentage = BigDecimal("22.0"),
            optionalValuesJson = "{\"BMI\":27.1}",
        )
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.ONE_TO_THREE_YEARS, GoalCode.FITNESS, 3))
        `when`(records.findByIdAndUserId(recordId, userId)).thenReturn(record)

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(3, 45, inbodyRecordId = recordId))

        assertEquals(15, result.cardioMinutes)
    }

    @Test
    fun `large left and right leg difference adds a non-diagnostic check note`() {
        val recordId = UUID.randomUUID()
        val record = InbodyRecord(
            id = recordId, userId = userId, measuredDate = LocalDate.of(2026, 9, 1),
            weightKg = BigDecimal("72.0"), skeletalMuscleMassKg = BigDecimal("30.0"), bodyFatPercentage = BigDecimal("22.0"),
            optionalValuesJson = "{\"LEFT_LEG_MUSCLE_KG\":9.0,\"RIGHT_LEG_MUSCLE_KG\":7.8}",
        )
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.ONE_TO_THREE_YEARS, GoalCode.FITNESS, 3))
        `when`(records.findByIdAndUserId(recordId, userId)).thenReturn(record)

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(3, 45, inbodyRecordId = recordId))

        org.junit.jupiter.api.Assertions.assertTrue(result.reasons.any { it.contains("좌우 다리") })
    }

    @Test
    fun `another users inbody cannot be selected`() {
        val recordId = UUID.randomUUID()
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.BEGINNER, GoalCode.FITNESS, 3))
        `when`(records.findByIdAndUserId(recordId, userId)).thenReturn(null)

        assertThrows(ApiException::class.java) {
            service.recommend(userId, CreateWorkoutRecommendationRequest(3, 45, inbodyRecordId = recordId))
        }
    }

    @Test
    fun `Luna narration is returned as the recommendation summary without changing the routine`() {
        val lunaNarrator = object : WorkoutRecommendationNarrator {
            override fun narrate(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String? {
                return "현재 운동 빈도에 맞춰 천천히 시작해 보세요."
            }
        }
        val lunaService = WorkoutRecommendationService(routines, exercises, records, userGoals, lunaNarrator)
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.BEGINNER, GoalCode.FITNESS, 3))

        val result = lunaService.recommend(userId, CreateWorkoutRecommendationRequest(daysPerWeek = 3, sessionMinutes = 45))

        assertEquals("현재 운동 빈도에 맞춰 천천히 시작해 보세요.", result.aiSummary)
        assertEquals(2, result.items.single().targetSets)
        assertEquals(6, result.items.single().targetRpe)
    }

    @Test
    fun `split routine is folded into available training days`() {
        val advanced = baseRoutine(SkillLevel.OVER_THREE_YEARS, GoalCode.FITNESS, 4).copy(
            splitCount = 4,
            items = (1..4).map { day -> RoutineItemResponse(
                id = UUID.randomUUID(), exerciseId = UUID.randomUUID(), exerciseName = "운동 $day", bodyPart = BodyPart.LEGS,
                sequenceNo = day, splitDayNo = day, targetSets = 4, targetRepsMin = 8, targetRepsMax = 10,
                targetWeightKg = null, targetRpe = 9, restSeconds = 90, version = 0,
            ) },
        )
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(advanced)

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(daysPerWeek = 2, sessionMinutes = 60))

        assertEquals(2, result.splitCount)
        assertEquals(listOf(1, 2, 1, 2), result.items.map { it.splitDayNo })
        org.junit.jupiter.api.Assertions.assertTrue(result.reasons.any { it.contains("4분할 구성을 2분할") })
    }

    @Test
    fun `users actual diet goal controls cardio even with a fitness experience template`() {
        `when`(routines.getRecommended(userId, SportCode.HEALTH)).thenReturn(baseRoutine(SkillLevel.ONE_TO_THREE_YEARS, GoalCode.FITNESS, 3))
        `when`(userGoals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.DIET), true)))

        val result = service.recommend(userId, CreateWorkoutRecommendationRequest(daysPerWeek = 3, sessionMinutes = 45))

        assertEquals(15, result.cardioMinutes)
    }

    private fun baseRoutine(level: SkillLevel, goal: GoalCode, sets: Int) = RoutineDetail(
        id = UUID.randomUUID(), ownerUserId = null, sportCode = SportCode.HEALTH, name = "초급 전신",
        sourceType = RoutineSourceType.SYSTEM_TEMPLATE, targetGoalCode = goal, targetLevel = level,
        difficulty = DifficultyLevel.BEGINNER, splitCount = 1, template = true, version = 0,
        items = listOf(RoutineItemResponse(
            id = UUID.randomUUID(), exerciseId = UUID.randomUUID(), exerciseName = "레그 프레스", bodyPart = BodyPart.LEGS,
            sequenceNo = 1, splitDayNo = 1, targetSets = sets, targetRepsMin = 10, targetRepsMax = 12,
            targetWeightKg = null, targetRpe = 7, restSeconds = 90, version = 0,
        )),
    )
}
