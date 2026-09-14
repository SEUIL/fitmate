package com.fitmate.backend.reporting.repository

import com.fitmate.backend.reporting.domain.ReportTargetType
import com.fitmate.backend.reporting.domain.UserReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserReportRepository : JpaRepository<UserReport, UUID> {
    fun existsByReporterUserIdAndTargetTypeAndTargetId(reporterUserId: UUID, targetType: ReportTargetType, targetId: UUID): Boolean
    fun findAllByStatusOrderByCreatedAtDescIdDesc(status: com.fitmate.backend.reporting.domain.ReportStatus, pageable: Pageable): Slice<UserReport>
    fun findAllByOrderByCreatedAtDescIdDesc(pageable: Pageable): Slice<UserReport>
    fun findAllByOrderByCreatedAtDescIdDesc(): List<UserReport>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from UserReport r where r.id = :id")
    fun findForUpdate(id: UUID): UserReport?
}
