package com.fitmate.backend.sportrecord.repository

import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.sportrecord.domain.SportRecord
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SportRecordRepository : JpaRepository<SportRecord, UUID> {
    @Query("select r from SportRecord r join fetch r.workoutSession s where r.workoutSessionId = :id and s.userId = :userId")
    fun findOwned(@Param("id") id: UUID, @Param("userId") userId: UUID): SportRecord?

    @Query("select r from SportRecord r join fetch r.workoutSession s where s.userId = :userId and (:sportCode is null or s.sportCode = :sportCode) order by s.endedAt desc, r.workoutSessionId desc")
    fun findAllOwned(@Param("userId") userId: UUID, @Param("sportCode") sportCode: SportCode?, pageable: Pageable): Slice<SportRecord>
}
