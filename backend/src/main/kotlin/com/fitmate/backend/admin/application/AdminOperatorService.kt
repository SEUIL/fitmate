package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.repository.AdminOperatorSettingsRepository
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.AuthSessionRepository
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.repository.UserProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class AdminOperatorService(private val roles: AdminRoleRepository, private val users: UserRepository,
    private val profiles: UserProfileRepository, private val sessions: AuthSessionRepository,
    private val settings: AdminOperatorSettingsRepository, private val access: AdminToolAccess,
    private val reauth: OperatorReauthentication, private val clock: Clock) {
    private fun requireSuper(user: AuthenticatedUser) {
        if (roles.findByUserIdAndRevokedAtIsNull(user.userId)?.role != AdminRole.SUPER_ADMIN) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
    }
    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, cursor: String?): OperatorCatalog {
        requireSuper(user)
        val page = try { cursor?.let { String(Base64.getUrlDecoder().decode(it)).toInt().also { n -> require(n in 0..1000000) } } ?: 0 }
            catch (_: IllegalArgumentException) { throw ApiException(ApiErrorCode.VALIDATION_ERROR) }
        val rows = roles.findAllByOrderByGrantedAtDescUserIdDesc(PageRequest.of(page, 20))
        return OperatorCatalog(settings.findById(1).orElseThrow().version, rows.content.map(::summary),
            if (rows.hasNext()) Base64.getUrlEncoder().withoutPadding().encodeToString((page + 1).toString().toByteArray()) else null, user.userId)
    }
    @Transactional(readOnly = true)
    fun candidate(user: AuthenticatedUser, id: UUID): OperatorCandidate {
        requireSuper(user)
        val target = users.findById(id).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (target.accountStatus == AccountStatus.WITHDRAWN) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val role = roles.findById(id).orElse(null)
        return OperatorCandidate(id, profiles.findById(id).orElse(null)?.nickname, target.accountStatus, role?.role, role?.revokedAt == null && role != null)
    }
    @Transactional
    fun change(user: AuthenticatedUser, targetId: UUID, token: String, key: UUID, request: OperatorChange): OperatorChangeResult {
        // Lock before checking the actor: two operators cannot revoke each other concurrently.
        val catalog = settings.lockCatalog() ?: error("Missing operator settings")
        requireSuper(user)
        reauth.requireRecent(user, token)
        return access.command(user, key, "operator:$targetId", request, OperatorChangeResult::class.java) {
            if (request.reason.isBlank()) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
            if (targetId == user.userId) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE, "본인의 운영자 권한은 변경할 수 없습니다.")
            if (catalog.version != request.expectedVersion) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            val existing = roles.findForUpdate(targetId)
            val target = users.findForOperatorUpdate(targetId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            if (request.enabled && target.accountStatus != AccountStatus.ACTIVE) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "활성 회원에게만 운영자 권한을 부여할 수 있습니다.")
            if (!request.enabled && (existing == null || existing.revokedAt != null)) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            if (existing?.role == AdminRole.SUPER_ADMIN && existing.revokedAt == null && (!request.enabled || request.role != AdminRole.SUPER_ADMIN) &&
                roles.countByRoleAndRevokedAtIsNull(AdminRole.SUPER_ADMIN) <= 1) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "마지막 최고 관리자는 유지해야 합니다.")
            }
            if (existing != null && existing.revokedAt == null && request.enabled && existing.role == request.role) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 부여된 역할입니다.")
            }
            val now = clock.instant()
            val assignment = existing ?: AdminRoleAssignment(targetId)
            val previous = existing?.takeIf { it.revokedAt == null }?.role?.name ?: "NONE"
            if (request.enabled) { assignment.role = request.role; assignment.grantedAt = now; assignment.revokedAt = null }
            else assignment.revokedAt = now
            roles.saveAndFlush(assignment)
            target.authVersion++
            sessions.findAllByUserIdAndRevokedAtIsNull(targetId).forEach { it.revokedAt = now; it.revokeReason = "OPERATOR_ROLE_CHANGED" }
            catalog.version++
            settings.saveAndFlush(catalog)
            access.audit(user, "OPERATOR_ROLE_CHANGED", "OPERATOR", targetId,
                "$previous → ${if (request.enabled) request.role.name else "NONE"}: ${request.reason.trim()}")
            OperatorChangeResult(catalog.version, summary(assignment))
        }
    }
    private fun summary(role: AdminRoleAssignment): OperatorSummary {
        val id = requireNotNull(role.userId)
        val target = users.findById(id).orElseThrow()
        return OperatorSummary(id, profiles.findById(id).orElse(null)?.nickname, role.role, role.revokedAt == null,
            target.accountStatus, role.grantedAt, role.revokedAt)
    }
}
