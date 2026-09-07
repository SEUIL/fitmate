package com.fitmate.backend.member.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.common.api.ItemListResponse
import com.fitmate.backend.member.application.MemberService
import com.fitmate.backend.terms.domain.TermsType
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class MemberController(private val memberService: MemberService) {
    @GetMapping("/terms")
    fun terms(@RequestParam(required = false) type: TermsType?, @RequestParam(defaultValue = "true") current: Boolean): ApiResponse<ItemListResponse<TermsResponse>> = ApiResponse.success(ItemListResponse(memberService.getTerms(type, current)))

    @GetMapping("/regions")
    fun regions(@RequestParam(required = false) parentId: UUID?): ApiResponse<ItemListResponse<RegionResponse>> = ApiResponse.success(ItemListResponse(memberService.getRegions(parentId)))

    @GetMapping("/facilities")
    fun facilities(@RequestParam(required = false) regionId: UUID?, @RequestParam(required = false) name: String?): ApiResponse<ItemListResponse<FacilityResponse>> = ApiResponse.success(ItemListResponse(memberService.getFacilities(regionId, name)))

    @GetMapping("/users/me")
    fun me(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<MeResponse> = ApiResponse.success(memberService.me(currentUser))

    @PatchMapping("/users/me/profile")
    fun updateProfile(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: ProfileUpdateRequest): ApiResponse<ProfileResponse> = ApiResponse.success(memberService.updateProfile(currentUser, request))

    @PatchMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: ChangePasswordRequest) {
        memberService.changePassword(currentUser, request)
    }

    @GetMapping("/users/me/consents")
    fun consents(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<List<ConsentResponse>> = ApiResponse.success(memberService.me(currentUser).consents)

    @PutMapping("/users/me/consents/{termsDocumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun consent(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable termsDocumentId: UUID) {
        memberService.consent(currentUser, termsDocumentId)
    }

    @DeleteMapping("/users/me/consents/{termsDocumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdrawConsent(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable termsDocumentId: UUID) {
        memberService.withdrawConsent(currentUser, termsDocumentId)
    }

    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: WithdrawRequest) {
        memberService.withdraw(currentUser, request)
    }

    @GetMapping("/users/me/notification-settings")
    fun notificationSettings(@AuthenticationPrincipal currentUser: AuthenticatedUser): ApiResponse<NotificationSettingsResponse> = ApiResponse.success(memberService.notificationSettings(currentUser))

    @PatchMapping("/users/me/notification-settings")
    fun updateNotificationSettings(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: NotificationSettingsUpdateRequest): ApiResponse<NotificationSettingsResponse> = ApiResponse.success(memberService.updateNotificationSettings(currentUser, request))
}
