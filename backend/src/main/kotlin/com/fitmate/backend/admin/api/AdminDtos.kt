package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.domain.ModerationAction
import com.fitmate.backend.admin.domain.ModerationReason
import com.fitmate.backend.reporting.domain.ReportReason
import com.fitmate.backend.reporting.domain.ReportStatus
import com.fitmate.backend.reporting.domain.ReportTargetType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class AdminAccessResponse(val eligible: Boolean, val role: String?)
data class AdminMetric(val value: Long)
data class AdminDashboardResponse(val generatedAt: Instant, val pendingReports: AdminMetric, val newUsers: AdminMetric, val completedWorkouts: AdminMetric, val failedAnalyses: AdminMetric, val recentReports: List<AdminReportSummary>, val recentHistory: List<AdminAuditItem>)
data class AdminReportSummary(val id: UUID, val displayId: String, val targetType: ReportTargetType, val title: String, val reporterReason: ReportReason, val status: ReportStatus, val createdAt: Instant, val version: Long, val subjectUser: AdminSubjectUser?)
data class AdminSubjectUser(val id: UUID, val nickname: String?)
data class AdminReportPage(val items: List<AdminReportSummary>, val nextCursor: String?, val hasNext: Boolean)
data class AdminMemberPage(val items: List<AdminSubjectUser>)
data class AdminContentItem(val reportId: UUID, val targetType: ReportTargetType, val targetId: UUID, val title: String, val status: ReportStatus, val createdAt: Instant)
data class AdminContentPage(val items: List<AdminContentItem>)
data class AdminEvidence(val availability: String, val text: String?, val reporterStatement: String? = null)
data class AdminDecisionSummary(val action: String, val reasonCode: String?, val publicReason: String?, val internalNote: String?, val decidedAt: Instant?, val sanctionEndsAt: Instant?)
data class AdminAuditItem(val action: String, val summary: String, val occurredAt: Instant)
data class AdminReportDetail(val report: AdminReportSummary, val assignedOperatorId: UUID?, val evidence: AdminEvidence, val history: List<AdminAuditItem>, val allowedActions: List<ModerationAction>, val decision: AdminDecisionSummary? = null)
data class ClaimReportRequest(@field:NotNull @field:Min(0) val expectedVersion: Long)
data class DecideReportRequest(
    @field:NotNull @field:Min(0) val expectedVersion: Long,
    @field:NotNull val resolution: ReportStatus,
    @field:NotNull val action: ModerationAction,
    @field:NotNull val reasonCode: ModerationReason,
    @field:NotBlank @field:Size(max = 500) val publicReason: String,
    @field:Size(max = 500) val internalNote: String? = null,
    @field:Min(1) @field:Max(30) val durationDays: Int? = null,
)
