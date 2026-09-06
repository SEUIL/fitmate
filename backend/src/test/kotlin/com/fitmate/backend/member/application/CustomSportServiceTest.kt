package com.fitmate.backend.member.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.api.CustomSportDto
import com.fitmate.backend.member.domain.UserCustomSport
import com.fitmate.backend.member.domain.UserCustomSportId
import com.fitmate.backend.member.repository.UserCustomSportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CustomSportServiceTest {
    private val repository = mock(UserCustomSportRepository::class.java)
    private val now = Instant.parse("2026-09-06T00:00:00Z")
    private val service = CustomSportService(repository, Clock.fixed(now, ZoneOffset.UTC))
    private val userId = UUID.randomUUID()

    @Test
    fun `normalizes whitespace and unicode while keeping display casing`() {
        val result = service.replace(userId, listOf(CustomSportDto("  ＣｒｏｓｓＦｉｔ　 Club "), CustomSportDto("필라테스")))

        assertEquals(listOf(CustomSportDto("CrossFit Club"), CustomSportDto("필라테스")), result)
        verify(repository).saveAll(argThat<List<UserCustomSport>> { rows ->
            rows.any { it.id == UserCustomSportId(userId, "crossfit club") && it.name == "CrossFit Club" && it.createdAt == now }
        })
    }

    @Test
    fun `rejects normalized duplicates before changing stored data`() {
        listOf(
            listOf(" 필라테스 ", "필라테스"),
            listOf("CrossFit", "crossfit"),
            listOf("Cross  Fit", "Cross\tFit"),
            listOf("ＣｒｏｓｓＦｉｔ", "CrossFit"),
        ).forEach { names ->
            val error = assertThrows(ApiException::class.java) { service.replace(userId, names.map(::CustomSportDto)) }
            assertEquals(ApiErrorCode.VALIDATION_ERROR, error.errorCode)
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `rejects empty oversized and control character names`() {
        listOf("", " \t\n", "\u3000", "a".repeat(51), "bad\u0000name").forEach { name ->
            assertThrows(ApiException::class.java) { service.replace(userId, listOf(CustomSportDto(name))) }
        }
        assertThrows(ApiException::class.java) { service.replace(userId, (1..21).map { CustomSportDto("운동$it") }) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `accepts name and collection size boundaries`() {
        service.validate(listOf(CustomSportDto("a".repeat(50))))
        service.validate((1..20).map { CustomSportDto("운동$it") })
        service.validate(emptyList())
    }

    @Test
    fun `replacement preserves creation time for retained interests and removes omitted ones`() {
        val createdAt = now.minusSeconds(3600)
        val retained = UserCustomSport(UserCustomSportId(userId, "crossfit"), "crossfit", createdAt)
        val removed = UserCustomSport(UserCustomSportId(userId, "필라테스"), "필라테스", createdAt)
        `when`(repository.findAllByIdUserIdOrderByIdNormalizedNameAsc(userId)).thenReturn(listOf(retained, removed))

        service.replace(userId, listOf(CustomSportDto("CrossFit")))

        verify(repository).deleteAll(listOf(removed))
        verify(repository).saveAll(listOf(retained))
        assertEquals("CrossFit", retained.name)
        assertEquals(createdAt, retained.createdAt)
    }

    @Test
    fun `empty array deletes all custom interests belonging to current user`() {
        val existing = listOf(UserCustomSport(UserCustomSportId(userId, "필라테스"), "필라테스", now))
        `when`(repository.findAllByIdUserIdOrderByIdNormalizedNameAsc(userId)).thenReturn(existing)

        assertEquals(emptyList<CustomSportDto>(), service.replace(userId, emptyList()))
        verify(repository).deleteAll(existing)
    }

    @Test
    fun `same name can be saved independently for two users and read back`() {
        val rows = mutableMapOf<UserCustomSportId, UserCustomSport>()
        `when`(repository.saveAll(anyList<UserCustomSport>())).thenAnswer { call ->
            call.getArgument<List<UserCustomSport>>(0).also { saved -> saved.forEach { rows[it.id] = it } }
        }
        val otherUserId = UUID.randomUUID()
        listOf(userId, otherUserId).forEach { owner ->
            `when`(repository.findAllByIdUserIdOrderByIdNormalizedNameAsc(owner)).thenAnswer { rows.values.filter { it.id.userId == owner } }
            service.replace(owner, listOf(CustomSportDto("필라테스")))
        }

        assertEquals(2, rows.size)
        assertEquals(listOf(CustomSportDto("필라테스")), service.get(userId))
        assertEquals(listOf(CustomSportDto("필라테스")), service.get(otherUserId))
    }
}
