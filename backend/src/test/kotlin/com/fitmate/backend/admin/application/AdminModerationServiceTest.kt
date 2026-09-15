package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.ClaimReportRequest
import com.fitmate.backend.admin.api.DecideReportRequest
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.domain.AdminSanction
import com.fitmate.backend.admin.domain.ModerationAction
import com.fitmate.backend.admin.domain.ModerationReason
import com.fitmate.backend.admin.repository.AdminAuditLogRepository
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.admin.repository.AdminSanctionRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.community.repository.CommunityPostCommentRepository
import com.fitmate.backend.community.repository.CommunityPostRepository
import com.fitmate.backend.dm.repository.DirectMessageRepository
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.mate.repository.MatePostRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import com.fitmate.backend.reporting.domain.ReportStatus
import com.fitmate.backend.reporting.domain.UserReport
import com.fitmate.backend.reporting.repository.UserReportRepository
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class AdminModerationServiceTest {
    private val roles = mock(AdminRoleRepository::class.java)
    private val reports = mock(UserReportRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val profiles = mock(UserProfileRepository::class.java)
    private val sanctions = mock(AdminSanctionRepository::class.java)
    private val audits = mock(AdminAuditLogRepository::class.java)
    private val keys = mock(IdempotencyKeyRepository::class.java)
    private val now = Instant.parse("2026-09-15T00:00:00Z")
    private val service = AdminModerationService(
        roles, reports, users, profiles, sanctions, audits, keys,
        mock(WorkoutSessionRepository::class.java), mock(InbodyAnalysisRepository::class.java),
        mock(CommunityPostRepository::class.java), mock(CommunityPostCommentRepository::class.java),
        mock(DirectMessageRepository::class.java), mock(MatePostRepository::class.java),
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val adminId = UUID.randomUUID()
    private val auth = AuthenticatedUser(adminId, UUID.randomUUID(), UUID.randomUUID().toString(), 0, now.plusSeconds(900))

    @Test
    fun `non admin cannot read dashboard`() {
        val error = assertThrows(ApiException::class.java) { service.dashboard(auth) }
        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, error.errorCode)
        verifyNoInteractions(reports)
    }

    @Test
    fun `claim rejects stale report version`() {
        authorize()
        val reportId = UUID.randomUUID()
        `when`(reports.findForUpdate(reportId)).thenReturn(UserReport(id = reportId, version = 2))

        val error = assertThrows(ApiException::class.java) { service.claim(auth, reportId, ClaimReportRequest(1)) }

        assertEquals(ApiErrorCode.VERSION_CONFLICT, error.errorCode)
        verify(reports, never()).saveAndFlush(any(UserReport::class.java))
    }

    @Test
    fun `admin cannot sanction own account`() {
        authorize()
        val reportId = UUID.randomUUID()
        val report = UserReport(id = reportId, targetId = adminId, subjectUserId = adminId, status = ReportStatus.REVIEWING, version = 1)
        `when`(reports.findForUpdate(reportId)).thenReturn(report)
        val request = DecideReportRequest(1, ReportStatus.RESOLVED, ModerationAction.WARNING, ModerationReason.SPAM, "경고 안내")

        val error = assertThrows(ApiException::class.java) { service.decide(auth, reportId, UUID.randomUUID().toString(), request) }

        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, error.errorCode)
        verifyNoInteractions(sanctions)
    }

    @Test
    fun `expired suspension becomes active for next login`() {
        val suspended = User(id = adminId, accountStatus = AccountStatus.SUSPENDED)
        `when`(users.findForOperatorUpdate(adminId)).thenReturn(suspended)
        `when`(sanctions.findAllByUserIdAndAction(adminId, ModerationAction.SUSPEND))
            .thenReturn(listOf(AdminSanction(userId = adminId, action = ModerationAction.SUSPEND, endsAt = now.minusSeconds(1))))
        `when`(users.save(suspended)).thenReturn(suspended)
        val suspensionService = AccountSuspensionService(sanctions, users, Clock.fixed(now, ZoneOffset.UTC))

        val result = suspensionService.restoreIfExpired(suspended)

        assertEquals(AccountStatus.ACTIVE, result.accountStatus)
        verify(users).save(suspended)
    }

    @Test
    fun `short suspension expiry does not cancel older active suspension`() {
        val user = User(id = adminId, accountStatus = AccountStatus.SUSPENDED)
        `when`(users.findForOperatorUpdate(adminId)).thenReturn(user)
        `when`(sanctions.findAllByUserIdAndAction(adminId, ModerationAction.SUSPEND)).thenReturn(listOf(
            AdminSanction(endsAt = now.plusSeconds(86400 * 20)), AdminSanction(endsAt = now.minusSeconds(1))))
        val result = AccountSuspensionService(sanctions, users, Clock.fixed(now, ZoneOffset.UTC)).restoreIfExpired(user)
        assertEquals(AccountStatus.SUSPENDED, result.accountStatus)
        verify(users, never()).save(user)
    }

    @Test
    fun `missing suspension record never reactivates an account`() {
        val user = User(id = adminId, accountStatus = AccountStatus.SUSPENDED)
        `when`(users.findForOperatorUpdate(adminId)).thenReturn(user)
        assertEquals(AccountStatus.SUSPENDED,
            AccountSuspensionService(sanctions, users, Clock.fixed(now, ZoneOffset.UTC)).restoreIfExpired(user).accountStatus)
    }

    @Test
    fun `moderator cannot suspend even with a direct decision request`() {
        authorize(AdminRole.MODERATOR)
        val request = DecideReportRequest(1, ReportStatus.RESOLVED, ModerationAction.SUSPEND,
            ModerationReason.SPAM, "이용 제한 안내", durationDays = 7)

        val error = assertThrows(ApiException::class.java) {
            service.decide(auth, UUID.randomUUID(), UUID.randomUUID().toString(), request)
        }

        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, error.errorCode)
        verifyNoInteractions(reports, sanctions, users, keys, audits)
    }

    @Test
    fun `detail actions follow role and protect other operators`() {
        val reportId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val report = UserReport(id = reportId, targetId = subjectId, subjectUserId = subjectId,
            status = ReportStatus.REVIEWING, version = 1)
        `when`(reports.findById(reportId)).thenReturn(Optional.of(report))
        `when`(users.existsById(subjectId)).thenReturn(true)
        authorize(AdminRole.MODERATOR)
        assertEquals(listOf(ModerationAction.NONE, ModerationAction.WARNING), service.detail(auth, reportId).allowedActions)
        authorize(AdminRole.ADMIN)
        assertEquals(ModerationAction.entries, service.detail(auth, reportId).allowedActions)

        `when`(roles.findByUserIdAndRevokedAtIsNull(subjectId))
            .thenReturn(AdminRoleAssignment(subjectId, AdminRole.MODERATOR, now))
        assertEquals(listOf(ModerationAction.NONE), service.detail(auth, reportId).allowedActions)
        authorize(AdminRole.SUPER_ADMIN)
        assertEquals(ModerationAction.entries, service.detail(auth, reportId).allowedActions)
    }

    @Test
    fun `admin cannot sanction another operator and rejection leaves report unchanged`() {
        authorize()
        val reportId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val report = UserReport(id = reportId, targetId = subjectId, subjectUserId = subjectId,
            status = ReportStatus.REVIEWING, version = 1)
        `when`(reports.findForUpdate(reportId)).thenReturn(report)
        `when`(roles.findByUserIdAndRevokedAtIsNull(subjectId))
            .thenReturn(AdminRoleAssignment(subjectId, AdminRole.MODERATOR, now))
        val request = DecideReportRequest(1, ReportStatus.RESOLVED, ModerationAction.WARNING, ModerationReason.SPAM, "경고 안내")

        val error = assertThrows(ApiException::class.java) {
            service.decide(auth, reportId, UUID.randomUUID().toString(), request)
        }

        assertEquals(ApiErrorCode.FORBIDDEN_RESOURCE, error.errorCode)
        assertEquals(ReportStatus.REVIEWING, report.status)
        verifyNoInteractions(sanctions, users, audits)
        verify(reports, never()).saveAndFlush(any(UserReport::class.java))
    }

    private fun authorize(role: AdminRole = AdminRole.ADMIN) {
        `when`(roles.findByUserIdAndRevokedAtIsNull(adminId)).thenReturn(AdminRoleAssignment(adminId, role, now))
    }
}
