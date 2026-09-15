package com.fitmate.backend.recommendation.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.recommendation.application.WorkoutPlanService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workout-recommendations/plans")
class WorkoutPlanController(private val service: WorkoutPlanService) {
    @GetMapping("/options")
    fun options(@AuthenticationPrincipal user: AuthenticatedUser) = ApiResponse.success(service.options(user.userId))
    @PostMapping
    fun create(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: CreateWorkoutPlanRequest) = ApiResponse.success(service.create(user.userId, request))
    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.get(user.userId, id))
    @PostMapping("/{id}/cancel")
    fun cancel(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.cancel(user.userId, id))
    @GetMapping("/{id}/alternatives")
    fun alternatives(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID, @RequestParam day: Int, @RequestParam exerciseId: UUID) =
        ApiResponse.success(ItemListResponse(service.alternatives(user.userId, id, day, exerciseId)))
    @PostMapping("/{id}/replace")
    fun replace(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID, @Valid @RequestBody request: ReplacePlanExerciseRequest) = ApiResponse.success(service.replace(user.userId, id, request))
    @PostMapping("/{id}/save")
    fun save(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID, @Valid @RequestBody request: PlanVersionRequest) = ApiResponse.success(service.save(user.userId, id, request.version))
}
