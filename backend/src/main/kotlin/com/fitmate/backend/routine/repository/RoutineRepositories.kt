package com.fitmate.backend.routine.repository

import com.fitmate.backend.routine.domain.Routine
import com.fitmate.backend.routine.domain.RoutineItem
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoutineRepository : JpaRepository<Routine, UUID> {
    @EntityGraph(attributePaths = ["sport"])
    fun findAllByIsTemplateTrueAndIsActiveTrueOrderByNameAsc(): List<Routine>

    @EntityGraph(attributePaths = ["sport"])
    fun findAllByOwnerUserIdAndIsTemplateFalseAndIsActiveTrueOrderByUpdatedAtDesc(ownerUserId: UUID): List<Routine>

    @EntityGraph(attributePaths = ["sport"])
    fun findByIdAndIsActiveTrue(id: UUID): Routine?
}

interface RoutineItemRepository : JpaRepository<RoutineItem, UUID> {
    @EntityGraph(attributePaths = ["exercise"])
    fun findAllByRoutineIdOrderBySequenceNoAsc(routineId: UUID): List<RoutineItem>
}
