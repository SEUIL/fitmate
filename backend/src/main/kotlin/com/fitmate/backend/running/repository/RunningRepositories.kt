package com.fitmate.backend.running.repository

import com.fitmate.backend.running.domain.RunningLap
import com.fitmate.backend.running.domain.RunningRecord
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RunningRecordRepository : JpaRepository<RunningRecord, UUID> {
    @Query(
        """
        select record from RunningRecord record
        join fetch record.workoutSession session
        where record.workoutSessionId = :recordId and session.userId = :userId
        """,
    )
    fun findOwned(
        @Param("recordId") recordId: UUID,
        @Param("userId") userId: UUID,
    ): RunningRecord?

    @Query(
        """
        select record from RunningRecord record
        join fetch record.workoutSession session
        where session.userId = :userId
        order by session.endedAt desc, record.workoutSessionId desc
        """,
    )
    fun findAllOwned(
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): Slice<RunningRecord>
}

interface RunningLapRepository : JpaRepository<RunningLap, UUID> {
    fun findAllByWorkoutSessionIdOrderByLapNoAsc(workoutSessionId: UUID): List<RunningLap>
}
