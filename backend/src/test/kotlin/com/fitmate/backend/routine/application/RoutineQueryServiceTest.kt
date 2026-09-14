package com.fitmate.backend.routine.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import com.fitmate.backend.fitness.domain.Sport
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.domain.UserGoal
import com.fitmate.backend.member.domain.UserGoalId
import com.fitmate.backend.member.domain.UserSport
import com.fitmate.backend.member.domain.UserSportId
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.domain.RoutineItem
import com.fitmate.backend.routine.repository.RoutineItemRepository
import com.fitmate.backend.routine.repository.RoutineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class RoutineQueryServiceTest {
    private val routines = mock(RoutineRepository::class.java)
    private val items = mock(RoutineItemRepository::class.java)
    private val goals = mock(UserGoalRepository::class.java)
    private val userSports = mock(UserSportRepository::class.java)
    private val sports = mock(SportRepository::class.java)
    private val service = RoutineQueryService(routines, items, goals, userSports, sports)
    private val userId = UUID.randomUUID()
    private val sportId = UUID.randomUUID()
    private val health = Sport(id = sportId, code = SportCode.HEALTH, name = "헬스", isActive = true)

    @BeforeEach
    fun setUp() {
        `when`(sports.findByCodeAndIsActiveTrue(SportCode.HEALTH)).thenReturn(health)
        `when`(goals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.FITNESS), true)))
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.BEGINNER)))
    }

    @Test
    fun `recommended routine prefers exact goal and skill match`() {
        `when`(goals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.MUSCLE_GAIN), true)))
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.SIX_MONTHS_TO_ONE_YEAR)))
        val fallback = template("fallback", GoalCode.FITNESS, SkillLevel.BEGINNER)
        val exact = template("exact", GoalCode.MUSCLE_GAIN, SkillLevel.SIX_MONTHS_TO_ONE_YEAR)
        `when`(routines.findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()).thenReturn(listOf(fallback, exact))
        val exercise = ExerciseCatalog(id = UUID.randomUUID(), sport = health, name = "스쿼트", bodyPart = BodyPart.LEGS)
        `when`(items.findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(exact.id)))
            .thenReturn(listOf(RoutineItem(id = UUID.randomUUID(), routine = exact, exercise = exercise, sequenceNo = 1)))

        val result = service.getRecommended(userId, SportCode.HEALTH)

        assertEquals("exact", result.name)
        assertEquals(listOf("스쿼트"), result.items.map { it.exerciseName })
    }

    @Test
    fun `recommended routine uses goal-wide template before beginner fallback`() {
        `when`(goals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.HABIT), true)))
        val fallback = template("초급 전신", GoalCode.FITNESS, SkillLevel.BEGINNER)
        val goalWide = template("운동 습관", GoalCode.HABIT, null)
        `when`(routines.findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()).thenReturn(listOf(fallback, goalWide))

        assertEquals("운동 습관", service.getRecommended(userId, SportCode.HEALTH).name)
    }

    @Test
    fun `trained user can use a matching fitness template before a goal-wide beginner template`() {
        `when`(goals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.DIET), true)))
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.ONE_TO_THREE_YEARS)))
        val beginnerDiet = template("다이어트 전신", GoalCode.DIET, null)
        val experiencedFitness = template("숙련 근비대", GoalCode.FITNESS, SkillLevel.ONE_TO_THREE_YEARS)
        `when`(routines.findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()).thenReturn(listOf(beginnerDiet, experiencedFitness))

        assertEquals("숙련 근비대", service.getRecommended(userId, SportCode.HEALTH).name)
    }

    @Test
    fun `recommended routine falls back when profile has no matching template`() {
        `when`(goals.findAllByIdUserId(userId)).thenReturn(listOf(UserGoal(UserGoalId(userId, GoalCode.RECORD_IMPROVEMENT), true)))
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.OVER_THREE_YEARS)))
        val fallback = template("초급 전신", GoalCode.FITNESS, SkillLevel.BEGINNER)
        `when`(routines.findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()).thenReturn(listOf(fallback))

        assertEquals("초급 전신", service.getRecommended(userId, SportCode.HEALTH).name)
    }

    @Test
    fun `recommendation requires active sport and complete user selections`() {
        val inactive = assertThrows(ApiException::class.java) { service.getRecommended(userId, SportCode.RUNNING) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, inactive.errorCode)
        verify(sports, never()).findByCodeAndIsActiveTrue(SportCode.RUNNING)
        verify(goals, never()).findAllByIdUserId(userId)

        `when`(goals.findAllByIdUserId(userId)).thenReturn(emptyList())
        val missingGoal = assertThrows(ApiException::class.java) { service.getRecommended(userId, SportCode.HEALTH) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, missingGoal.errorCode)
    }

    @Test
    fun `detail allows templates and owned routines but hides another user's routine`() {
        val template = template("템플릿", GoalCode.HABIT, null)
        val owned = userRoutine("내 루틴", userId)
        val other = userRoutine("다른 루틴", UUID.randomUUID())
        listOf(template, owned, other).forEach { `when`(routines.findByIdAndIsActiveTrue(requireNotNull(it.id))).thenReturn(it) }

        val templateApiId = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(template.id))
        val ownedApiId = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(owned.id))
        val otherApiId = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(other.id))
        assertEquals("템플릿", service.getDetail(userId, templateApiId).name)
        assertEquals("내 루틴", service.getDetail(userId, ownedApiId).name)
        val hidden = assertThrows(ApiException::class.java) { service.getDetail(userId, otherApiId) }
        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, hidden.errorCode)
        verify(items, never()).findAllByRoutineIdOrderBySequenceNoAsc(requireNotNull(other.id))
    }

    @Test
    fun `template and personal lists use separate repository scopes`() {
        val template = template("템플릿", GoalCode.HABIT, null)
        val owned = userRoutine("내 루틴", userId)
        `when`(routines.findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc()).thenReturn(listOf(template))
        `when`(routines.findAllByOwnerUserIdAndIsTemplateFalseAndIsActiveTrueOrderByUpdatedAtDesc(userId)).thenReturn(listOf(owned))

        assertEquals(listOf("템플릿"), service.getTemplates().map { it.name })
        assertEquals(listOf("내 루틴"), service.getMine(userId).map { it.name })
    }

    private fun template(name: String, goal: GoalCode, level: SkillLevel?) = Routine(
        id = UUID.randomUUID(), sport = health, name = name, targetGoalCode = goal, targetLevel = level, isTemplate = true,
    )

    private fun userRoutine(name: String, ownerId: UUID) = Routine(
        id = UUID.randomUUID(), ownerUserId = ownerId, sport = health, name = name, isTemplate = false,
    )
}
