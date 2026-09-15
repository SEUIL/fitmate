package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.AdminAuditLog
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.domain.AdminSanction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminRoleRepository : JpaRepository<AdminRoleAssignment, UUID> { fun findByUserIdAndRevokedAtIsNull(userId: UUID): AdminRoleAssignment? }
interface AdminSanctionRepository : JpaRepository<AdminSanction, UUID> {
    fun findAllByUserIdAndAction(userId: UUID, action: com.fitmate.backend.admin.domain.ModerationAction): List<AdminSanction>
    fun findFirstByReportIdOrderByCreatedAtDesc(reportId: UUID): AdminSanction?
    fun findFirstByUserIdAndActionOrderByCreatedAtDesc(userId: UUID, action: com.fitmate.backend.admin.domain.ModerationAction): AdminSanction?
}
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID> {
    fun findAllByReportIdOrderByOccurredAtDesc(reportId: UUID): List<AdminAuditLog>
    fun findTop3ByOrderByOccurredAtDesc(): List<AdminAuditLog>
}
