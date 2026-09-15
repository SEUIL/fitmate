package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.AdminNotice
import com.fitmate.backend.admin.domain.AdminCategorySettings
import com.fitmate.backend.admin.domain.NoticeStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AdminNoticeRepository : JpaRepository<AdminNotice, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AdminNotice n where n.id = :id")
    fun findForUpdate(id: UUID): AdminNotice?
    fun findAllByOrderByUpdatedAtDescIdDesc(pageable: Pageable): List<AdminNotice>
    fun findAllByStatusOrderByPublishedAtDescIdDesc(status: NoticeStatus, pageable: Pageable): List<AdminNotice>
    fun findByIdAndStatus(id: UUID, status: NoticeStatus): AdminNotice?
}

interface AdminCategorySettingsRepository : JpaRepository<AdminCategorySettings, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminCategorySettings s where s.id = 1")
    fun lockSettings(): AdminCategorySettings?
}
