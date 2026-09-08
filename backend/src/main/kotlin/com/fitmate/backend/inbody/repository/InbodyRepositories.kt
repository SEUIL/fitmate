package com.fitmate.backend.inbody.repository

import com.fitmate.backend.inbody.domain.InbodyAnalysis
import com.fitmate.backend.inbody.domain.InbodyRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InbodyAnalysisRepository : JpaRepository<InbodyAnalysis, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyAnalysis?
}

interface InbodyRecordRepository : JpaRepository<InbodyRecord, UUID> {
    fun findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(userId: UUID): List<InbodyRecord>
    fun findByIdAndUserId(id: UUID, userId: UUID): InbodyRecord?
    fun existsByAnalysisId(analysisId: UUID): Boolean
}
