package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.domain.*
import com.fitmate.backend.admin.repository.*
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.*
import com.fitmate.backend.community.domain.PostVisibility
import com.fitmate.backend.community.repository.CommunityPostRepository
import com.fitmate.backend.community.repository.CommunityPostCommentRepository
import com.fitmate.backend.mate.domain.MateVisibility
import com.fitmate.backend.mate.domain.MatePostStatus
import com.fitmate.backend.mate.repository.MatePostRepository
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.repository.UserProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class AdminRuleService(private val rules: ModerationRuleRepository, private val detections: RuleDetectionRepository,
    private val access: AdminToolAccess, private val posts: CommunityPostRepository, private val comments: CommunityPostCommentRepository,
    private val mates: MatePostRepository, private val profiles: UserProfileRepository, private val clock: Clock,
    private val revisions: RuleRevisionRepository, private val mapper: com.fasterxml.jackson.databind.ObjectMapper) {
    @Transactional(readOnly = true)
    fun history(user: AuthenticatedUser, id: UUID, cursor: String?): RuleRevisionPage {
        access.requireManager(user)
        val rows = revisions.findAllByRuleIdOrderByRuleVersionDesc(id, page(cursor))
        return RuleRevisionPage(rows.content.map { RuleRevisionView(mapper.readValue(it.configurationJson, RuleView::class.java), it.reason, it.createdAt) }, next(rows.hasNext(), rows.number))
    }
    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, cursor: String?): RulePage {
        access.requireManager(user)
        val page = rules.findAllByOrderByCreatedAtDescIdDesc(page(cursor))
        return RulePage(page.content.map(::view), next(page.hasNext(), page.number))
    }
    fun save(user: AuthenticatedUser, id: UUID, key: UUID, request: RuleWrite): RuleView =
        access.command(user, key, "rule:$id", request, RuleView::class.java) {
            validate(request)
            val row = rules.findForUpdate(id)
            if ((row?.version ?: 0) != request.expectedVersion) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            if (row != null && row.name == request.name.trim() && row.kind == request.kind && row.scope == request.scope &&
                row.term == request.term.trim() && row.thresholdCount == request.thresholdCount && row.windowSeconds == request.windowSeconds && row.enabled == request.enabled) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "변경된 규칙 내용이 없습니다.")
            }
            val value = row ?: ModerationRule(id)
            value.name = request.name.trim(); value.kind = request.kind; value.scope = request.scope
            value.term = request.term.trim(); value.thresholdCount = request.thresholdCount; value.windowSeconds = request.windowSeconds; value.enabled = request.enabled
            rules.saveAndFlush(value)
            revisions.save(RuleRevision(UUID.randomUUID(), id, value.version, user.userId, mapper.writeValueAsString(view(value)), request.reason.trim(), clock.instant()))
            access.audit(user, "RULE_CHANGED", "RULE", id, "${value.name}: ${request.reason.trim()}")
            view(value)
        }
    fun preview(user: AuthenticatedUser, request: RulePreview): RulePreviewResult {
        access.requireManager(user); validate(request.rule)
        val rule = request.rule
        val matched = if (rule.kind == RuleKind.WORD) normalizeRuleText(request.sampleText).contains(normalizeRuleText(rule.term))
            else request.priorMatchingCount + 1 >= rule.thresholdCount
        return RulePreviewResult(matched, if (rule.kind == RuleKind.WORD) "문자 폭·대소문자·연속 공백을 정규화한 포함 일치입니다."
            else "같은 작성자·영역에서 지정 시간 안에 이전 ${request.priorMatchingCount}건과 현재 1건이 있다고 가정합니다. 샘플은 저장하지 않습니다.")
    }
    @Transactional(readOnly = true)
    fun candidates(user: AuthenticatedUser, reviewed: Boolean, cursor: String?): DetectionPage {
        access.requireManager(user)
        val rows = detections.findAllByReviewedOrderByCreatedAtDescIdDesc(reviewed, page(cursor))
        return DetectionPage(rows.content.map { detection(it) }, next(rows.hasNext(), rows.number))
    }
    @Transactional
    fun candidate(user: AuthenticatedUser, id: UUID): DetectionView {
        access.requireManager(user)
        val row = detections.findById(id).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        access.audit(user, "RULE_CANDIDATE_VIEWED", "RULE_DETECTION", id, "탐지 후보의 현재 공개 원문을 열람했습니다.")
        return detection(row, currentText(row))
    }
    fun review(user: AuthenticatedUser, id: UUID, key: UUID, request: DetectionReview): DetectionView =
        access.command(user, key, "rule-detection:$id", request, DetectionView::class.java) {
            val row = detections.findForUpdate(id) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            if (row.reviewed) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            row.reviewed = true; row.reviewReason = request.reason.trim(); row.reviewedBy = user.userId; row.reviewedAt = clock.instant()
            detections.saveAndFlush(row)
            access.audit(user, "RULE_CANDIDATE_REVIEWED", "RULE_DETECTION", id, request.reason.trim())
            detection(row)
        }
    private fun currentText(row: RuleDetection): String? {
        fun publicProfile(id: UUID?) = id?.let { profiles.findById(it).orElse(null) }?.let { it.deletedAt == null && it.visibility == ProfileVisibility.PUBLIC } == true
        fun publicPost(id: UUID) = posts.findByIdAndDeletedAtIsNull(id)?.takeIf { it.visibility == PostVisibility.PUBLIC && publicProfile(it.authorUserId) }
        return when (row.scope) {
            RuleScope.POST -> publicPost(requireNotNull(row.contentId))?.content
            RuleScope.COMMENT -> comments.findByIdAndDeletedAtIsNull(requireNotNull(row.contentId))?.takeIf {
                publicPost(requireNotNull(it.postId)) != null && publicProfile(it.authorUserId)
            }?.content
            RuleScope.MATE -> mates.findById(requireNotNull(row.contentId)).orElse(null)?.takeIf { it.visibility == MateVisibility.PUBLIC && it.status != MatePostStatus.CANCELED }?.let { "${it.title}\n${it.body}" }
        }
    }
    private fun validate(r: RuleWrite) {
        if (r.name.isBlank() || r.reason.isBlank() || (r.kind == RuleKind.WORD && normalizeRuleText(r.term).isBlank())) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
    }
    private fun view(r: ModerationRule) = RuleView(requireNotNull(r.id), r.name, r.kind, r.scope, r.term, r.thresholdCount, r.windowSeconds, r.enabled, r.version)
    private fun detection(d: RuleDetection, text: String? = null) = DetectionView(requireNotNull(d.id), d.ruleName, d.ruleVersion, d.scope,
        requireNotNull(d.contentId), requireNotNull(d.userId), d.summary, d.reviewed, d.reviewReason, d.createdAt, text)
    private fun page(cursor: String?) = PageRequest.of(try { cursor?.let { String(Base64.getUrlDecoder().decode(it)).toInt().also { n -> require(n in 0..1000000) } } ?: 0
        } catch (_: IllegalArgumentException) { throw ApiException(ApiErrorCode.VALIDATION_ERROR) }, 20)
    private fun next(hasNext: Boolean, page: Int) = if (hasNext) Base64.getUrlEncoder().withoutPadding().encodeToString((page + 1).toString().toByteArray()) else null
}
