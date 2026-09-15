package com.fitmate.backend.auth.repository

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.AuthEvent
import com.fitmate.backend.auth.domain.AuthSession
import com.fitmate.backend.auth.domain.RevokedAccessToken
import com.fitmate.backend.auth.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findForOperatorUpdate(id: UUID): User?
    fun findByEmailNormalizedAndAccountStatusNot(emailNormalized: String, accountStatus: AccountStatus): User?
    fun existsByEmailNormalizedAndAccountStatusNot(emailNormalized: String, accountStatus: AccountStatus): Boolean
    fun countByCreatedAtGreaterThanEqual(createdAt: Instant): Long
}

interface AuthSessionRepository : JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.refreshTokenHash = :refreshTokenHash")
    fun findForUpdateByRefreshTokenHash(refreshTokenHash: String): AuthSession?
    fun findAllByUserIdAndRevokedAtIsNull(userId: UUID): List<AuthSession>
    fun findByIdAndUserId(id: UUID, userId: UUID): AuthSession?
}

interface RevokedAccessTokenRepository : JpaRepository<RevokedAccessToken, String> {
    fun deleteByExpiresAtBefore(now: Instant): Long
}

interface AuthEventRepository : JpaRepository<AuthEvent, UUID>
