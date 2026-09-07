package com.fitmate.backend.notification.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.notification.domain.UserNotification
import com.fitmate.backend.notification.repository.NotificationRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class NotificationResponse(val notificationId: UUID, val type: String, val actorUserId: UUID?, val resourceType: String?, val resourceId: UUID?, val body: String?, val readAt: Instant?, val createdAt: Instant)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notifications: NotificationRepository) {
    @GetMapping
    @Transactional(readOnly = true)
    fun list(@AuthenticationPrincipal user: AuthenticatedUser): ApiResponse<ItemListResponse<NotificationResponse>> = ApiResponse.success(
        ItemListResponse(notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(user.userId).map(::response)),
    )

    @PatchMapping("/{notificationId}/read")
    @Transactional
    fun read(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable notificationId: UUID): ApiResponse<NotificationResponse> {
        val notification = notifications.findById(notificationId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (notification.recipientUserId != user.userId) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        if (notification.readAt == null) notification.readAt = Instant.now()
        return ApiResponse.success(response(notifications.saveAndFlush(notification)))
    }

    @PatchMapping("/read-all")
    @Transactional
    fun readAll(@AuthenticationPrincipal user: AuthenticatedUser): ResponseEntity<Void> {
        val now = Instant.now()
        notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(user.userId).filter { it.readAt == null }.forEach { it.readAt = now }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    @Transactional
    fun deleteAll(@AuthenticationPrincipal user: AuthenticatedUser): ResponseEntity<Void> {
        notifications.deleteAllByRecipientUserId(user.userId)
        return ResponseEntity.noContent().build()
    }

    private fun response(item: UserNotification) = NotificationResponse(requireNotNull(item.id), item.type.name, item.actorUserId, item.resourceType, item.resourceId, item.body, item.readAt, item.createdAt)
}
