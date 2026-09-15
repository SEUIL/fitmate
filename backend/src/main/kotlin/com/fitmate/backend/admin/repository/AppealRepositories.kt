package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AppealSessionRepository : JpaRepository<AppealSession, String>
interface AppealAuthAttemptsRepository : JpaRepository<AppealAuthAttempts, UUID>
interface SanctionAppealRepository : JpaRepository<SanctionAppeal, UUID> {
    fun findAllByUserIdOrderByCreatedAtDescIdDesc(userId: UUID, pageable: Pageable): Page<SanctionAppeal>
    fun findAllByOrderByCreatedAtDescIdDesc(pageable: Pageable): Page<SanctionAppeal>
    fun findAllByStatusOrderByCreatedAtDescIdDesc(status: AppealStatus, pageable: Pageable): Page<SanctionAppeal>
    fun findFirstBySanctionIdOrderByCreatedAtDescIdDesc(sanctionId: UUID): SanctionAppeal?
    fun existsBySanctionIdAndStatusIn(sanctionId: UUID, statuses: Collection<AppealStatus>): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SanctionAppeal a where a.id = :id")
    fun findForUpdate(id: UUID): SanctionAppeal?
}
