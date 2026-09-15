package com.fitmate.backend.workout.repository

import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionExercise
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.domain.WorkoutSet
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID
import com.fitmate.backend.fitness.domain.SportCode

interface WorkoutSessionRepository : JpaRepository<WorkoutSession, UUID> {
    @Query("select s from WorkoutSession s where s.userId = :user and s.sportCode = :sport and s.status = :status and s.startedAt >= :from and s.endedAt <= :through order by s.startedAt desc, s.id desc")
    fun insightHistory(user: UUID, sport: SportCode, status: WorkoutSessionStatus, from: Instant, through: Instant, pageable: Pageable): Slice<WorkoutSession>
    fun countByStatusAndEndedAtGreaterThanEqual(status: WorkoutSessionStatus, endedAt: Instant): Long
    @EntityGraph(attributePaths = ["routine"])
    fun findByIdAndUserId(id: UUID, userId: UUID): WorkoutSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ws from WorkoutSession ws where ws.id = :id and ws.userId = :userId")
    fun findByIdAndUserIdForUpdate(@Param("id") id: UUID, @Param("userId") userId: UUID): WorkoutSession?

    fun findAllByUserIdOrderByStartedAtDescIdDesc(userId: UUID, pageable: Pageable): Slice<WorkoutSession>
    fun findAllByUserIdAndStatusOrderByStartedAtDescIdDesc(
        userId: UUID,
        status: WorkoutSessionStatus,
        pageable: Pageable,
    ): Slice<WorkoutSession>
    fun findAllByUserIdAndSportCodeOrderByStartedAtDescIdDesc(userId: UUID, sportCode: SportCode, pageable: Pageable): Slice<WorkoutSession>
    fun findAllByUserIdAndStatusAndSportCodeOrderByStartedAtDescIdDesc(userId: UUID, status: WorkoutSessionStatus, sportCode: SportCode, pageable: Pageable): Slice<WorkoutSession>

    fun countByUserIdAndRoutineIdAndStatus(
        userId: UUID,
        routineId: UUID,
        status: WorkoutSessionStatus,
    ): Long
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
    fun deleteAllBySessionExerciseId(sessionExerciseId: UUID)
}
