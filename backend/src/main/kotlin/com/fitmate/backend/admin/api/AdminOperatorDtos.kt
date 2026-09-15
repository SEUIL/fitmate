package com.fitmate.backend.admin.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.auth.domain.AccountStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class OperatorSummary(val userId: UUID, val nickname: String?, val role: AdminRole,
    val enabled: Boolean, val accountStatus: AccountStatus, val grantedAt: Instant, val revokedAt: Instant?)
data class OperatorCatalog(val version: Long, val items: List<OperatorSummary>, val nextCursor: String?, val actorId: UUID)
data class OperatorCandidate(val userId: UUID, val nickname: String?, val accountStatus: AccountStatus,
    val currentRole: AdminRole?, val enabled: Boolean)
data class OperatorChange(
    @param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long,
    val role: AdminRole,
    @param:JsonProperty(required = true) val enabled: Boolean,
    @field:NotBlank @field:Size(max = 300) val reason: String,
)
data class OperatorChangeResult(val version: Long, val operator: OperatorSummary)
