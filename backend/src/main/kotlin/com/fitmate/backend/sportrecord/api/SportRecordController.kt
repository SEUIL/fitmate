package com.fitmate.backend.sportrecord.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.sportrecord.application.SportRecordService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sport-records")
class SportRecordController(private val service: SportRecordService) {
    @PostMapping
    fun create(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: CreateSportRecordRequest): org.springframework.http.ResponseEntity<ApiResponse<SportRecordResponse>> {
        val result = service.create(user.userId, request)
        return org.springframework.http.ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(ApiResponse.success(result.response))
    }
    @GetMapping("/{recordId}") fun get(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable recordId: UUID) = ApiResponse.success(service.get(user.userId, recordId))
    @GetMapping fun list(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) sportCode: SportCode?, @RequestParam(defaultValue = "0") cursor: Int, @RequestParam(defaultValue = "20") size: Int): ApiResponse<CursorPageResponse<SportRecordResponse>> = ApiResponse.success(service.list(user.userId, sportCode, cursor, size))
}
