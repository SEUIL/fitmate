package com.fitmate.backend.inbody.repository

import com.fitmate.backend.inbody.domain.InbodyAnalysis
import com.fitmate.backend.inbody.domain.InbodyRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import java.time.Instant
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus

interface InbodyAnalysisRepository : JpaRepository<InbodyAnalysis, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyAnalysis?
    fun countByStatusAndCreatedAtGreaterThanEqual(status: InbodyAnalysisStatus, createdAt: Instant): Long
}

interface InbodyRecordRepository : JpaRepository<InbodyRecord, UUID> {
    fun findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId: UUID): List<InbodyRecord>
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyRecord?
    fun existsByAnalysisId(analysisId: UUID): Boolean
}
