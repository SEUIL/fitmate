package com.fitmate.backend.admin.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AppealStatus { RECEIVED, REVIEWING, ACCEPTED, REJECTED }
@Entity @Table(name = "ops_appeals")
class SanctionAppeal(
    @Id @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "sanction_id", columnDefinition = "BINARY(16)") var sanctionId: UUID? = null,
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(length = 2000) var statement: String = "",
    @Enumerated(EnumType.STRING) var status: AppealStatus = AppealStatus.RECEIVED,
    @Version var version: Long = 0,
    @Column(name = "public_reason", length = 500) var publicReason: String? = null,
    @Column(name = "internal_note", length = 500) var internalNote: String? = null,
    @Column(name = "reviewer_id", columnDefinition = "BINARY(16)") var reviewerId: UUID? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
) : BaseTimeEntity()
@Entity @Table(name = "ops_appeal_sessions")
class AppealSession(@Id @Column(name = "token_hash", length = 64) var tokenHash: String = "",
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "auth_version") var authVersion: Long = 0,
    @Column(name = "expires_at") var expiresAt: Instant = Instant.EPOCH)
@Entity @Table(name = "ops_appeal_auth_attempts")
class AppealAuthAttempts(@Id @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    var failures: Int = 0, @Column(name = "blocked_until") var blockedUntil: Instant? = null)
