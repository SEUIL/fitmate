package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.domain.AppealAuthAttempts
import com.fitmate.backend.admin.domain.AppealSession
import com.fitmate.backend.admin.repository.AppealAuthAttemptsRepository
import com.fitmate.backend.admin.repository.AppealSessionRepository
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

data class AppealPrincipal(val userId: UUID)
data class AppealAccessResult(val token: String, val expiresAt: Instant)

@Service
class AppealAccessService(private val users: UserRepository, private val sessions: AppealSessionRepository,
    private val attempts: AppealAuthAttemptsRepository, private val passwords: PasswordEncoder, private val clock: Clock) {
    private val missingUserHash = passwords.encode(UUID.randomUUID().toString())
    @Transactional(noRollbackFor = [ApiException::class])
    fun authenticate(email: String, password: String): AppealAccessResult {
        val found = users.findByEmailNormalizedAndAccountStatusNot(email.trim().lowercase(Locale.ROOT), AccountStatus.WITHDRAWN)
        if (found == null) { passwords.matches(password, missingUserHash); throw ApiException(ApiErrorCode.INVALID_CREDENTIALS) }
        val user = users.findForOperatorUpdate(requireNotNull(found.id)) ?: throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        val now = clock.instant()
        val state = attempts.findById(requireNotNull(user.id)).orElseGet { AppealAuthAttempts(user.id) }
        if (state.blockedUntil?.isAfter(now) == true) throw ApiException(ApiErrorCode.APPEAL_AUTH_LOCKED)
        if (state.blockedUntil != null) { state.failures = 0; state.blockedUntil = null }
        if (!passwords.matches(password, user.passwordHash) || user.accountStatus == AccountStatus.WITHDRAWN) {
            state.failures++
            if (state.failures >= 5) state.blockedUntil = now.plusSeconds(300)
            attempts.saveAndFlush(state)
            throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        }
        state.failures = 0; state.blockedUntil = null; attempts.save(state)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val expires = now.plusSeconds(1800)
        sessions.save(AppealSession(hash(token), user.id, user.authVersion, expires))
        return AppealAccessResult(token, expires)
    }
    fun verify(token: String): AppealPrincipal {
        val session = sessions.findById(hash(token)).orElse(null) ?: throw ApiException(ApiErrorCode.INVALID_ACCESS_TOKEN)
        val user = users.findById(requireNotNull(session.userId)).orElse(null) ?: throw ApiException(ApiErrorCode.INVALID_ACCESS_TOKEN)
        if (!session.expiresAt.isAfter(clock.instant()) || session.authVersion != user.authVersion || user.accountStatus == AccountStatus.WITHDRAWN) {
            throw ApiException(ApiErrorCode.INVALID_ACCESS_TOKEN)
        }
        return AppealPrincipal(requireNotNull(user.id))
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
