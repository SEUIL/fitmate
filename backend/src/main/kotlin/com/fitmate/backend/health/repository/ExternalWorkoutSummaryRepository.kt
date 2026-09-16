package com.fitmate.backend.health.repository

import com.fitmate.backend.health.domain.ExternalWorkoutSummary
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExternalWorkoutSummaryRepository : JpaRepository<ExternalWorkoutSummary, UUID> {
    fun findByWorkoutSessionIdAndUserId(workoutSessionId: UUID, userId: UUID): ExternalWorkoutSummary?
    fun deleteByWorkoutSessionIdAndUserId(workoutSessionId: UUID, userId: UUID): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExternalWorkoutSummary e where e.workoutSessionId = :sessionId and e.userId = :userId")
    fun findOwnedForUpdate(@Param("sessionId") sessionId: UUID, @Param("userId") userId: UUID): ExternalWorkoutSummary?

    fun findByUserIdAndProviderAndSourceAppIdAndSourceRecordId(
        userId: UUID,
        provider: String,
        sourceAppId: String?,
        sourceRecordId: String,
    ): ExternalWorkoutSummary?
}
