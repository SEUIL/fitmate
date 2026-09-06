package com.fitmate.backend.routine.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.routine.application.RoutineQueryService
import com.fitmate.backend.routine.application.dto.RoutineDetail
import com.fitmate.backend.routine.application.dto.RoutineSummary
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/routines")
class RoutineQueryController(private val routineQueryService: RoutineQueryService) {
    @GetMapping("/recommended")
    fun recommended(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestParam sportCode: SportCode,
    ): ApiResponse<RoutineDetail> = ApiResponse.success(routineQueryService.getRecommended(currentUser.userId, sportCode))

    @GetMapping("/templates")
    fun templates(): ApiResponse<ItemListResponse<RoutineSummary>> =
        ApiResponse.success(ItemListResponse(routineQueryService.getTemplates()))

    @GetMapping
    fun mine(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<ItemListResponse<RoutineSummary>> =
        ApiResponse.success(ItemListResponse(routineQueryService.getMine(currentUser.userId)))

    @GetMapping("/{routineId}")
    fun detail(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @PathVariable routineId: UUID,
    ): ApiResponse<RoutineDetail> = ApiResponse.success(routineQueryService.getDetail(currentUser.userId, routineId))
}
