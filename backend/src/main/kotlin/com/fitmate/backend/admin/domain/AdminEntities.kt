package com.fitmate.backend.admin.domain

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

enum class AdminRole { MODERATOR, ADMIN, SUPER_ADMIN }
enum class ModerationAction { NONE, WARNING, SUSPEND }
enum class ModerationReason { NO_VIOLATION, INSUFFICIENT_EVIDENCE, POLICY_VIOLATION, SPAM, HARASSMENT }

@Entity
@Table(name = "ops_admin_roles")
class AdminRoleAssignment(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var role: AdminRole = AdminRole.MODERATOR,
    @Column(name = "granted_at", nullable = false) var grantedAt: Instant = Instant.EPOCH,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
)

@Entity
@Table(name = "ops_sanctions")
class AdminSanction(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "report_id", nullable = false, columnDefinition = "BINARY(16)") var reportId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var action: ModerationAction = ModerationAction.WARNING,
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 32) var reasonCode: ModerationReason = ModerationReason.POLICY_VIOLATION,
    @Column(name = "public_reason", nullable = false, length = 500) var publicReason: String = "",
    @Column(name = "starts_at", nullable = false) var startsAt: Instant = Instant.EPOCH,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)") var createdBy: UUID? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "ops_audit_logs")
class AdminAuditLog(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "actor_user_id", nullable = false, columnDefinition = "BINARY(16)") var actorUserId: UUID? = null,
    @Column(nullable = false, length = 32) var action: String = "",
    @Column(name = "target_type", nullable = false, length = 32) var targetType: String = "REPORT",
    @Column(name = "target_id", nullable = false, columnDefinition = "BINARY(16)") var targetId: UUID? = null,
    @Column(name = "report_id", columnDefinition = "BINARY(16)") var reportId: UUID? = null,
    @Column(nullable = false, length = 500) var summary: String = "",
    @Column(name = "occurred_at", nullable = false) var occurredAt: Instant = Instant.EPOCH,
)
