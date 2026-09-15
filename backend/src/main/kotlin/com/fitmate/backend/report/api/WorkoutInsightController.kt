package com.fitmate.backend.report.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.report.application.WorkoutInsightService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workout-sessions/{sessionId}/insights")
class WorkoutInsightController(private val service: WorkoutInsightService) {
    @GetMapping("/options")
    fun options(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable sessionId: UUID,
        @RequestParam(defaultValue = "4") historyWeeks: Int) = ApiResponse.success(service.options(user.userId, sessionId, historyWeeks))
    @GetMapping
    fun latest(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable sessionId: UUID) = ApiResponse.success(service.latest(user.userId, sessionId))
    @PostMapping
    fun create(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable sessionId: UUID, @Valid @RequestBody request: CreateWorkoutInsightRequest) = ApiResponse.success(service.create(user.userId, sessionId, request))
    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable sessionId: UUID, @PathVariable id: UUID) = ApiResponse.success(service.get(user.userId, sessionId, id))
    @PostMapping("/{id}/cancel")
    fun cancel(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable sessionId: UUID, @PathVariable id: UUID) = ApiResponse.success(service.cancel(user.userId, sessionId, id))
}
