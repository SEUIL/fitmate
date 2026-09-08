package com.fitmate.backend.social.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.social.application.SocialRelationshipService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class SocialRelationshipController(private val socialRelationshipService: SocialRelationshipService) {
    @GetMapping("/users/{userId}/profile")
    fun profile(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<UserProfilePreviewResponse> =
        ApiResponse.success(socialRelationshipService.profile(currentUser.userId, userId))

    @GetMapping("/users/{userId}/followers")
    fun followers(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<ItemListResponse<SocialUserResponse>> =
        ApiResponse.success(ItemListResponse(socialRelationshipService.followers(currentUser.userId, userId)))

    @GetMapping("/users/{userId}/following")
    fun following(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<ItemListResponse<SocialUserResponse>> =
        ApiResponse.success(ItemListResponse(socialRelationshipService.following(currentUser.userId, userId)))

    @PostMapping("/users/{userId}/follow")
    fun follow(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<FollowResponse> =
        ApiResponse.success(socialRelationshipService.follow(currentUser.userId, userId))

    @DeleteMapping("/users/{userId}/follow")
    fun cancelOrUnfollow(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<FollowResponse> =
        ApiResponse.success(socialRelationshipService.cancelOrUnfollow(currentUser.userId, userId))

    @GetMapping("/users/me/follow-requests")
    fun requests(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<ItemListResponse<FollowRequestResponse>> =
        ApiResponse.success(ItemListResponse(socialRelationshipService.pendingRequests(currentUser.userId)))

    @PostMapping("/users/me/follow-requests/{followerUserId}/accept")
    fun accept(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable followerUserId: UUID): ApiResponse<FollowResponse> =
        ApiResponse.success(socialRelationshipService.respond(currentUser.userId, followerUserId, accepted = true))

    @PostMapping("/users/me/follow-requests/{followerUserId}/reject")
    fun reject(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable followerUserId: UUID): ApiResponse<FollowResponse> =
        ApiResponse.success(socialRelationshipService.respond(currentUser.userId, followerUserId, accepted = false))

    @DeleteMapping("/users/me/followers/{followerUserId}")
    fun removeFollower(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable followerUserId: UUID): ApiResponse<FollowResponse> =
        ApiResponse.success(socialRelationshipService.removeFollower(currentUser.userId, followerUserId))
}
