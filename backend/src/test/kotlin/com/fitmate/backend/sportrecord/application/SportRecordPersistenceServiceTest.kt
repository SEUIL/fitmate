package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.workout.domain.WorkoutSession
import jakarta.persistence.EntityManager
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
}
