package com.fitmate.backend.ops.repository
import com.fitmate.backend.ops.domain.IdempotencyKeyRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyRecord, UUID> {
    fun findByUserIdAndIdempotencyKey(userId: UUID, idempotencyKey: UUID): IdempotencyKeyRecord?
    fun findByIdempotencyKey(idempotencyKey: UUID): IdempotencyKeyRecord?
}
