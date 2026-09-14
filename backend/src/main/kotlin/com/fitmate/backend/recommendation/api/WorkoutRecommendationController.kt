package com.fitmate.backend.recommendation.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.recommendation.application.WorkoutRecommendationService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/workout-recommendations")
class WorkoutRecommendationController(
    private val service: WorkoutRecommendationService,
) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @Valid @RequestBody request: CreateWorkoutRecommendationRequest,
    ): ApiResponse<WorkoutRecommendationResponse> = ApiResponse.success(service.recommend(currentUser.userId, request))
}
