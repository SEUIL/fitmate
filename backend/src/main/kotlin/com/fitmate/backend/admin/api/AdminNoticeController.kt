package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminNoticeService
import com.fitmate.backend.admin.domain.NoticeStatus
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class NoticeEdit(@field:NotBlank @field:Size(max = 100) val title: String,
    @field:NotBlank @field:Size(max = 5000) val body: String,
    @param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long)
data class NoticeVersion(@param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long)
data class NoticeSummary(val id: UUID, val title: String, val status: NoticeStatus, val publishedAt: Instant?, val version: Long)
data class NoticeDetail(val id: UUID, val title: String, val body: String, val status: NoticeStatus,
    val publishedAt: Instant?, val endedAt: Instant?, val version: Long, val targetType: String = "ALL")
data class NoticePage(val items: List<NoticeSummary>, val nextCursor: String?, val hasNext: Boolean)

@RestController
@Validated
@RequestMapping("/api/v1/admin/notices")
class AdminNoticeController(private val service: AdminNoticeService) {
    @GetMapping fun list(@AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) cursor: String?, @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int) =
        ApiResponse.success(service.list(user, cursor, size))
    @GetMapping("/{id}") fun detail(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID) = ApiResponse.success(service.detail(user, id))
    @PostMapping fun create(@AuthenticationPrincipal user: AuthenticatedUser, @RequestHeader("Idempotency-Key") key: UUID,
        @Valid @RequestBody request: NoticeEdit) = ApiResponse.success(service.create(user, key, request))
    @PutMapping("/{id}") fun edit(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: NoticeEdit) = ApiResponse.success(service.edit(user, id, key, request))
    @PostMapping("/{id}/publish") fun publish(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: NoticeVersion) = ApiResponse.success(service.transition(user, id, key, request, NoticeStatus.PUBLISHED))
    @PostMapping("/{id}/end") fun end(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: NoticeVersion) = ApiResponse.success(service.transition(user, id, key, request, NoticeStatus.ENDED))
}

@RestController
@Validated
@RequestMapping("/api/v1/notices")
class UserNoticeController(private val service: AdminNoticeService) {
    @GetMapping fun list(@RequestParam(required = false) cursor: String?, @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int) =
        ApiResponse.success(service.publicList(cursor, size))
    @GetMapping("/{id}") fun detail(@PathVariable id: UUID) = ApiResponse.success(service.publicDetail(id))
}
