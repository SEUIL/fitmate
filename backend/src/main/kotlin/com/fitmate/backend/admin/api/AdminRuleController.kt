package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminRuleService
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/v1/admin/rules")
class AdminRuleController(private val service: AdminRuleService) {
    @GetMapping("/{id}/history") fun history(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.history(user, id, cursor))
    @GetMapping fun list(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.list(user, cursor))
    @PutMapping("/{id}") fun save(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: RuleWrite) = ApiResponse.success(service.save(user, id, key, request))
    @PostMapping("/preview") fun preview(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: RulePreview) = ApiResponse.success(service.preview(user, request))
    @GetMapping("/detections") fun candidates(@AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(defaultValue = "false") reviewed: Boolean, @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.candidates(user, reviewed, cursor))
    @GetMapping("/detections/{id}") fun candidate(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.candidate(user, id))
    @PostMapping("/detections/{id}/review") fun review(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: DetectionReview) = ApiResponse.success(service.review(user, id, key, request))
}
