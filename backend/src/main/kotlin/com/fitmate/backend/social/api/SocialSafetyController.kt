package com.fitmate.backend.social.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.social.application.SocialSafetyService
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
class SocialSafetyController(private val socialSafetyService: SocialSafetyService) {
    @PostMapping("/users/{userId}/block")
    fun block(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ApiResponse<BlockedUserResponse> =
        ApiResponse.success(socialSafetyService.block(currentUser.userId, userId))

    @DeleteMapping("/users/{userId}/block")
    fun unblock(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID) {
        socialSafetyService.unblock(currentUser.userId, userId)
    }

    @GetMapping("/users/me/blocks")
    fun blocks(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<ItemListResponse<BlockedUserResponse>> =
        ApiResponse.success(ItemListResponse(socialSafetyService.blocks(currentUser.userId)))
}
