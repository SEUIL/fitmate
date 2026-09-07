package com.fitmate.backend.workout.repository

import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionExercise
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.domain.WorkoutSet
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface WorkoutSessionRepository : JpaRepository<WorkoutSession, UUID> {
    @EntityGraph(attributePaths = ["routine"])
    fun findByIdAndUserId(id: UUID, userId: UUID): WorkoutSession?
}

interface WorkoutSessionExerciseRepository : JpaRepository<WorkoutSessionExercise, UUID> {
    @EntityGraph(attributePaths = ["exercise"])
    fun findAllByWorkoutSessionIdOrderBySequenceNoAsc(workoutSessionId: UUID): List<WorkoutSessionExercise>

    @EntityGraph(attributePaths = ["workoutSession", "exercise"])
    fun findByIdAndWorkoutSessionUserId(id: UUID, userId: UUID): WorkoutSessionExercise?

    @Query(
        """
        select se from WorkoutSessionExercise se
        join fetch se.workoutSession ws
        join fetch se.exercise e
        where e.id = :exerciseId
          and ws.userId = :userId
          and ws.status = :status
          and se.completedAt is not null
          and ws.startedAt < :before
        order by ws.startedAt desc
        """,
    )
    fun findPreviousCompleted(
        @Param("exerciseId") exerciseId: UUID,
        @Param("userId") userId: UUID,
        @Param("status") status: WorkoutSessionStatus = WorkoutSessionStatus.COMPLETED,
        @Param("before") before: Instant,
        pageable: Pageable,
    ): List<WorkoutSessionExercise>
}

interface WorkoutSetRepository : JpaRepository<WorkoutSet, UUID> {
    fun findAllBySessionExerciseIdOrderBySetNoAsc(sessionExerciseId: UUID): List<WorkoutSet>
    fun findBySessionExerciseIdAndSetNo(sessionExerciseId: UUID, setNo: Int): WorkoutSet?
}
