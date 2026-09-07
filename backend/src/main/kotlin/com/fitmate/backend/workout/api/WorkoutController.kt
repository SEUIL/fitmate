package com.fitmate.backend.workout.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.workout.application.WorkoutService
import com.fitmate.backend.report.api.WorkoutReportResponse
import com.fitmate.backend.report.application.WorkoutReportService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class WorkoutController(
    private val workoutService: WorkoutService,
    private val workoutReportService: WorkoutReportService,
) {
    @PostMapping("/workout-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateWorkoutSessionRequest,
    ): ApiResponse<WorkoutSessionResponse> = ApiResponse.success(workoutService.create(user.userId, request))

    @GetMapping("/workout-sessions/{sessionId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ): ApiResponse<WorkoutSessionResponse> = ApiResponse.success(workoutService.get(user.userId, sessionId))

    @GetMapping("/workout-sessions")
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) status: com.fitmate.backend.workout.domain.WorkoutSessionStatus?,
        @RequestParam(defaultValue = "0") cursor: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<CursorPageResponse<WorkoutSessionSummaryResponse>> =
        ApiResponse.success(workoutService.list(user.userId, status, cursor, size))

    @PostMapping("/workout-sessions/{sessionId}/{action:start|pause|resume|complete|cancel}")
    fun state(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
        @PathVariable action: String,
        @Valid @RequestBody request: WorkoutStateRequest,
    ): ApiResponse<WorkoutSessionResponse> = ApiResponse.success(
        workoutService.changeState(
            user.userId, sessionId, request.version, request.clientUpdatedAt, action, request.cancelReason,
        ),
    )

    @PutMapping("/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setNo}")
    fun upsertSet(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
        @PathVariable sessionExerciseId: UUID,
        @PathVariable setNo: Int,
        @Valid @RequestBody request: UpsertWorkoutSetRequest,
    ): ApiResponse<WorkoutSetResponse> = ApiResponse.success(
        workoutService.upsertSet(user.userId, sessionId, sessionExerciseId, setNo, request),
    )

    @GetMapping("/exercises/{exerciseId}/previous-record")
    fun previous(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable exerciseId: UUID,
        @RequestParam(required = false) beforeSessionId: UUID?,
    ): ApiResponse<PreviousExerciseRecordResponse?> = ApiResponse.success(
        workoutService.previous(user.userId, exerciseId, beforeSessionId),
    )

    @GetMapping("/workout-sessions/{sessionId}/report")
    fun report(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: UUID,
    ): ApiResponse<WorkoutReportResponse> = ApiResponse.success(workoutReportService.get(user.userId, sessionId))
}
