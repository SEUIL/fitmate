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
import java.time.Instant
import java.util.UUID

enum class ReportTargetType { USER, POST, COMMENT, DM_MESSAGE }
enum class ReportReason { SPAM, HARASSMENT, HATE, SEXUAL, VIOLENCE, SELF_HARM, OTHER }
enum class ReportStatus { RECEIVED, REVIEWING, RESOLVED, DISMISSED }

@Entity
@Table(name = "user_reports")
class UserReport(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "reporter_user_id", columnDefinition = "BINARY(16)") var reporterUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false) var targetType: ReportTargetType = ReportTargetType.USER,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "target_id", columnDefinition = "BINARY(16)") var targetId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var reason: ReportReason = ReportReason.OTHER,
    @Column(length = 1000) var detail: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ReportStatus = ReportStatus.RECEIVED,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
) : BaseTimeEntity()
