package com.fitmate.backend.running.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.running.application.RunningRecordService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/running-records")
class RunningRecordController(
    private val service: RunningRecordService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateRunningRecordRequest,
    ): ApiResponse<RunningRecordResponse> = ApiResponse.success(service.create(user.userId, request))

    @GetMapping("/{recordId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable recordId: UUID,
    ): ApiResponse<RunningRecordResponse> = ApiResponse.success(service.get(user.userId, recordId))

    @DeleteMapping("/{recordId}")
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable recordId: UUID,
    ): ResponseEntity<Void> {
        service.delete(user.userId, recordId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(defaultValue = "0") cursor: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<CursorPageResponse<RunningRecordSummaryResponse>> =
        ApiResponse.success(service.list(user.userId, cursor, size))
}
