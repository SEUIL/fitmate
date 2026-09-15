package com.fitmate.backend.mate.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import org.springframework.data.domain.PageRequest
import com.fitmate.backend.mate.api.CreateMatePostRequest
import com.fitmate.backend.mate.api.UpdateMatePostRequest
import com.fitmate.backend.mate.domain.MateDifficulty
import com.fitmate.backend.mate.domain.MatePost
import com.fitmate.backend.mate.domain.MatePostStatus
import com.fitmate.backend.mate.repository.MateParticipationRepository
import com.fitmate.backend.mate.repository.MatePostRepository
import com.fitmate.backend.member.repository.RegionRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.matechat.repository.MateChatRoomRepository
import com.fitmate.backend.notification.application.NotificationWriter
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MateServiceTest {
    private val posts = mock(MatePostRepository::class.java)
    private val applications = mock(MateParticipationRepository::class.java)
    private val service = MateService(posts, applications, mock(SportRepository::class.java), mock(RegionRepository::class.java), mock(IdempotencyKeyRepository::class.java), mock(NotificationWriter::class.java), Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC), mock(MateChatRoomRepository::class.java), mock(UserProfileRepository::class.java), ObjectMapper().findAndRegisterModules(), mock(MateAccessPolicy::class.java), mock(com.fitmate.backend.admin.application.ModerationRuleEngine::class.java))
    private val author = UUID.randomUUID()

    @Test fun `list binds every sport filter as an enum`() {
        SportCode.entries.forEach { sport ->
            `when`(posts.findPage(sport, null, null, null, null, null, null, null, PageRequest.of(0, 31), author)).thenReturn(emptyList())
            service.list(author, sport.name, null, null, null, null, null, null, 30)
            verify(posts).findPage(sport, null, null, null, null, null, null, null, PageRequest.of(0, 31), author)
        }
    }

    @Test fun `list preserves combined date region level and status filters`() {
        val region = UUID.randomUUID()
        val dbRegion = UuidBinaryConverter.toMysqlOrderedUuid(region)
        val from = Instant.parse("2026-09-10T15:00:00Z")
        val to = Instant.parse("2026-09-11T14:59:59.999999999Z")
        `when`(posts.findPage(SportCode.HEALTH, dbRegion, from, to, MateDifficulty.BEGINNER, MatePostStatus.RECRUITING, null, null, PageRequest.of(0, 31), author)).thenReturn(emptyList())
        service.list(author, "HEALTH", region, from, to, MateDifficulty.BEGINNER, MatePostStatus.RECRUITING, null, 30)
        verify(posts).findPage(SportCode.HEALTH, dbRegion, from, to, MateDifficulty.BEGINNER, MatePostStatus.RECRUITING, null, null, PageRequest.of(0, 31), author)
    }

    @Test fun `list accepts all sports and rejects unknown sport before querying`() {
        `when`(posts.findPage(null, null, null, null, null, null, null, null, PageRequest.of(0, 31), author)).thenReturn(emptyList())
        service.list(author, null, null, null, null, null, null, null, 30)
        verify(posts).findPage(null, null, null, null, null, null, null, null, PageRequest.of(0, 31), author)
        clearInvocations(posts)
        val error = assertThrows(ApiException::class.java) { service.list(author, "INVALID", null, null, null, null, null, null, 30) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, error.errorCode)
        verifyNoInteractions(posts)
    }

    @Test fun `cannot reduce capacity below accepted members`() {
        val post = MatePost(id = UUID.randomUUID(), authorId = author, capacity = 3, acceptedCount = 2)
        `when`(posts.findLocked(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(post)
        val error = assertThrows(ApiException::class.java) { service.update(author, UUID.randomUUID(), UpdateMatePostRequest(0, capacity = 1)) }
        assertEquals(ApiErrorCode.VALIDATION_ERROR, error.errorCode)
    }

    @Test fun `full post rejects new application`() {
        val post = MatePost(id = UUID.randomUUID(), authorId = author, status = MatePostStatus.FULL, scheduledStartAt = Instant.parse("2026-09-08T00:00:00Z"))
        `when`(posts.findLocked(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(post)
        val error = assertThrows(ApiException::class.java) { service.apply(UUID.randomUUID(), UUID.randomUUID()) }
        assertEquals(ApiErrorCode.MATE_POST_NOT_RECRUITING, error.errorCode)
        verifyNoInteractions(applications)
    }

    @Test fun `past start time rejects application`() {
        val post = MatePost(id = UUID.randomUUID(), authorId = author, status = MatePostStatus.RECRUITING, scheduledStartAt = Instant.parse("2026-09-06T23:59:59Z"))
        `when`(posts.findLocked(any(UUID::class.java) ?: UUID(0, 0))).thenReturn(post)
        val error = assertThrows(ApiException::class.java) { service.apply(UUID.randomUUID(), UUID.randomUUID()) }
        assertEquals(ApiErrorCode.MATE_POST_NOT_RECRUITING, error.errorCode)
    }
}
