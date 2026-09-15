package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityExistsException
import jakarta.persistence.PersistenceException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
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
        try {
            entityManager.persist(session)
            entityManager.flush()
            entityManager.persist(record)
            entityManager.flush()
            return record
        } catch (exception: ConstraintViolationException) {
            throw DataIntegrityViolationException("운동 기록 저장 제약을 위반했습니다.", exception)
        } catch (exception: EntityExistsException) {
            throw DataIntegrityViolationException("운동 기록이 이미 존재합니다.", exception)
        } catch (exception: PersistenceException) {
            if (generateSequence<Throwable>(exception) { it.cause }.any { it is ConstraintViolationException || it is EntityExistsException }) {
                throw DataIntegrityViolationException("운동 기록 저장 제약을 위반했습니다.", exception)
            }
            throw exception
        }
    }
}
