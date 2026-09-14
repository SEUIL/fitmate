package com.fitmate.backend.recommendation.infrastructure

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fitness_workout_plans")
class WorkoutPlanEntity(
    @Id @Column(length = 36) var id: String = "",
    @Column(name = "owner_user_id", nullable = false, columnDefinition = "BINARY(16)") var ownerUserId: UUID? = null,
    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT") var requestJson: String = "",
    @Column(name = "result_json", columnDefinition = "LONGTEXT") var resultJson: String? = null,
    @Column(nullable = false, length = 16) var status: String = "GENERATING",
    @Column(name = "generation_token", nullable = false, length = 36) var generationToken: String = UUID.randomUUID().toString(),
    @Column(name = "routine_id", columnDefinition = "BINARY(16)") var routineId: UUID? = null,
    @Column(length = 300) var message: String? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Version @Column(nullable = false) var version: Long = 0,
)

interface WorkoutPlanRepository : JpaRepository<WorkoutPlanEntity, String> {
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """INSERT INTO fitness_workout_plans
        (id, owner_user_id, request_json, status, generation_token, updated_at, created_at, version)
        VALUES (:id, :owner, :request, 'GENERATING', :token, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
        ON DUPLICATE KEY UPDATE id = fitness_workout_plans.id""", nativeQuery = true)
    fun insertIfAbsent(@Param("id") id: String, @Param("owner") owner: UUID, @Param("request") request: String, @Param("token") token: String): Int
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WorkoutPlanEntity p where p.id = :id and p.ownerUserId = :owner")
    fun lockOwned(@Param("id") id: String, @Param("owner") owner: UUID): WorkoutPlanEntity?
    fun findByIdAndOwnerUserId(id: String, ownerUserId: UUID): WorkoutPlanEntity?
}
