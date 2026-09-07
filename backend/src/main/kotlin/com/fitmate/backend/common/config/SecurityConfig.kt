package com.fitmate.backend.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.api.ApiErrorBody
import com.fitmate.backend.common.api.ErrorResponse
import com.fitmate.backend.common.error.ApiErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: com.fitmate.backend.auth.application.JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> writeError(response, ApiErrorCode.INVALID_ACCESS_TOKEN) }
                it.accessDeniedHandler { _, response, _ -> writeError(response, ApiErrorCode.FORBIDDEN_RESOURCE) }
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/exercises/*/previous-record").authenticated()
                it.requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/email-availability",
                    "/api/v1/auth/nickname-availability",
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/terms",
                    "/api/v1/regions",
                    "/api/v1/facilities",
                    "/api/v1/sports",
                    "/api/v1/exercises/**",
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    private fun writeError(response: jakarta.servlet.http.HttpServletResponse, errorCode: ApiErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ErrorResponse(ApiErrorBody(errorCode.name, errorCode.message)))
    }
}
