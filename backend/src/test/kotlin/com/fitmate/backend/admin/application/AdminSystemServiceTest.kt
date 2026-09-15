package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.inbody.repository.InbodyFailureCount
import com.fitmate.backend.inbody.repository.InbodyStatusCount
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AdminSystemServiceTest {
    private val roles = mock(AdminRoleRepository::class.java)
    private val analyses = mock(InbodyAnalysisRepository::class.java)
    private val now = Instant.parse("2026-09-15T00:00:00Z")
    private val from = now.minusSeconds(86400)
    private val actor = UUID.randomUUID()
    private val user = AuthenticatedUser(actor, UUID.randomUUID(), "test", 0, now.plusSeconds(900))
    private val service = AdminSystemService(roles, analyses, Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `regular users and moderators cannot query system health`() {
        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, assertThrows(ApiException::class.java) { service.summary(user, null, null) }.errorCode)
        authorize(AdminRole.MODERATOR)
        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, assertThrows(ApiException::class.java) { service.summary(user, null, null) }.errorCode)
        verifyNoInteractions(analyses)
    }

    @Test fun `failure rate excludes pending and processing and unknown metrics stay unavailable`() {
        authorize()
        `when`(analyses.countStatuses(from, now)).thenReturn(listOf(
            status(InbodyAnalysisStatus.UPLOADED, 10), status(InbodyAnalysisStatus.PROCESSING, 6),
            status(InbodyAnalysisStatus.COMPLETED, 2), status(InbodyAnalysisStatus.REVIEW_REQUIRED, 1), status(InbodyAnalysisStatus.FAILED, 1)))
        `when`(analyses.countFailureCodes(from, now, InbodyAnalysisStatus.FAILED)).thenReturn(listOf(failure("AI_TIMEOUT", 1)))
        val result = service.summary(user, null, null)
        assertEquals(0.25, result.failureRate)
        assertEquals(20, result.statuses.sumOf { it.count })
        assertEquals("AI_TIMEOUT", result.failures.single().code)
        assertEquals(from, result.from)
        assertEquals("UNAVAILABLE", result.activeUsers.availability)
        assertNull(result.activeUsers.value)
        assertNull(result.apiErrorRate.value)
        assertNull(result.apiLatencyMs.value)
    }

    @Test fun `empty results have no failure percentage and unsafe failure strings are omitted`() {
        authorize(AdminRole.SUPER_ADMIN)
        assertNull(service.summary(user, null, null).failureRate)
        `when`(analyses.countFailureCodes(from, now, InbodyAnalysisStatus.FAILED))
            .thenReturn(listOf(failure(null, 1), failure("unexpected provider detail", 2)))
        val result = service.summary(user, null, null)
        assertEquals("UNKNOWN", result.failures.single().code)
        assertEquals(3, result.failures.single().count)
    }

    @Test fun `invalid ranges are rejected before database queries`() {
        authorize()
        for ((start, end) in listOf(now to now, now to from, from to now.plusSeconds(1), now.minusSeconds(32 * 86400L) to now)) {
            assertEquals(ApiErrorCode.VALIDATION_ERROR, assertThrows(ApiException::class.java) { service.summary(user, start, end) }.errorCode)
        }
        verifyNoInteractions(analyses)
    }

    private fun authorize(role: AdminRole = AdminRole.ADMIN) {
        `when`(roles.findByUserIdAndRevokedAtIsNull(actor)).thenReturn(AdminRoleAssignment(actor, role, now))
    }
    private fun status(value: InbodyAnalysisStatus, count: Long) = object : InbodyStatusCount {
        override val status = value
        override val total = count
    }
    private fun failure(value: String?, count: Long) = object : InbodyFailureCount {
        override val code = value
        override val total = count
    }
}
