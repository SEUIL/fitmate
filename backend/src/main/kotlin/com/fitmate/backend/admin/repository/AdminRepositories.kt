package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.AdminAuditLog
import com.fitmate.backend.admin.domain.AdminRoleAssignment
import com.fitmate.backend.admin.domain.AdminSanction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminRoleRepository : JpaRepository<AdminRoleAssignment, UUID> {
    fun findAllByOrderByGrantedAtDescUserIdDesc(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<AdminRoleAssignment>
    fun countByRoleAndRevokedAtIsNull(role: com.fitmate.backend.admin.domain.AdminRole): Long
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): AdminRoleAssignment?
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AdminRoleAssignment r where r.userId = :userId and r.revokedAt is null")
    fun findActiveForUpdate(userId: UUID): AdminRoleAssignment?
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AdminRoleAssignment r where r.userId = :userId")
    fun findForUpdate(userId: UUID): AdminRoleAssignment?
}
interface AdminSanctionRepository : JpaRepository<AdminSanction, UUID> {
    fun findAllByUserIdOrderByCreatedAtDescIdDesc(userId: UUID, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<AdminSanction>
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from AdminSanction s where s.id = :id")
    fun findForUpdate(id: UUID): AdminSanction?
    fun findAllByUserIdAndAction(userId: UUID, action: com.fitmate.backend.admin.domain.ModerationAction): List<AdminSanction>
    fun findFirstByReportIdOrderByCreatedAtDesc(reportId: UUID): AdminSanction?
    fun findFirstByUserIdAndActionOrderByCreatedAtDesc(userId: UUID, action: com.fitmate.backend.admin.domain.ModerationAction): AdminSanction?
}
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID> {
    fun findAllByReportIdOrderByOccurredAtDesc(reportId: UUID): List<AdminAuditLog>
    fun findTop3ByOrderByOccurredAtDesc(): List<AdminAuditLog>
}
