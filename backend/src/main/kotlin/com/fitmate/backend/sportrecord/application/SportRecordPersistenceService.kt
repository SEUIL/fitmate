package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SportRecordPersistenceService(
    private val entityManager: EntityManager,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(session: WorkoutSession, record: SportRecord): SportRecord {
        // persist never performs JPA merge: a concurrent session with the same PK raises a constraint error
        // instead of overwriting the already-created record.
        entityManager.persist(session)
        entityManager.flush()
        entityManager.persist(record)
        entityManager.flush()
        return record
    }
}
