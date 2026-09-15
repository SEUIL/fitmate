package com.fitmate.backend.inbody.repository

import com.fitmate.backend.inbody.domain.InbodyAnalysis
import com.fitmate.backend.inbody.domain.InbodyRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import java.time.Instant
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus

interface InbodyAnalysisRepository : JpaRepository<InbodyAnalysis, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyAnalysis?
    fun countByStatusAndCreatedAtGreaterThanEqual(status: InbodyAnalysisStatus, createdAt: Instant): Long

    @Query("select a.status as status, count(a) as total from InbodyAnalysis a where a.createdAt >= :from and a.createdAt < :to group by a.status")
    fun countStatuses(@Param("from") from: Instant, @Param("to") to: Instant): List<InbodyStatusCount>

    @Query("select a.failureCode as code, count(a) as total from InbodyAnalysis a where a.createdAt >= :from and a.createdAt < :to and a.status = :status group by a.failureCode")
    fun countFailureCodes(@Param("from") from: Instant, @Param("to") to: Instant,
        @Param("status") status: InbodyAnalysisStatus): List<InbodyFailureCount>
}

interface InbodyStatusCount { val status: InbodyAnalysisStatus; val total: Long }
interface InbodyFailureCount { val code: String?; val total: Long }

interface InbodyRecordRepository : JpaRepository<InbodyRecord, UUID> {
    fun findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId: UUID): List<InbodyRecord>
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyRecord?
    fun existsByAnalysisId(analysisId: UUID): Boolean
}
