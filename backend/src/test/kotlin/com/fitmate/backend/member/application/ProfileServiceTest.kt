package com.fitmate.backend.member.application

import com.fitmate.backend.auth.api.SportSelectionRequest
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.domain.Sport
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.api.CustomSportDto
import com.fitmate.backend.member.api.UpdateProfileRequest
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.domain.UserProfile
import com.fitmate.backend.member.domain.UserSport
import com.fitmate.backend.member.domain.UserSportId
import com.fitmate.backend.member.repository.*
import com.fitmate.backend.terms.repository.UserConsentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class ProfileServiceTest {
    private val users = mock(UserRepository::class.java)
    private val profiles = mock(UserProfileRepository::class.java)
    private val regions = mock(RegionRepository::class.java)
    private val facilities = mock(FacilityRepository::class.java)
    private val userRegions = mock(UserActivityRegionRepository::class.java)
    private val userFacilities = mock(UserFacilityRepository::class.java)
    private val times = mock(UserAvailableTimeRepository::class.java)
    private val userSports = mock(UserSportRepository::class.java)
    private val sports = mock(SportRepository::class.java)
    private val goals = mock(UserGoalRepository::class.java)
    private val consents = mock(UserConsentRepository::class.java)
    private val customSports = mock(CustomSportService::class.java)
    private val now = Instant.parse("2026-09-06T00:00:00Z")
    private val service = ProfileService(users, profiles, regions, facilities, userRegions, userFacilities,
        times, userSports, sports, goals, consents, customSports, Clock.fixed(now, ZoneOffset.UTC))
    private val userId = UUID.randomUUID()
    private val profile = UserProfile(userId = userId, nickname = "fitmate", version = 3)

    @BeforeEach
    fun setUp() {
        `when`(users.findById(userId)).thenReturn(Optional.of(User(id = userId)))
        `when`(profiles.findById(userId)).thenReturn(Optional.of(profile))
        `when`(profiles.findForUpdateByUserId(userId)).thenReturn(profile)
    }

    @Test
    fun `custom-only update validates and replaces interests and marks profile dirty`() {
        val selection = listOf(CustomSportDto("필라테스"))
        `when`(customSports.get(userId)).thenReturn(selection)

        val result = service.update(userId, UpdateProfileRequest(version = 3, customSports = selection))

        verify(customSports).validate(selection)
        verify(customSports).replace(userId, selection)
        verify(profiles).saveAndFlush(profile)
        assertEquals(now, profile.updatedAt)
        assertEquals(selection, result.customSports)
        verify(userSports, never()).deleteAllByIdUserId(userId)
        verifyNoInteractions(sports)
    }

    @Test
    fun `omitted custom sports are preserved and empty array clears them`() {
        val sportId = UUID.randomUUID()
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.BEGINNER)))
        `when`(sports.findById(sportId)).thenReturn(Optional.of(Sport(id = sportId, code = SportCode.RUNNING)))
        service.update(userId, UpdateProfileRequest(version = 3, bio = "소개"))
        verify(customSports, never()).replace(eq(userId) ?: userId, anyList())

        service.update(userId, UpdateProfileRequest(version = 3, customSports = emptyList()))
        verify(customSports).replace(userId, emptyList())
    }

    @Test
    fun `stale version fails before any interests are changed`() {
        val error = assertThrows(ApiException::class.java) {
            service.update(userId, UpdateProfileRequest(version = 2, customSports = emptyList()))
        }
        assertEquals(ApiErrorCode.VERSION_CONFLICT, error.errorCode)
        verifyNoInteractions(customSports, userSports)
        verify(profiles, never()).saveAndFlush(profile)
    }

    @Test
    fun `missing or deleted profile cannot be updated`() {
        `when`(profiles.findForUpdateByUserId(userId)).thenReturn(null)
        val error = assertThrows(ApiException::class.java) { service.update(userId, UpdateProfileRequest(version = 3)) }
        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, error.errorCode)
        verifyNoInteractions(customSports)
    }

    @Test
    fun `managed sports remain separate from custom interests on read`() {
        val sportId = UUID.randomUUID()
        `when`(userSports.findAllByIdUserId(userId)).thenReturn(listOf(UserSport(UserSportId(userId, sportId), SkillLevel.BEGINNER)))
        `when`(sports.findById(sportId)).thenReturn(Optional.of(Sport(id = sportId, code = SportCode.HEALTH)))
        `when`(customSports.get(userId)).thenReturn(listOf(CustomSportDto("크로스핏")))

        val result = service.get(userId)

        assertEquals(listOf(SportSelectionRequest(SportCode.HEALTH, SkillLevel.BEGINNER)), result.sports)
        assertEquals(listOf(CustomSportDto("크로스핏")), result.customSports)
    }

    @Test
    fun `removing all managed and custom interests is rejected`() {
        val error = assertThrows(ApiException::class.java) {
            service.update(userId, UpdateProfileRequest(version = 3, sports = emptyList(), customSports = emptyList()))
        }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, error.errorCode)
        verify(customSports, never()).replace(eq(userId) ?: userId, anyList())
        verify(userSports, never()).deleteAllByIdUserId(userId)
    }

    @Test
    fun `custom interests can replace managed interests without HEALTH`() {
        val selected = listOf(CustomSportDto("필라테스"))
        `when`(customSports.get(userId)).thenReturn(selected)
        val result = service.update(userId, UpdateProfileRequest(version = 3, sports = emptyList(), customSports = selected))
        assertEquals(selected, result.customSports)
        verify(userSports).deleteAllByIdUserId(userId)
        verify(customSports).replace(userId, selected)
    }
}
