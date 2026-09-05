package com.fitmate.backend.auth.application

import com.fitmate.backend.auth.domain.AuthSession
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.common.config.JwtProperties
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class AuthenticatedUser(
    val userId: UUID,
    val sessionId: UUID,
    val accessTokenJti: String,
    val authVersion: Long,
    val expiresAt: Instant,
)

data class IssuedAccessToken(
    val value: String,
    val expiresAt: Instant,
)

@Service
class JwtService(
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: JwtDecoder,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) {
    fun issueAccessToken(user: User, session: AuthSession): IssuedAccessToken {
        val now = clock.instant()
        val expiresAt = now.plus(jwtProperties.accessTokenTtl)
        val sessionId = requireNotNull(session.id)
        val userId = requireNotNull(user.id)
        val jti = UUID.randomUUID().toString()
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(jti)
            .claim("sid", sessionId.toString())
            .claim("av", user.authVersion)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return IssuedAccessToken(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresAt)
    }

    fun decode(token: String): Jwt = jwtDecoder.decode(token)

    fun toAuthenticatedUser(jwt: Jwt): AuthenticatedUser {
        val authVersion = jwt.getClaim<Number>("av")?.toLong()
            ?: throw IllegalArgumentException("missing auth version")
        return AuthenticatedUser(
            userId = UUID.fromString(jwt.subject),
            sessionId = UUID.fromString(jwt.getClaimAsString("sid")),
            accessTokenJti = requireNotNull(jwt.id),
            authVersion = authVersion,
            expiresAt = requireNotNull(jwt.expiresAt),
        )
    }
}
