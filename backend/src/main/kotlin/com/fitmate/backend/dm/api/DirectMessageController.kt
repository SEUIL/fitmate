package com.fitmate.backend.dm.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.dm.application.DirectMessageService
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/dm")
class DirectMessageController(private val directMessages: DirectMessageService) {
    @PostMapping("/conversations")
    fun start(@AuthenticationPrincipal user: AuthenticatedUser, @Valid @RequestBody request: CreateConversationRequest): ApiResponse<ConversationResponse> = ApiResponse.success(directMessages.start(user.userId, request))
    @GetMapping("/conversations")
    fun conversations(@AuthenticationPrincipal user: AuthenticatedUser): ApiResponse<ItemListResponse<ConversationResponse>> = ApiResponse.success(ItemListResponse(directMessages.conversations(user.userId)))
    @GetMapping("/requests")
    fun requests(@AuthenticationPrincipal user: AuthenticatedUser): ApiResponse<ItemListResponse<ConversationResponse>> = ApiResponse.success(ItemListResponse(directMessages.pendingRequests(user.userId)))
    @PostMapping("/conversations/{conversationId}/accept")
    fun accept(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable conversationId: UUID): ApiResponse<ConversationResponse> = ApiResponse.success(directMessages.respond(user.userId, conversationId, true))
    @PostMapping("/conversations/{conversationId}/reject")
    fun reject(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable conversationId: UUID): ApiResponse<ConversationResponse> = ApiResponse.success(directMessages.respond(user.userId, conversationId, false))
    @GetMapping("/conversations/{conversationId}/messages")
    fun messages(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable conversationId: UUID): ApiResponse<ItemListResponse<DirectMessageResponse>> = ApiResponse.success(ItemListResponse(directMessages.messages(user.userId, conversationId)))
    @PostMapping("/conversations/{conversationId}/messages")
    fun send(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable conversationId: UUID, @Valid @RequestBody request: SendDirectMessageRequest): ApiResponse<DirectMessageResponse> = ApiResponse.success(directMessages.send(user.userId, conversationId, request))
    @GetMapping("/conversations/{conversationId}/media/{mediaId}")
    fun media(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable conversationId: UUID, @PathVariable mediaId: UUID): ResponseEntity<Resource> {
        val download = directMessages.openMedia(user.userId, conversationId, mediaId)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType)).body(download.resource)
    }
}
