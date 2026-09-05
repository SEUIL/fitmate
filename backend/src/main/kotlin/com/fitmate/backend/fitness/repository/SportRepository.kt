package com.fitmate.backend.fitness.repository

import com.fitmate.backend.fitness.domain.Sport
import com.fitmate.backend.fitness.domain.SportCode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SportRepository : JpaRepository<Sport, UUID> {
    fun findByIsActiveTrueOrderByDisplayOrderAsc(): List<Sport>
    fun findByCodeAndIsActiveTrue(code: SportCode): Sport?
}
