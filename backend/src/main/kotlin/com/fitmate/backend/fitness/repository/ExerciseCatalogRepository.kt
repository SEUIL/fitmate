package com.fitmate.backend.fitness.repository

import com.fitmate.backend.fitness.domain.BodyPart
import com.fitmate.backend.fitness.domain.EquipmentType
import com.fitmate.backend.fitness.domain.ExerciseCatalog
import com.fitmate.backend.fitness.domain.SportCode
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExerciseCatalogRepository : JpaRepository<ExerciseCatalog, UUID> {

    @Query(
        """
        select e
        from ExerciseCatalog e
        join fetch e.sport s
        where e.isActive = true
          and (:sportCode is null or s.code = :sportCode)
          and (:bodyPart is null or e.bodyPart = :bodyPart)
          and (:equipmentType is null or e.equipmentType = :equipmentType)
        order by e.id asc
        """,
    )
    fun findActiveExercises(
        @Param("sportCode") sportCode: SportCode?,
        @Param("bodyPart") bodyPart: BodyPart?,
        @Param("equipmentType") equipmentType: EquipmentType?,
        pageable: Pageable,
    ): List<ExerciseCatalog>

    @Query(
        """
        select e
        from ExerciseCatalog e
        join fetch e.sport s
        where e.id = :exerciseId
          and e.isActive = true
        """,
    )
    fun findActiveExerciseById(@Param("exerciseId") exerciseId: UUID): ExerciseCatalog?
}
