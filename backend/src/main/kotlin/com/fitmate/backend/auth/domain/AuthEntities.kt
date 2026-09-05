package com.fitmate.backend.auth.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class AccountStatus { ACTIVE, SUSPENDED, BANNED, WITHDRAWN }
enum class AuthEventType { SIGNUP, LOGIN, REFRESH, LOGOUT, LOGOUT_ALL, TOKEN_REUSE, PASSWORD_CHANGED, WITHDRAWAL }

@Entity
@Table(name = "core_users")
class User(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "email", nullable = false, length = 320) var email: String = "",
    @Column(name = "email_normalized", nullable = false, length = 320) var emailNormalized: String = "",
    @Column(name = "password_hash", nullable = false, length = 100) var passwordHash: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "account_status", nullable = false, length = 16) var accountStatus: AccountStatus = AccountStatus.ACTIVE,
    @Column(name = "auth_version", nullable = false) var authVersion: Long = 0,
    @Column(name = "last_login_at") var lastLoginAt: Instant? = null,
    @Column(name = "withdrawn_at") var withdrawnAt: Instant? = null,
) : BaseTimeEntity()

@Entity
@Table(name = "core_auth_sessions")
class AuthSession(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "token_family_id", nullable = false, columnDefinition = "BINARY(16)") var tokenFamilyId: UUID? = null,
    @Column(name = "refresh_token_hash", nullable = false, length = 64) var refreshTokenHash: String = "",
    @Column(name = "device_id", length = 128) var deviceId: String? = null,
    @Column(name = "issued_at", nullable = false) var issuedAt: Instant = Instant.EPOCH,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "last_used_at") var lastUsedAt: Instant? = null,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "revoke_reason", length = 32) var revokeReason: String? = null,
    @Column(name = "replaced_by_session_id", columnDefinition = "BINARY(16)") var replacedBySessionId: UUID? = null,
) : BaseTimeEntity()

@Entity
@Table(name = "core_revoked_access_tokens")
class RevokedAccessToken(
    @Id @Column(name = "jti", length = 36) var jti: String = "",
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "session_id", columnDefinition = "BINARY(16)") var sessionId: UUID? = null,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "revoked_at", nullable = false) var revokedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "core_auth_events")
class AuthEvent(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "session_id", columnDefinition = "BINARY(16)") var sessionId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 32) var eventType: AuthEventType = AuthEventType.LOGIN,
    @Column(name = "user_agent_summary", length = 255) var userAgentSummary: String? = null,
    @Column(name = "occurred_at", nullable = false) var occurredAt: Instant = Instant.EPOCH,
)
