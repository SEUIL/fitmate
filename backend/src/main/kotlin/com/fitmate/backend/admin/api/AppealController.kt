package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.*
import com.fitmate.backend.admin.domain.AppealStatus
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class AppealController(private val access: AppealAccessService, private val service: AppealService) {
    @PostMapping("/api/v1/auth/appeal-sessions") fun login(@Valid @RequestBody request: AppealLogin) = ApiResponse.success(access.authenticate(request.email, request.password))
    @GetMapping("/api/v1/appeal-access/sanctions") fun sanctions(@AuthenticationPrincipal user: AppealPrincipal,
        @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.ownSanctions(user, cursor))
    @PostMapping("/api/v1/appeal-access/appeals") fun create(@AuthenticationPrincipal user: AppealPrincipal,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: AppealCreate) = ApiResponse.success(service.create(user, key, request))
    @GetMapping("/api/v1/appeal-access/appeals") fun ownAppeals(@AuthenticationPrincipal user: AppealPrincipal,
        @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.ownAppeals(user, cursor))
    @GetMapping("/api/v1/admin/appeals") fun list(@AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) status: AppealStatus?, @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.list(user, status, cursor))
    @GetMapping("/api/v1/admin/appeals/{id}") fun detail(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.detail(user, id))
    @PostMapping("/api/v1/admin/appeals/{id}/claim") fun claim(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @Valid @RequestBody request: AppealVersion) = ApiResponse.success(service.claim(user, id, request))
    @PostMapping("/api/v1/admin/appeals/{id}/decision") fun decide(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: AppealDecision) = ApiResponse.success(service.decide(user, id, key, request))
}
