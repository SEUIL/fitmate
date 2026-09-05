package com.fitmate.backend.auth.api

import com.fitmate.backend.auth.application.AuthService
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @GetMapping("/email-availability")
    fun emailAvailability(@RequestParam email: String): ApiResponse<AvailabilityResponse> = ApiResponse.success(AvailabilityResponse(email.trim(), authService.isEmailAvailable(email)))

    @GetMapping("/nickname-availability")
    fun nicknameAvailability(@RequestParam nickname: String): ApiResponse<AvailabilityResponse> = ApiResponse.success(AvailabilityResponse(nickname.trim(), authService.isNicknameAvailable(nickname)))

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest, servletRequest: HttpServletRequest): ApiResponse<SignUpResponse> = ApiResponse.success(authService.signUp(request, servletRequest.getHeader("User-Agent")))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, servletRequest: HttpServletRequest): ApiResponse<LoginResponse> = ApiResponse.success(authService.login(request, servletRequest.getHeader("User-Agent")))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest, servletRequest: HttpServletRequest): ApiResponse<TokenResponse> = ApiResponse.success(authService.refresh(request, servletRequest.getHeader("User-Agent")))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal currentUser: AuthenticatedUser, @Valid @RequestBody request: LogoutRequest, servletRequest: HttpServletRequest) {
        authService.logout(currentUser, request, servletRequest.getHeader("User-Agent"))
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(@AuthenticationPrincipal currentUser: AuthenticatedUser, servletRequest: HttpServletRequest) {
        authService.logoutAll(currentUser, servletRequest.getHeader("User-Agent"))
    }
}
