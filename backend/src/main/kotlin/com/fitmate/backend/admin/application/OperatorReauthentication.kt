package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import jakarta.persistence.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Entity @Table(name = "ops_operator_auth_attempts")
class OperatorAuthAttempts(@Id @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    var failures: Int = 0, @Column(name = "blocked_until") var blockedUntil: Instant? = null)
@Entity @Table(name = "ops_operator_reauth")
class OperatorReauthReceipt(@Id @Column(name = "token_hash", length = 64) var tokenHash: String = "",
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "session_id", columnDefinition = "BINARY(16)") var sessionId: UUID? = null,
    @Column(name = "auth_version") var authVersion: Long = 0,
    @Column(name = "expires_at") var expiresAt: Instant = Instant.EPOCH)
interface OperatorAuthAttemptsRepository : JpaRepository<OperatorAuthAttempts, UUID>
interface OperatorReauthRepository : JpaRepository<OperatorReauthReceipt, String>
data class OperatorReauthResult(val token: String, val expiresAt: Instant, val mode: String = "DEMO_CODE")

/** Explicitly enabled local/test convenience; not production MFA. */
@Service
class OperatorReauthentication(private val roles: AdminRoleRepository,
    private val attempts: OperatorAuthAttemptsRepository, private val receipts: OperatorReauthRepository,
    private val environment: Environment, private val clock: Clock,
    @Value("\${fitmate.admin.demo-auth-enabled:false}") private val enabled: Boolean,
    @Value("\${fitmate.admin.demo-code:}") private val demoCode: String) {
    private fun checkConfiguration() {
        val profiles = environment.activeProfiles.ifEmpty { environment.defaultProfiles }
        if (!enabled || !demoCode.matches(Regex("[0-9]{6}")) || profiles.any { it !in setOf("local", "test") }) {
            throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE, "이 환경에서는 테스트 인증을 사용할 수 없습니다.")
        }
    }
    @Transactional(noRollbackFor = [ApiException::class])
    fun authenticate(user: AuthenticatedUser, code: String): OperatorReauthResult {
        checkConfiguration()
        if (roles.findActiveForUpdate(user.userId)?.role != AdminRole.SUPER_ADMIN) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
        val now = clock.instant()
        val state = attempts.findById(user.userId).orElseGet { OperatorAuthAttempts(user.userId) }
        if (state.blockedUntil?.isAfter(now) == true) throw ApiException(ApiErrorCode.OPERATOR_AUTH_LOCKED)
        if (state.blockedUntil != null) { state.failures = 0; state.blockedUntil = null }
        if (!MessageDigest.isEqual(code.toByteArray(), demoCode.toByteArray())) {
            state.failures++
            if (state.failures >= 5) state.blockedUntil = now.plusSeconds(300)
            attempts.saveAndFlush(state)
            throw ApiException(ApiErrorCode.OPERATOR_CODE_INVALID)
        }
        state.failures = 0; state.blockedUntil = null; attempts.save(state)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val expiresAt = now.plusSeconds(300)
        receipts.save(OperatorReauthReceipt(hash(token), user.userId, user.sessionId, user.authVersion, expiresAt))
        return OperatorReauthResult(token, expiresAt)
    }
    fun requireRecent(user: AuthenticatedUser, token: String) {
        checkConfiguration()
        val receipt = receipts.findById(hash(token)).orElse(null)
        if (receipt == null || receipt.userId != user.userId || receipt.sessionId != user.sessionId ||
            receipt.authVersion != user.authVersion || !receipt.expiresAt.isAfter(clock.instant())) {
            throw ApiException(ApiErrorCode.OPERATOR_REAUTH_REQUIRED)
        }
    }
    private fun hash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
