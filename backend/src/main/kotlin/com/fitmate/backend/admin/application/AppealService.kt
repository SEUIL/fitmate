package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.ops.domain.IdempotencyKeyRecord
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class AppealService(private val appeals: SanctionAppealRepository, private val sanctions: AdminSanctionRepository,
    private val roles: AdminRoleRepository, private val users: UserRepository, private val profiles: UserProfileRepository,
    private val access: AdminToolAccess, private val suspension: AccountSuspensionService, private val keys: IdempotencyKeyRepository,
    private val mapper: ObjectMapper, private val clock: Clock) {
    private val openStatuses = listOf(AppealStatus.RECEIVED, AppealStatus.REVIEWING)
    @Transactional(readOnly = true)
    fun ownAppeals(user: AppealPrincipal, cursor: String?): AppealPage {
        val rows = appeals.findAllByUserIdOrderByCreatedAtDescIdDesc(user.userId, page(cursor))
        return AppealPage(rows.content.map { view(it, null) }, next(rows.hasNext(), rows.number))
    }
    @Transactional(readOnly = true)
    fun ownSanctions(user: AppealPrincipal, cursor: String?): AppealSanctionPage {
        val rows = sanctions.findAllByUserIdOrderByCreatedAtDescIdDesc(user.userId, page(cursor))
        return AppealSanctionPage(rows.content.map { sanction ->
            val latest = appeals.findFirstBySanctionIdOrderByCreatedAtDescIdDesc(requireNotNull(sanction.id))
            AppealSanction(requireNotNull(sanction.id), sanction.action, sanction.publicReason, sanction.startsAt, sanction.endsAt,
                sanction.revokedAt, sanction.revokedAt == null && latest?.status !in openStatuses, latest?.let { view(it, null) })
        }, next(rows.hasNext(), rows.number))
    }
    @Transactional
    fun create(user: AppealPrincipal, key: UUID, request: AppealCreate): AppealView {
        if (request.statement.isBlank()) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        // User lock serializes same-key submissions even when they reference different sanctions.
        users.findForOperatorUpdate(user.userId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(("appeal:create\n" + mapper.writeValueAsString(request)).toByteArray()).joinToString("") { "%02x".format(it) }
        keys.findByUserIdAndIdempotencyKey(user.userId, key)?.let {
            if (it.requestHash != hash) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            return mapper.readValue(requireNotNull(it.responseBody), AppealView::class.java)
        }
        val sanction = sanctions.findForUpdate(request.sanctionId)?.takeIf { it.userId == user.userId }
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        if (sanction.revokedAt != null || appeals.existsBySanctionIdAndStatusIn(request.sanctionId, openStatuses)) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val saved = appeals.saveAndFlush(SanctionAppeal(UUID.randomUUID(), request.sanctionId, user.userId, request.statement.trim()))
        val response = view(saved, null)
        keys.save(IdempotencyKeyRecord(UUID.randomUUID(), user.userId, key, hash, 200, mapper.writeValueAsString(response), clock.instant().plusSeconds(86400)))
        return response
    }
    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, status: AppealStatus?, cursor: String?): AppealPage {
        val role = requireOperator(user)
        val rows = if (status == null) appeals.findAllByOrderByCreatedAtDescIdDesc(page(cursor))
            else appeals.findAllByStatusOrderByCreatedAtDescIdDesc(status, page(cursor))
        return AppealPage(rows.content.map { view(it, user, role) }, next(rows.hasNext(), rows.number))
    }
    @Transactional
    fun detail(user: AuthenticatedUser, id: UUID): AppealView {
        val role = requireOperator(user)
        val appeal = appeals.findById(id).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        access.audit(user, "APPEAL_VIEWED", "APPEAL", id, "이의신청 상세를 열람했습니다.")
        return view(appeal, user, role)
    }
    @Transactional
    fun claim(user: AuthenticatedUser, id: UUID, request: AppealVersion): AppealView {
        val role = roles.findActiveForUpdate(user.userId)?.role ?: throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
        val appeal = appeals.findForUpdate(id) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        requireReviewer(user, role, appeal)
        if (appeal.version != request.expectedVersion) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        if (appeal.status != AppealStatus.RECEIVED) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        appeal.status = AppealStatus.REVIEWING; appeal.reviewerId = user.userId
        appeals.saveAndFlush(appeal)
        access.audit(user, "APPEAL_CLAIMED", "APPEAL", id, "이의신청 검토를 시작했습니다.")
        return view(appeal, user, role)
    }
    fun decide(user: AuthenticatedUser, id: UUID, key: UUID, request: AppealDecision): AppealView =
        access.command(user, key, "appeal:$id:decision", request, AppealView::class.java) {
            val role = requireOperator(user)
            val appeal = appeals.findForUpdate(id) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            requireReviewer(user, role, appeal)
            if (appeal.version != request.expectedVersion) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            if (appeal.status != AppealStatus.REVIEWING) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            if (request.status !in listOf(AppealStatus.ACCEPTED, AppealStatus.REJECTED) || request.publicReason.isBlank()) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
            val target = users.findForOperatorUpdate(requireNotNull(appeal.userId)) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            val sanction = sanctions.findForUpdate(requireNotNull(appeal.sanctionId)) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            if (request.status == AppealStatus.ACCEPTED) {
                if (sanction.revokedAt != null) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 해제된 제재입니다.")
                sanction.revokedAt = clock.instant(); sanction.revokedBy = user.userId; sanction.revocationReason = request.publicReason.trim()
                sanctions.saveAndFlush(sanction)
                suspension.restoreIfExpired(target)
            }
            appeal.status = request.status; appeal.publicReason = request.publicReason.trim(); appeal.internalNote = request.internalNote?.trim()?.takeIf { it.isNotEmpty() }
            appeal.reviewerId = user.userId; appeal.reviewedAt = clock.instant()
            appeals.saveAndFlush(appeal)
            access.audit(user, "APPEAL_DECIDED", "APPEAL", id, "${request.status.name}: ${request.publicReason.trim().take(450)}")
            view(appeal, user, role)
        }
    private fun requireOperator(user: AuthenticatedUser) = roles.findByUserIdAndRevokedAtIsNull(user.userId)?.role ?: throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
    private fun canReview(user: AuthenticatedUser?, role: AdminRole?, appeal: SanctionAppeal): Boolean = user != null && user.userId != appeal.userId &&
        role in listOf(AdminRole.ADMIN, AdminRole.SUPER_ADMIN) && (role == AdminRole.SUPER_ADMIN || roles.findByUserIdAndRevokedAtIsNull(requireNotNull(appeal.userId)) == null)
    private fun requireReviewer(user: AuthenticatedUser, role: AdminRole, appeal: SanctionAppeal) {
        if (!canReview(user, role, appeal)) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
    }
    private fun view(a: SanctionAppeal, actor: AuthenticatedUser?, role: AdminRole? = null): AppealView {
        val sanction = sanctions.findById(requireNotNull(a.sanctionId)).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        return AppealView(requireNotNull(a.id), requireNotNull(a.sanctionId),
        requireNotNull(a.userId), profiles.findById(requireNotNull(a.userId)).orElse(null)?.nickname, a.statement, a.status, a.version, a.createdAt,
        a.publicReason, a.reviewedAt, if (actor == null) null else a.internalNote, canReview(actor, role, a),
        AppealSanctionDetail(sanction.action, sanction.publicReason, sanction.startsAt, sanction.endsAt, sanction.revokedAt))
    }
    private fun page(cursor: String?) = PageRequest.of(try {
        cursor?.let { String(Base64.getUrlDecoder().decode(it)).toInt().also { n -> require(n in 0..1000000) } } ?: 0
    } catch (_: IllegalArgumentException) { throw ApiException(ApiErrorCode.VALIDATION_ERROR) }, 20)
    private fun next(hasNext: Boolean, page: Int) = if (hasNext) Base64.getUrlEncoder().withoutPadding().encodeToString((page + 1).toString().toByteArray()) else null
}
