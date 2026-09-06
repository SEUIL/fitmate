package com.fitmate.backend.member.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.member.application.ProfileService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class ProfileController(private val profileService: ProfileService) {
    @GetMapping
    fun get(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<MyProfileResponse> =
        ApiResponse.success(profileService.get(currentUser.userId))

    @PatchMapping("/profile")
    fun update(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<MyProfileResponse> = ApiResponse.success(profileService.update(currentUser.userId, request))
}
