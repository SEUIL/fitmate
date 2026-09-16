package com.fitmate.backend.health.repository

import com.fitmate.backend.health.domain.ExternalWorkoutSample
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalWorkoutSampleRepository : JpaRepository<ExternalWorkoutSample, UUID> {
    fun findAllByWorkoutSessionIdOrderByMeasuredAtAsc(workoutSessionId: UUID): List<ExternalWorkoutSample>
    fun deleteAllByWorkoutSessionId(workoutSessionId: UUID)
}
