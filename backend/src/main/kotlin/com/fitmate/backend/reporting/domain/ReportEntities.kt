package com.fitmate.backend.reporting.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class ReportTargetType { USER, POST, COMMENT, DM_MESSAGE, MATE_POST }
enum class ReportReason { SPAM, HARASSMENT, HATE, SEXUAL, VIOLENCE, SELF_HARM, OTHER }
enum class ReportStatus { RECEIVED, REVIEWING, RESOLVED, DISMISSED }

@Entity
@Table(name = "user_reports")
class UserReport(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "reporter_user_id", columnDefinition = "BINARY(16)") var reporterUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false) var targetType: ReportTargetType = ReportTargetType.USER,
    @Column(name = "target_id", columnDefinition = "BINARY(16)") var targetId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var reason: ReportReason = ReportReason.OTHER,
    @Column(length = 1000) var detail: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ReportStatus = ReportStatus.RECEIVED,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "assigned_operator_id", columnDefinition = "BINARY(16)") var assignedOperatorId: UUID? = null,
    @Column(name = "decision_action", length = 16) var decisionAction: String? = null,
    @Column(name = "decision_reason_code", length = 32) var decisionReasonCode: String? = null,
    @Column(name = "public_reason", length = 500) var publicReason: String? = null,
    @Column(name = "internal_note", length = 500) var internalNote: String? = null,
    @Column(name = "subject_user_id", columnDefinition = "BINARY(16)") var subjectUserId: UUID? = null,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()
