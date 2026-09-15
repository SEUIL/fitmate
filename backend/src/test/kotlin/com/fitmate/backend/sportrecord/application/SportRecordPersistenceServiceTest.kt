package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.Mockito.doThrow
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock

class SportRecordPersistenceServiceTest {
    private val entityManager = mock(EntityManager::class.java)
    private val service = SportRecordPersistenceService(entityManager)

    @Test fun `persists parent then detail without merge`() {
        val session = WorkoutSession()
        val record = SportRecord()

        service.save(session, record)

        inOrder(entityManager).apply {
            verify(entityManager).persist(session)
            verify(entityManager).flush()
            verify(entityManager).persist(record)
            verify(entityManager).flush()
        }
    }

    @Test fun `converts direct flush constraint violation to Spring integrity exception`() {
        doThrow(ConstraintViolationException("duplicate", java.sql.SQLException(), "pk")).`when`(entityManager).flush()

        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            service.save(WorkoutSession(), SportRecord())
        }
    }

    @Test fun `converts wrapped flush constraint violation to Spring integrity exception`() {
        doThrow(PersistenceException(ConstraintViolationException("duplicate", java.sql.SQLException(), "pk"))).`when`(entityManager).flush()

        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            service.save(WorkoutSession(), SportRecord())
        }
    }
}
