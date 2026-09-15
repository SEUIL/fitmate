package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Clock
import java.util.Locale
import java.util.UUID

internal fun normalizeRuleText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[\\s\\p{Z}]+"), " ").trim()

/** Marks review candidates only. It never hides content or changes an account. */
@Service
class ModerationRuleEngine(private val rules: ModerationRuleRepository, private val events: RuleEventRepository,
    private val detections: RuleDetectionRepository, private val jdbc: JdbcTemplate, private val clock: Clock) {
    @Transactional
    fun beginWrite(userId: UUID) {
        val id = java.nio.ByteBuffer.allocate(16).putLong(userId.mostSignificantBits).putLong(userId.leastSignificantBits).array()
        // The duplicate-key update takes an exclusive lock directly, avoiding shared-lock upgrades.
        jdbc.update("INSERT INTO ops_rule_writer_locks (user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id = user_id", id)
    }
    @Transactional
    fun inspect(userId: UUID, scope: RuleScope, contentId: UUID, text: String, publiclyVisible: Boolean, created: Boolean) {
        beginWrite(userId)
        val now = clock.instant()
        events.deleteAllByUserIdAndOccurredAtBefore(userId, now.minusSeconds(3600))
        if (!publiclyVisible) { events.findById(contentId).ifPresent(events::delete); return }
        val applicable = rules.findAllByEnabledTrueAndScope(scope)
        if (applicable.isEmpty()) return
        val normalized = normalizeRuleText(text)
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        val old = events.findById(contentId).orElse(null)
        // Editing never increments writing counts; its latest hash replaces the original hash.
        if (created || old != null) events.saveAndFlush(RuleEvent(contentId, userId, scope, hash, old?.occurredAt ?: now))
        val recent = events.findAllByUserIdAndScopeAndOccurredAtAfter(userId, scope, now.minusSeconds(3600))
        for (rule in applicable) {
            val count = recent.count { it.occurredAt.isAfter(now.minusSeconds(rule.windowSeconds.toLong())) && !it.occurredAt.isAfter(now) && (rule.kind == RuleKind.RATE || it.contentHash == hash) }
            val match = when (rule.kind) {
                RuleKind.WORD -> normalizeRuleText(rule.term).let { it.isNotEmpty() && normalized.contains(it) }
                RuleKind.REPEAT, RuleKind.RATE -> created && count >= rule.thresholdCount
            }
            if (match && !detections.existsByRuleIdAndRuleVersionAndContentId(requireNotNull(rule.id), rule.version, contentId)) {
                val summary = if (rule.kind == RuleKind.WORD) "정규화한 단어가 본문에 포함됨" else "${rule.windowSeconds}초 내 ${count}건 (기준 ${rule.thresholdCount}건)"
                detections.save(RuleDetection(UUID.randomUUID(), rule.id, rule.version, rule.name, scope, contentId, userId, summary, createdAt = now))
            }
        }
    }
}
