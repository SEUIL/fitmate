package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminSystemService
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AdminSystemCount(val code: String, val count: Long)
data class AdminSystemMetric(val availability: String, val value: Double? = null)
data class AdminSystemSummary(
    val from: Instant, val to: Instant, val asOf: Instant,
    val cohort: String = "CREATED_AT",
    val statuses: List<AdminSystemCount>, val failures: List<AdminSystemCount>,
    val failureRate: Double?,
    val apiErrorRate: AdminSystemMetric = AdminSystemMetric("UNAVAILABLE"),
    val apiLatencyMs: AdminSystemMetric = AdminSystemMetric("UNAVAILABLE"),
    val activeUsers: AdminSystemMetric = AdminSystemMetric("UNAVAILABLE"),
)

@RestController
@RequestMapping("/api/v1/admin/system")
class AdminSystemController(private val service: AdminSystemService) {
    @GetMapping("/summary")
    fun summary(@AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) from: Instant?, @RequestParam(required = false) to: Instant?) =
        ApiResponse.success(service.summary(user, from, to))
}
