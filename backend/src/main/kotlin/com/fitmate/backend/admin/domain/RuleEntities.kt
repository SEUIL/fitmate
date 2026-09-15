package com.fitmate.backend.admin.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class RuleKind { WORD, REPEAT, RATE }
enum class RuleScope { POST, COMMENT, MATE }
@Entity @Table(name = "ops_rule_revisions")
class RuleRevision(@Id @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "rule_id", columnDefinition = "BINARY(16)") var ruleId: UUID? = null,
    @Column(name = "rule_version") var ruleVersion: Long = 0,
    @Column(name = "actor_id", columnDefinition = "BINARY(16)") var actorId: UUID? = null,
    @Column(name = "configuration_json", columnDefinition = "TEXT") var configurationJson: String = "",
    @Column(length = 300) var reason: String = "", @Column(name = "created_at") var createdAt: Instant = Instant.EPOCH)
@Entity @Table(name = "ops_rules")
class ModerationRule(@Id @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(length = 80) var name: String = "",
    @Enumerated(EnumType.STRING) var kind: RuleKind = RuleKind.WORD,
    @Enumerated(EnumType.STRING) var scope: RuleScope = RuleScope.POST,
    @Column(length = 100) var term: String = "",
    @Column(name = "threshold_count") var thresholdCount: Int = 3,
    @Column(name = "window_seconds") var windowSeconds: Int = 60,
    var enabled: Boolean = false, @Version var version: Long = 0) : BaseTimeEntity()

@Entity @Table(name = "ops_rule_events")
class RuleEvent(@Id @Column(name = "content_id", columnDefinition = "BINARY(16)") var contentId: UUID? = null,
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Enumerated(EnumType.STRING) var scope: RuleScope = RuleScope.POST,
    @Column(name = "content_hash", length = 64) var contentHash: String = "",
    @Column(name = "occurred_at") var occurredAt: Instant = Instant.EPOCH)

@Entity @Table(name = "ops_rule_detections")
class RuleDetection(@Id @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "rule_id", columnDefinition = "BINARY(16)") var ruleId: UUID? = null,
    @Column(name = "rule_version") var ruleVersion: Long = 0,
    @Column(name = "rule_name", length = 80) var ruleName: String = "",
    @Enumerated(EnumType.STRING) var scope: RuleScope = RuleScope.POST,
    @Column(name = "content_id", columnDefinition = "BINARY(16)") var contentId: UUID? = null,
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(length = 300) var summary: String = "", var reviewed: Boolean = false,
    @Column(name = "review_reason", length = 300) var reviewReason: String? = null,
    @Column(name = "reviewed_by", columnDefinition = "BINARY(16)") var reviewedBy: UUID? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.EPOCH)
