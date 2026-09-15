package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.AdminSystemCount
import com.fitmate.backend.admin.api.AdminSystemSummary
import com.fitmate.backend.admin.domain.AdminRole
import com.fitmate.backend.admin.repository.AdminRoleRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class AdminSystemService(private val roles: AdminRoleRepository,
    private val analyses: InbodyAnalysisRepository, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun summary(user: AuthenticatedUser, from: Instant?, to: Instant?): AdminSystemSummary {
        val role = roles.findByUserIdAndRevokedAtIsNull(user.userId)?.role
        if (role !in setOf(AdminRole.ADMIN, AdminRole.SUPER_ADMIN)) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
        val now = clock.instant()
        val end = to ?: now
        val start = from ?: end.minus(Duration.ofDays(1))
        if (!start.isBefore(end) || end.isAfter(now) || Duration.between(start, end) > Duration.ofDays(31)) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "조회 기간은 과거 31일 이내 길이로 지정해 주세요.")
        }
        val counts = analyses.countStatuses(start, end).associate { it.status to it.total }
        val failed = counts[InbodyAnalysisStatus.FAILED] ?: 0L
        val finished = failed + (counts[InbodyAnalysisStatus.COMPLETED] ?: 0L) + (counts[InbodyAnalysisStatus.REVIEW_REQUIRED] ?: 0L)
        // Aggregate projections deliberately exclude user IDs, OCR payloads and failure messages.
        val failures = analyses.countFailureCodes(start, end, InbodyAnalysisStatus.FAILED)
            .groupBy { it.code?.takeIf { value -> value.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) } ?: "UNKNOWN" }
            .map { (code, rows) -> AdminSystemCount(code, rows.sumOf { it.total }) }
            .sortedWith(compareByDescending<AdminSystemCount> { it.count }.thenBy { it.code })
        return AdminSystemSummary(start, end, now,
            statuses = InbodyAnalysisStatus.entries.map { AdminSystemCount(it.name, counts[it] ?: 0L) },
            failures = failures, failureRate = if (finished == 0L) null else failed.toDouble() / finished)
    }
}
