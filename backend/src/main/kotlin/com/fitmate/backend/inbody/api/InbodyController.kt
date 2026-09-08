package com.fitmate.backend.inbody.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.inbody.application.InbodyAnalysisService
import com.fitmate.backend.inbody.application.InbodyRecordService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/inbody-analyses")
class InbodyAnalysisController(private val analysisService: InbodyAnalysisService) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateInbodyAnalysisRequest,
    ): ResponseEntity<ApiResponse<InbodyAnalysisResponse>> = ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(analysisService.create(user.userId, requireNotNull(request.mediaId))))

    @GetMapping("/{analysisId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable analysisId: UUID,
    ): ApiResponse<InbodyAnalysisResponse> = ApiResponse.success(analysisService.get(user.userId, analysisId))

    @PostMapping("/{analysisId}/retry")
    fun retry(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable analysisId: UUID,
    ): ResponseEntity<ApiResponse<InbodyAnalysisResponse>> = ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(analysisService.retry(user.userId, analysisId)))
}

@RestController
@RequestMapping("/api/v1/inbody-records")
class InbodyRecordController(private val recordService: InbodyRecordService) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: SaveInbodyRecordRequest,
    ): ResponseEntity<ApiResponse<InbodyRecordResponse>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(recordService.create(user.userId, request)))

    @GetMapping
    fun list(@AuthenticationPrincipal user: AuthenticatedUser): ApiResponse<ItemListResponse<InbodyRecordResponse>> =
        ApiResponse.success(ItemListResponse(recordService.list(user.userId)))

    @GetMapping("/{recordId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable recordId: UUID,
    ): ApiResponse<InbodyRecordResponse> = ApiResponse.success(recordService.get(user.userId, recordId))

    @DeleteMapping("/{recordId}")
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable recordId: UUID,
    ): ResponseEntity<Void> {
        recordService.delete(user.userId, recordId)
        return ResponseEntity.noContent().build()
    }
}
