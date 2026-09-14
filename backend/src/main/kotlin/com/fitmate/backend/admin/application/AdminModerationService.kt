package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.community.repository.CommunityPostCommentRepository
import com.fitmate.backend.community.repository.CommunityPostRepository
import com.fitmate.backend.dm.repository.DirectMessageRepository
import com.fitmate.backend.mate.repository.MatePostRepository
import com.fitmate.backend.reporting.domain.ReportTargetType
import com.fitmate.backend.reporting.domain.ReportStatus
import com.fitmate.backend.reporting.domain.UserReport
import com.fitmate.backend.reporting.repository.UserReportRepository
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import com.fitmate.backend.ops.domain.IdempotencyKeyRecord
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class AdminModerationService(
    private val roles: AdminRoleRepository, private val reports: UserReportRepository, private val users: UserRepository,
    private val profiles: UserProfileRepository, private val sanctions: AdminSanctionRepository, private val audits: AdminAuditLogRepository,
    private val idempotency: IdempotencyKeyRepository, private val sessions: WorkoutSessionRepository,
    private val analyses: InbodyAnalysisRepository, private val posts: CommunityPostRepository,
    private val comments: CommunityPostCommentRepository, private val messages: DirectMessageRepository,
    private val matePosts: MatePostRepository, private val clock: Clock,
) {
    fun access(user: AuthenticatedUser): AdminAccessResponse = roles.findByUserIdAndRevokedAtIsNull(user.userId)
        ?.let { AdminAccessResponse(true, it.role.name) } ?: AdminAccessResponse(false, null)

    fun dashboard(user: AuthenticatedUser): AdminDashboardResponse {
        authorize(user); val now = clock.instant(); val since = now.minus(1, ChronoUnit.DAYS)
        return AdminDashboardResponse(now, AdminMetric(reports.findAll().count { it.status in setOf(ReportStatus.RECEIVED, ReportStatus.REVIEWING) }.toLong()),
            AdminMetric(users.countByCreatedAtGreaterThanEqual(since)), AdminMetric(sessions.countByStatusAndEndedAtGreaterThanEqual(WorkoutSessionStatus.COMPLETED, since)),
            AdminMetric(analyses.countByStatusAndCreatedAtGreaterThanEqual(InbodyAnalysisStatus.FAILED, since)),
            reports.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 3)).content.map(::summary), audits.findTop3ByOrderByOccurredAtDesc().map { AdminAuditItem(it.action, it.summary, it.occurredAt) })
    }

    fun list(user: AuthenticatedUser, status: ReportStatus?, query: String?, cursor: String?, size: Int): AdminReportPage {
        authorize(user); val page = decodeCursor(cursor); val filtered = reports.findAllByOrderByCreatedAtDescIdDesc()
            .filter { status == null || it.status == status }
            .filter { report -> query.isNullOrBlank() || report.id.toString().contains(query, true) || report.reason.name.contains(query, true) || titleOf(report).contains(query, true) || subject(report)?.nickname?.contains(query, true) == true }
        val start = page * size; val items = filtered.drop(start).take(size).map(::summary); val hasNext = start + items.size < filtered.size
        return AdminReportPage(items, if (hasNext) encodeCursor(page + 1) else null, hasNext)
    }

    fun detail(user: AuthenticatedUser, reportId: UUID): AdminReportDetail { authorize(user); val report = find(reportId); return detailOf(report) }

    fun members(user: AuthenticatedUser, query: String?): AdminMemberPage {
        authorize(user)
        val items = reports.findAll().mapNotNull { report -> subject(report) }.distinctBy { it.id }
            .filter { query.isNullOrBlank() || it.nickname?.contains(query, true) == true || it.id.toString().contains(query, true) }
        return AdminMemberPage(items)
    }

    fun contents(user: AuthenticatedUser): AdminContentPage {
        authorize(user)
        val items = reports.findAll().asSequence().filter { it.targetType in setOf(ReportTargetType.POST, ReportTargetType.COMMENT, ReportTargetType.MATE_POST) }
            .sortedByDescending { it.createdAt }.map { AdminContentItem(requireNotNull(it.id), it.targetType, requireNotNull(it.targetId), titleOf(it), it.status, it.createdAt) }.toList()
        return AdminContentPage(items)
    }

    @Transactional
    fun claim(user: AuthenticatedUser, reportId: UUID, request: ClaimReportRequest): AdminReportDetail {
        authorize(user); val report = findLocked(reportId); checkVersion(report, request.expectedVersion)
        if (report.status != ReportStatus.RECEIVED) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        report.status = ReportStatus.REVIEWING; report.assignedOperatorId = user.userId; audit(user.userId, report, "REPORT_CLAIMED", "신고 검토를 시작했습니다.")
        reports.saveAndFlush(report)
        return detailOf(report)
    }

    @Transactional
    fun decide(user: AuthenticatedUser, reportId: UUID, key: String, request: DecideReportRequest): AdminReportDetail {
        authorize(user)
        val idempotencyKey = runCatching { UUID.fromString(key) }.getOrElse { throw ApiException(ApiErrorCode.VALIDATION_ERROR, "Idempotency-Key는 UUID여야 합니다.") }
        val hash = hash("$reportId|$request"); val existing = idempotency.findByUserIdAndIdempotencyKey(user.userId, idempotencyKey)
        if (existing != null) { if (existing.requestHash != hash) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT); return detailOf(find(reportId)) }
        val report = findLocked(reportId); checkVersion(report, request.expectedVersion)
        if (report.status != ReportStatus.REVIEWING) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        validateDecision(request)
        report.status = request.resolution; report.decisionAction = request.action.name; report.decisionReasonCode = request.reasonCode.name
        report.publicReason = request.publicReason.trim(); report.internalNote = request.internalNote?.trim()?.takeIf(String::isNotEmpty); report.resolvedAt = clock.instant()
        if (request.action != ModerationAction.NONE && subjectIdOf(report) == user.userId) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE, "본인 계정에는 제재할 수 없습니다.")
        if (request.action == ModerationAction.SUSPEND) suspendSubject(user.userId, report, request)
        if (request.action == ModerationAction.WARNING) createSanction(user.userId, report, request, null)
        audit(user.userId, report, "REPORT_DECIDED", "${request.action.name} 조치를 완료했습니다.")
        idempotency.save(IdempotencyKeyRecord(UUID.randomUUID(), user.userId, idempotencyKey, hash, 200, null, clock.instant().plus(1, ChronoUnit.DAYS)))
        reports.saveAndFlush(report)
        return detailOf(report)
    }

    private fun suspendSubject(actor: UUID, report: UserReport, request: DecideReportRequest) {
        val subjectId = report.subjectUserId ?: subjectIdOf(report)?.also { report.subjectUserId = it } ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "제재 대상 회원이 없습니다.")
        val subject = users.findById(subjectId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        subject.accountStatus = AccountStatus.SUSPENDED; subject.authVersion += 1
        createSanction(actor, report, request, clock.instant().plus(request.durationDays!!.toLong(), ChronoUnit.DAYS))
    }
    private fun createSanction(actor: UUID, report: UserReport, request: DecideReportRequest, endsAt: Instant?) {
        val subjectId = report.subjectUserId ?: subjectIdOf(report)?.also { report.subjectUserId = it }
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "제재 대상 회원이 없습니다.")
        sanctions.save(AdminSanction(UUID.randomUUID(), subjectId, report.id, request.action, request.reasonCode, request.publicReason.trim(), clock.instant(), endsAt, actor, clock.instant()))
    }
    private fun validateDecision(r: DecideReportRequest) {
        if (r.resolution == ReportStatus.RECEIVED || r.resolution == ReportStatus.REVIEWING) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.resolution == ReportStatus.DISMISSED && r.action != ModerationAction.NONE) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.resolution == ReportStatus.RESOLVED && r.action == ModerationAction.NONE) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.action == ModerationAction.SUSPEND && r.durationDays !in setOf(1, 3, 7, 30)) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.action != ModerationAction.SUSPEND && r.durationDays != null) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.action == ModerationAction.NONE && r.reasonCode !in setOf(ModerationReason.NO_VIOLATION, ModerationReason.INSUFFICIENT_EVIDENCE)) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        if (r.action != ModerationAction.NONE && r.reasonCode in setOf(ModerationReason.NO_VIOLATION, ModerationReason.INSUFFICIENT_EVIDENCE)) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
    }
    private fun detailOf(r: UserReport) = AdminReportDetail(summary(r), r.assignedOperatorId, AdminEvidence(if (r.detail == null) "UNAVAILABLE" else "AVAILABLE", r.detail), audits.findAllByReportIdOrderByOccurredAtDesc(requireNotNull(r.id)).map { AdminAuditItem(it.action, it.summary, it.occurredAt) }, if (r.status == ReportStatus.REVIEWING) ModerationAction.entries else emptyList())
    private fun summary(r: UserReport): AdminReportSummary { val id = requireNotNull(r.id); return AdminReportSummary(id, "R-${id.toString().take(8).uppercase()}", r.targetType, titleOf(r), r.reason, r.status, r.createdAt, r.version, subject(r)) }
    private fun subject(r: UserReport): AdminSubjectUser? = subjectIdOf(r)?.let { userId -> profiles.findById(userId).orElse(null)?.let { AdminSubjectUser(userId, it.nickname) } ?: AdminSubjectUser(userId, null) }
    private fun subjectIdOf(r: UserReport): UUID? = r.subjectUserId ?: when (r.targetType) {
        ReportTargetType.USER -> r.targetId
        ReportTargetType.POST -> r.targetId?.let { posts.findByIdAndDeletedAtIsNull(it)?.authorUserId }
        ReportTargetType.COMMENT -> r.targetId?.let { comments.findByIdAndDeletedAtIsNull(it)?.authorUserId }
        ReportTargetType.DM_MESSAGE -> r.targetId?.let { messages.findById(it).orElse(null)?.senderUserId }
        ReportTargetType.MATE_POST -> r.targetId?.let { matePosts.findById(it).orElse(null)?.authorId }
    }
    private fun titleOf(r: UserReport): String = when (r.targetType) {
        ReportTargetType.USER -> "회원 프로필 신고"
        ReportTargetType.POST -> r.targetId?.let { posts.findByIdAndDeletedAtIsNull(it)?.content?.lineSequence()?.firstOrNull()?.take(80) } ?: "삭제되었거나 없는 게시물"
        ReportTargetType.COMMENT -> r.targetId?.let { comments.findByIdAndDeletedAtIsNull(it)?.content?.take(80) } ?: "삭제되었거나 없는 댓글"
        ReportTargetType.DM_MESSAGE -> "신고된 DM 메시지"
        ReportTargetType.MATE_POST -> r.targetId?.let { matePosts.findById(it).orElse(null)?.title } ?: "삭제되었거나 없는 메이트 모집글"
    }
    private fun audit(actor: UUID, report: UserReport, action: String, summary: String) { audits.save(AdminAuditLog(UUID.randomUUID(), actor, action, "REPORT", requireNotNull(report.id), report.id, summary, clock.instant())) }
    private fun find(id: UUID) = reports.findById(id).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
    private fun findLocked(id: UUID) = reports.findForUpdate(id) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
    private fun checkVersion(r: UserReport, expected: Long) { if (r.version != expected) throw ApiException(ApiErrorCode.VERSION_CONFLICT) }
    private fun authorize(user: AuthenticatedUser): AdminRoleAssignment = roles.findByUserIdAndRevokedAtIsNull(user.userId) ?: throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
    private fun decodeCursor(value: String?) = value?.let { String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8).toIntOrNull() ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR) } ?: 0
    private fun encodeCursor(value: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toString().toByteArray(StandardCharsets.UTF_8))
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
