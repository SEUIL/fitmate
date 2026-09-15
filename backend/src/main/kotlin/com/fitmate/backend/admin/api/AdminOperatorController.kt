package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminOperatorService
import com.fitmate.backend.admin.application.OperatorReauthentication
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class OperatorCodeRequest(@field:Pattern(regexp = "[0-9]{6}") val code: String)

@RestController
@RequestMapping("/api/v1/admin/operators")
class AdminOperatorController(private val service: AdminOperatorService, private val reauth: OperatorReauthentication) {
    @GetMapping fun list(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) cursor: String?) = ApiResponse.success(service.list(user, cursor))
    @GetMapping("/candidates/{id}") fun candidate(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.candidate(user, id))
    @PostMapping("/reauth") fun authenticate(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: OperatorCodeRequest) = ApiResponse.success(reauth.authenticate(user, request.code))
    @PutMapping("/{id}") fun change(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @RequestHeader("X-Admin-Reauth") token: String,
        @Valid @RequestBody request: OperatorChange) = ApiResponse.success(service.change(user, id, token, key, request))
}
