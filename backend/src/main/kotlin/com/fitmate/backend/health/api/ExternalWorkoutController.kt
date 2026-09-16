package com.fitmate.backend.health.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.health.application.ExternalWorkoutService
import com.fitmate.backend.health.application.ExternalWorkoutAnalysisService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workout-sessions/{sessionId}/external-workout")
class ExternalWorkoutController(
    private val service: ExternalWorkoutService,
    private val analysis: ExternalWorkoutAnalysisService,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ): ApiResponse<ExternalWorkoutResponse?> = ApiResponse.success(service.get(user.userId, sessionId))

    @GetMapping("/analysis")
    fun analysis(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ): ApiResponse<ExternalWorkoutAnalysisResponse> = ApiResponse.success(analysis.get(user.userId, sessionId))

    @PutMapping
    fun put(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: PutExternalWorkoutRequest,
    ): ApiResponse<ExternalWorkoutResponse> = ApiResponse.success(service.put(user.userId, sessionId, request))

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ) { service.delete(user.userId, sessionId) }
}
