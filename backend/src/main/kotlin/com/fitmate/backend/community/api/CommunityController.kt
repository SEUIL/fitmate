package com.fitmate.backend.community.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.community.application.CommunityService
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/community")
class CommunityController(private val communityService: CommunityService) {
    @PostMapping("/posts")
    fun create(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: CreateCommunityPostRequest): ApiResponse<CommunityPostResponse> =
        ApiResponse.success(communityService.create(currentUser.userId, request))

    @GetMapping("/posts")
    fun feed(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestParam(defaultValue = "false") followingOnly: Boolean,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): ApiResponse<CursorPageResponse<CommunityPostResponse>> =
        ApiResponse.success(communityService.feed(currentUser.userId, followingOnly, limit, cursor))

    @GetMapping("/posts/{postId}")
    fun detail(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID): ApiResponse<CommunityPostResponse> =
        ApiResponse.success(communityService.detail(currentUser.userId, postId))

    @PutMapping("/posts/{postId}")
    fun update(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID, @Valid @RequestBody request: UpdateCommunityPostRequest): ApiResponse<CommunityPostResponse> =
        ApiResponse.success(communityService.update(currentUser.userId, postId, request))

    @DeleteMapping("/posts/{postId}")
    fun delete(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID): ResponseEntity<Void> {
        communityService.delete(currentUser.userId, postId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/posts/{postId}/likes")
    fun like(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID): ApiResponse<CommunityPostResponse> =
        ApiResponse.success(communityService.like(currentUser.userId, postId))

    @DeleteMapping("/posts/{postId}/likes")
    fun unlike(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID): ApiResponse<CommunityPostResponse> =
        ApiResponse.success(communityService.unlike(currentUser.userId, postId))

    @GetMapping("/posts/{postId}/comments")
    fun comments(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID): ApiResponse<ItemListResponse<CommunityCommentResponse>> =
        ApiResponse.success(ItemListResponse(communityService.comments(currentUser.userId, postId)))

    @PostMapping("/posts/{postId}/comments")
    fun addComment(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID, @Valid @RequestBody request: CreateCommunityCommentRequest): ApiResponse<CommunityCommentResponse> =
        ApiResponse.success(communityService.addComment(currentUser.userId, postId, request))

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    fun deleteComment(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID, @PathVariable commentId: UUID): ResponseEntity<Void> {
        communityService.deleteComment(currentUser.userId, postId, commentId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/posts/{postId}/media/{mediaId}")
    fun media(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable postId: UUID, @PathVariable mediaId: UUID): ResponseEntity<Resource> {
        val download = communityService.openPostMedia(currentUser.userId, postId, mediaId)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType)).body(download.resource)
    }
}
