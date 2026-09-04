package com.fitmate.backend.fitness.repository

import com.fitmate.backend.fitness.domain.ExerciseSubstitution
import com.fitmate.backend.fitness.domain.ExerciseSubstitutionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExerciseSubstitutionRepository : JpaRepository<ExerciseSubstitution, ExerciseSubstitutionId> {

    @Query(
        """
        select es
        from ExerciseSubstitution es
        join fetch es.substituteExercise se
        join fetch se.sport s
        where es.baseExercise.id = :exerciseId
        order by es.displayOrder asc
        """,
    )
    fun findAllByBaseExerciseId(@Param("exerciseId") exerciseId: UUID): List<ExerciseSubstitution>
}
