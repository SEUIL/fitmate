package com.fitmate.backend.admin.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fitmate.backend.admin.domain.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

data class RuleWrite(@param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long,
    @field:NotBlank @field:Size(max = 80) val name: String, val kind: RuleKind, val scope: RuleScope,
    @field:Size(max = 100) val term: String = "", @field:Min(2) @field:Max(100) val thresholdCount: Int = 3,
    @field:Min(10) @field:Max(3600) val windowSeconds: Int = 60,
    @param:JsonProperty(required = true) val enabled: Boolean,
    @field:NotBlank @field:Size(max = 300) val reason: String)
data class RulePreview(@field:Valid val rule: RuleWrite, @field:Size(max = 5000) val sampleText: String,
    @field:Min(0) @field:Max(100) val priorMatchingCount: Int = 0)
data class RuleView(val id: UUID, val name: String, val kind: RuleKind, val scope: RuleScope, val term: String,
    val thresholdCount: Int, val windowSeconds: Int, val enabled: Boolean, val version: Long)
data class RulePage(val items: List<RuleView>, val nextCursor: String?)
data class RuleRevisionView(val rule: RuleView, val reason: String, val createdAt: Instant)
data class RuleRevisionPage(val items: List<RuleRevisionView>, val nextCursor: String?)
data class RulePreviewResult(val matched: Boolean, val explanation: String)
data class DetectionView(val id: UUID, val ruleName: String, val ruleVersion: Long, val scope: RuleScope,
    val contentId: UUID, val userId: UUID, val summary: String, val reviewed: Boolean, val reviewReason: String?,
    val createdAt: Instant, val currentText: String? = null)
data class DetectionPage(val items: List<DetectionView>, val nextCursor: String?)
data class DetectionReview(@field:NotBlank @field:Size(max = 300) val reason: String)
