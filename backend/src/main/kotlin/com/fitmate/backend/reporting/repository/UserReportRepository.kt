package com.fitmate.backend.reporting.repository

import com.fitmate.backend.reporting.domain.ReportTargetType
import com.fitmate.backend.reporting.domain.UserReport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserReportRepository : JpaRepository<UserReport, UUID> {
    fun existsByReporterUserIdAndTargetTypeAndTargetId(reporterUserId: UUID, targetType: ReportTargetType, targetId: UUID): Boolean
}
