package com.fitmate.backend.admin.repository

import com.fitmate.backend.admin.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RuleRevisionRepository : JpaRepository<RuleRevision, UUID> {
    fun findAllByRuleIdOrderByRuleVersionDesc(ruleId: UUID, page: Pageable): Page<RuleRevision>
}

interface ModerationRuleRepository : JpaRepository<ModerationRule, UUID> {
    fun findAllByOrderByCreatedAtDescIdDesc(page: Pageable): Page<ModerationRule>
    fun findAllByEnabledTrueAndScope(scope: RuleScope): List<ModerationRule>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ModerationRule r where r.id = :id")
    fun findForUpdate(id: UUID): ModerationRule?
}
interface RuleEventRepository : JpaRepository<RuleEvent, UUID> {
    fun findAllByUserIdAndScopeAndOccurredAtAfter(userId: UUID, scope: RuleScope, after: Instant): List<RuleEvent>
    fun deleteAllByUserIdAndOccurredAtBefore(userId: UUID, before: Instant)
}
interface RuleDetectionRepository : JpaRepository<RuleDetection, UUID> {
    fun existsByRuleIdAndRuleVersionAndContentId(ruleId: UUID, ruleVersion: Long, contentId: UUID): Boolean
    fun findAllByReviewedOrderByCreatedAtDescIdDesc(reviewed: Boolean, page: Pageable): Page<RuleDetection>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from RuleDetection d where d.id = :id")
    fun findForUpdate(id: UUID): RuleDetection?
}
