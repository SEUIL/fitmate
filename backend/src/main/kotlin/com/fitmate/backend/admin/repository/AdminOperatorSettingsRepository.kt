package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.AdminOperatorSettings
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface AdminOperatorSettingsRepository : JpaRepository<AdminOperatorSettings, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AdminOperatorSettings s where s.id = 1")
    fun lockCatalog(): AdminOperatorSettings?
}
