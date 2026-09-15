package com.fitmate.backend.report.infrastructure

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fitness_workout_insights")
class WorkoutInsightEntity(
    @Id @Column(length = 36) var id: String = "",
    @Column(name = "owner_user_id", nullable = false, columnDefinition = "BINARY(16)") var ownerUserId: UUID? = null,
    @Column(name = "session_id", nullable = false, columnDefinition = "BINARY(16)") var sessionId: UUID? = null,
    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT") var requestJson: String = "",
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT") var snapshotJson: String = "",
    @Column(name = "result_json", columnDefinition = "LONGTEXT") var resultJson: String? = null,
    @Column(nullable = false, length = 16) var status: String = "GENERATING",
    @Column(name = "generation_token", nullable = false, length = 36) var generationToken: String = UUID.randomUUID().toString(),
    @Column(length = 300) var message: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Version var version: Long = 0,
)

interface WorkoutInsightRepository : JpaRepository<WorkoutInsightEntity, String> {
    fun findFirstByOwnerUserIdAndSessionIdAndStatusAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(ownerUserId: UUID, sessionId: UUID, status: String, createdAt: Instant): WorkoutInsightEntity?
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """INSERT INTO fitness_workout_insights
        (id, owner_user_id, session_id, request_json, snapshot_json, status, generation_token, created_at, updated_at, version)
        VALUES (:id, :owner, :session, :request, :snapshot, 'GENERATING', :token, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
        ON DUPLICATE KEY UPDATE id = fitness_workout_insights.id""", nativeQuery = true)
    fun insertIfAbsent(id: String, owner: UUID, session: UUID, request: String, snapshot: String, token: String): Int
    fun findFirstByOwnerUserIdAndSessionIdOrderByCreatedAtDescIdDesc(ownerUserId: UUID, sessionId: UUID): WorkoutInsightEntity?
    fun findByIdAndOwnerUserIdAndSessionId(id: String, ownerUserId: UUID, sessionId: UUID): WorkoutInsightEntity?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkoutInsightEntity i where i.id = :id and i.ownerUserId = :owner and i.sessionId = :session")
    fun lockOwned(id: String, owner: UUID, session: UUID): WorkoutInsightEntity?
}
