package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.time.Instant
import java.util.UUID

class OperatorReauthenticationTest {
    private val roles = mock(AdminRoleRepository::class.java)
    private val attempts = mock(OperatorAuthAttemptsRepository::class.java)
    private val receipts = mock(OperatorReauthRepository::class.java)
    private val actor = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(600))
    @Test fun `demo auth is unavailable unless explicitly enabled`() {
        val environment = MockEnvironment().also { it.setActiveProfiles("local") }
        val service = OperatorReauthentication(roles, attempts, receipts, environment, Clock.systemUTC(), false, "000000")
        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, assertThrows(ApiException::class.java) { service.authenticate(actor, "000000") }.errorCode)
        verifyNoInteractions(roles, attempts, receipts)
    }
    @Test fun `production profile cannot enable the demo code`() {
        val environment = MockEnvironment().also { it.setActiveProfiles("local", "production") }
        val service = OperatorReauthentication(roles, attempts, receipts, environment, Clock.systemUTC(), true, "000000")
        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, assertThrows(ApiException::class.java) { service.authenticate(actor, "000000") }.errorCode)
        verifyNoInteractions(roles, attempts, receipts)
    }
}
