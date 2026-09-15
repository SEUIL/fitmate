package com.fitmate.backend.admin.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fitmate.backend.admin.domain.AppealStatus
import com.fitmate.backend.admin.domain.ModerationAction
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

data class AppealLogin(@field:Email @field:NotBlank val email: String, @field:Size(min = 1, max = 64) val password: String)
data class AppealCreate(val sanctionId: UUID, @field:NotBlank @field:Size(max = 2000) val statement: String)
data class AppealVersion(@param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long)
data class AppealDecision(@param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long,
    val status: AppealStatus, @field:NotBlank @field:Size(max = 500) val publicReason: String,
    @field:Size(max = 500) val internalNote: String? = null)
data class AppealView(val id: UUID, val sanctionId: UUID, val userId: UUID, val nickname: String?, val statement: String,
    val status: AppealStatus, val version: Long, val createdAt: Instant, val publicReason: String?, val reviewedAt: Instant?,
    val internalNote: String?, val canReview: Boolean, val sanction: AppealSanctionDetail)
data class AppealSanctionDetail(val action: ModerationAction, val reason: String, val startsAt: Instant,
    val endsAt: Instant?, val revokedAt: Instant?)
data class AppealPage(val items: List<AppealView>, val nextCursor: String?)
data class AppealSanction(val id: UUID, val action: ModerationAction, val reason: String, val startsAt: Instant,
    val endsAt: Instant?, val revokedAt: Instant?, val canAppeal: Boolean, val latestAppeal: AppealView?)
data class AppealSanctionPage(val items: List<AppealSanction>, val nextCursor: String?)
