package com.fitmate.backend.common.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("fitmate.security.jwt")
data class JwtProperties(
    @field:NotBlank val issuer: String = "fitmate-backend",
    @field:NotBlank val secret: String = "",
    @field:NotNull val accessTokenTtl: Duration = Duration.ofMinutes(15),
    @field:NotNull val refreshTokenTtl: Duration = Duration.ofDays(30),
)
