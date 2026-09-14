package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminModerationService
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.reporting.domain.ReportStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Validated
@RequestMapping("/api/v1/admin")
class AdminController(private val service: AdminModerationService) {
    @GetMapping("/access") fun access(@AuthenticationPrincipal user: AuthenticatedUser) = ApiResponse.success(service.access(user))
    @GetMapping("/dashboard") fun dashboard(@AuthenticationPrincipal user: AuthenticatedUser) = ApiResponse.success(service.dashboard(user))
    @GetMapping("/reports") fun reports(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) status: ReportStatus?, @RequestParam(required = false) query: String?, @RequestParam(required = false) cursor: String?, @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int) = ApiResponse.success(service.list(user, status, query, cursor, size))
    @GetMapping("/reports/{reportId}") fun detail(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable reportId: UUID) = ApiResponse.success(service.detail(user, reportId))
    @GetMapping("/members") fun members(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) query: String?) = ApiResponse.success(service.members(user, query))
    @GetMapping("/contents") fun contents(@AuthenticationPrincipal user: AuthenticatedUser) = ApiResponse.success(service.contents(user))
    @PostMapping("/reports/{reportId}/claim") fun claim(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable reportId: UUID, @Valid @RequestBody request: ClaimReportRequest) = ApiResponse.success(service.claim(user, reportId, request))
    @PostMapping("/reports/{reportId}/decisions") fun decide(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable reportId: UUID, @RequestHeader("Idempotency-Key") key: String, @Valid @RequestBody request: DecideReportRequest) = ApiResponse.success(service.decide(user, reportId, key, request))
}
