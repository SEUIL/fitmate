package com.fitmate.backend.routine.repository

import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.domain.RoutineItem
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RoutineRepository : JpaRepository<Routine, UUID> {
    @EntityGraph(attributePaths = ["sport"])
    fun findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc(): List<Routine>

    @EntityGraph(attributePaths = ["sport"])
    fun findAllByOwnerUserIdAndIsTemplateFalseAndIsActiveTrueOrderByUpdatedAtDesc(ownerUserId: UUID): List<Routine>

    @EntityGraph(attributePaths = ["sport"])
    fun findByIdAndIsActiveTrue(id: UUID): Routine?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Routine r set r.version = r.version + 1, r.updatedAt = :updatedAt where r.id = :id and r.version = :version")
    fun incrementVersion(
        @Param("id") id: UUID,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}

interface RoutineItemRepository : JpaRepository<RoutineItem, UUID> {
    @EntityGraph(attributePaths = ["exercise"])
    fun findAllByRoutineIdOrderBySequenceNoAsc(routineId: UUID): List<RoutineItem>
}
