package com.fitmate.backend.ops.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ops_idempotency_keys")
class IdempotencyKeyRecord(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "idempotency_key", nullable = false, columnDefinition = "BINARY(16)")
    var idempotencyKey: UUID? = null,
    @Column(name = "request_hash", nullable = false, length = 64)
    var requestHash: String = "",
    @Column(name = "response_status", nullable = false)
    var responseStatus: Int = 200,
    @Column(name = "response_body", columnDefinition = "json")
    var responseBody: String? = null,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
) : BaseTimeEntity()
