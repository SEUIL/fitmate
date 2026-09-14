package com.fitmate.backend.auth.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.repository.AuthSessionRepository
import com.fitmate.backend.auth.repository.RevokedAccessTokenRepository
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.api.ApiErrorBody
import com.fitmate.backend.common.api.ErrorResponse
import com.fitmate.backend.common.error.ApiErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val revokedAccessTokenRepository: RevokedAccessTokenRepository,
    private val objectMapper: ObjectMapper,
    private val accountSuspensionService: com.fitmate.backend.admin.application.AccountSuspensionService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val currentUser = jwtService.toAuthenticatedUser(jwtService.decode(header.removePrefix("Bearer ").trim()))
            val user = accountSuspensionService.restoreIfExpired(userRepository.findById(currentUser.userId).orElseThrow { AuthenticationFailure(ApiErrorCode.INVALID_ACCESS_TOKEN) })
            when (user.accountStatus) {
                AccountStatus.ACTIVE -> Unit
                AccountStatus.SUSPENDED -> throw AuthenticationFailure(ApiErrorCode.ACCOUNT_SUSPENDED)
                AccountStatus.BANNED -> throw AuthenticationFailure(ApiErrorCode.ACCOUNT_BANNED)
                AccountStatus.WITHDRAWN -> throw AuthenticationFailure(ApiErrorCode.ACCOUNT_WITHDRAWN)
            }
            if (user.authVersion != currentUser.authVersion || revokedAccessTokenRepository.existsById(currentUser.accessTokenJti)) {
                throw AuthenticationFailure(ApiErrorCode.INVALID_ACCESS_TOKEN)
            }
            val session = authSessionRepository.findByIdAndUserId(currentUser.sessionId, currentUser.userId)
                ?: throw AuthenticationFailure(ApiErrorCode.INVALID_ACCESS_TOKEN)
            if (session.revokedAt != null) {
                throw AuthenticationFailure(ApiErrorCode.INVALID_ACCESS_TOKEN)
            }
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            filterChain.doFilter(request, response)
        } catch (exception: AuthenticationFailure) {
            writeError(response, exception.code)
        } catch (exception: JwtException) {
            writeError(response, ApiErrorCode.INVALID_ACCESS_TOKEN)
        } catch (exception: IllegalArgumentException) {
            writeError(response, ApiErrorCode.INVALID_ACCESS_TOKEN)
        }
    }

    private fun writeError(response: HttpServletResponse, errorCode: ApiErrorCode) {
        SecurityContextHolder.clearContext()
        response.status = errorCode.status.value()
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ErrorResponse(ApiErrorBody(errorCode.name, errorCode.message)))
    }

    private class AuthenticationFailure(val code: ApiErrorCode) : RuntimeException()
}
