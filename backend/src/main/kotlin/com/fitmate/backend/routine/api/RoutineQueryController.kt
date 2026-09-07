package com.fitmate.backend.routine.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.routine.application.RoutineQueryService
import com.fitmate.backend.routine.application.RoutineCommandService
import com.fitmate.backend.routine.application.dto.RoutineDetail
import com.fitmate.backend.routine.application.dto.RoutineSummary
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/routines")
class RoutineQueryController(
    private val routineQueryService: RoutineQueryService,
    private val routineCommandService: RoutineCommandService,
) {
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @Valid @RequestBody request: CreateRoutineRequest,
    ): ApiResponse<RoutineDetail> = ApiResponse.success(routineCommandService.create(currentUser.userId, request))

    @PatchMapping("/{routineId}")
    fun update(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @PathVariable routineId: UUID,
        @Valid @RequestBody request: UpdateRoutineRequest,
    ): ApiResponse<RoutineDetail> = ApiResponse.success(routineCommandService.update(currentUser.userId, routineId, request))

    @DeleteMapping("/{routineId}")
    fun delete(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @PathVariable routineId: UUID,
        @RequestParam version: Long,
    ): ResponseEntity<Void> {
        routineCommandService.delete(currentUser.userId, routineId, version)
        return ResponseEntity.noContent().build()
    }
}
