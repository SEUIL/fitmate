package com.fitmate.backend.health.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.health.application.ExternalWorkoutService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workout-sessions/{sessionId}/external-workout")
class ExternalWorkoutController(private val service: ExternalWorkoutService) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ): ApiResponse<ExternalWorkoutResponse?> = ApiResponse.success(service.get(user.userId, sessionId))

    @PutMapping
    fun put(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: PutExternalWorkoutRequest,
    ): ApiResponse<ExternalWorkoutResponse> = ApiResponse.success(service.put(user.userId, sessionId, request))
}
