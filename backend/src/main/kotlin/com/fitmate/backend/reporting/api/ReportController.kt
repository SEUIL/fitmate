package com.fitmate.backend.reporting.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.reporting.domain.ReportReason
import com.fitmate.backend.reporting.domain.ReportTargetType
import com.fitmate.backend.reporting.domain.UserReport
import com.fitmate.backend.reporting.repository.UserReportRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class CreateReportRequest(val targetType: ReportTargetType, val targetId: UUID, val reason: ReportReason, @field:Size(max = 1000) val detail: String? = null)
data class ReportResponse(val reportId: UUID, val status: String, val createdAt: Instant)

@RestController
@RequestMapping("/api/v1/reports")
class ReportController(private val reports: UserReportRepository) {
    @PostMapping
    @Transactional
    fun create(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: CreateReportRequest): ApiResponse<ReportResponse> {
        if (reports.existsByReporterUserIdAndTargetTypeAndTargetId(user.userId, request.targetType, request.targetId)) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 신고한 대상입니다.")
        }
        val saved = reports.saveAndFlush(UserReport(UUID.randomUUID(), user.userId, request.targetType, request.targetId, request.reason, request.detail?.trim()))
        return ApiResponse.success(ReportResponse(requireNotNull(saved.id), saved.status.name, saved.createdAt))
    }
}
