package com.fitmate.backend.admin.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.admin.domain.AdminAuditLog
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.repository.AdminAuditLogRepository
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.ops.domain.IdempotencyKeyRecord
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

@Service
class AdminToolAccess(private val roles: AdminRoleRepository, private val keys: IdempotencyKeyRepository,
    private val audits: AdminAuditLogRepository, private val mapper: ObjectMapper, private val clock: Clock) {
    fun requireManager(user: AuthenticatedUser) {
        checkRole(roles.findByUserIdAndRevokedAtIsNull(user.userId)?.role)
    }
    private fun checkRole(role: AdminRole?) {
        if (role !in setOf(AdminRole.ADMIN, AdminRole.SUPER_ADMIN)) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
    }

    @Transactional
    fun <T : Any> command(user: AuthenticatedUser, key: UUID, scope: String, request: Any,
        resultType: Class<T>, perform: () -> T): T {
        // Serializing each actor's tool commands also makes simultaneous retries use one receipt.
        checkRole(roles.findActiveForUpdate(user.userId)?.role)
        val hash = MessageDigest.getInstance("SHA-256").digest((scope + "\n" + mapper.writeValueAsString(request)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val existing = keys.findByUserIdAndIdempotencyKey(user.userId, key)
        if (existing != null) {
            if (existing.requestHash != hash) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            return mapper.readValue(requireNotNull(existing.responseBody), resultType)
        }
        val result = perform()
        keys.save(IdempotencyKeyRecord(UUID.randomUUID(), user.userId, key, hash, 200,
            mapper.writeValueAsString(result), clock.instant().plusSeconds(86400)))
        return result
    }
    fun audit(user: AuthenticatedUser, action: String, targetType: String, target: UUID, summary: String = action) {
        audits.save(AdminAuditLog(UUID.randomUUID(), user.userId, action, targetType, target,
            summary = summary, occurredAt = clock.instant()))
    }
}
