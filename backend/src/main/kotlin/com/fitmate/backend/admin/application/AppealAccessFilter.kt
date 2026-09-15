package com.fitmate.backend.admin.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.api.ApiErrorBody
import com.fitmate.backend.common.api.ErrorResponse
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.error.ApiErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AppealAccessFilter(private val access: AppealAccessService, private val mapper: ObjectMapper) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !request.servletPath.startsWith("/api/v1/appeal-access/")
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = try {
            val header = request.getHeader("Authorization").orEmpty()
            if (!header.startsWith("Appeal ")) throw ApiException(ApiErrorCode.INVALID_ACCESS_TOKEN)
            access.verify(header.removePrefix("Appeal ").trim())
        } catch (failure: ApiException) {
            SecurityContextHolder.clearContext()
            response.status = failure.errorCode.status.value()
            response.contentType = "application/json"; response.characterEncoding = "UTF-8"
            mapper.writeValue(response.writer, ErrorResponse(ApiErrorBody(failure.errorCode.name, failure.errorCode.message)))
            return
        }
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_APPEAL")))
        chain.doFilter(request, response)
    }
}
