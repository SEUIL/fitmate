package com.fitmate.backend.report.api

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateWorkoutInsightRequest(
    val requestId: UUID,
    val inbodyRecordId: UUID? = null,
    val includeSamsungHealth: Boolean = false,
    val externalAiConsent: Boolean = false,
    val consentVersion: String? = null,
    val historyWeeks: Int = 4,
    val inbodyRecordIds: List<UUID> = emptyList(),
    val includePreviousReports: Boolean = false,
)

data class InsightBodyComposition(val measuredDate: LocalDate, val bodyFatPercentage: BigDecimal, val skeletalMuscleMassKg: BigDecimal)
data class InsightSamsungSummary(val activeDurationSeconds: Long, val caloriesKcal: BigDecimal?, val meanHeartRate: BigDecimal?)
data class WorkoutInsightSnapshot(
    val report: WorkoutReportResponse,
    val goal: String?,
    val skillLevel: String?,
    val inbody: InsightBodyComposition?,
    val samsungHealth: InsightSamsungSummary?,
    val notices: List<String>,
    val longitudinal: InsightLongitudinal? = null,
)

data class InsightHistorySession(val report: WorkoutReportResponse, val samsungHealth: InsightSamsungSummary?)
data class InsightPreviousAdvice(val sessionId: UUID, val createdAt: Instant, val actions: List<InsightPoint>)
data class InsightLongitudinal(
    val weeks: Int, val from: Instant, val through: Instant, val demo: Boolean,
    val sessions: List<InsightHistorySession>, val bodies: List<InsightBodyComposition>,
    val previousAdvice: List<InsightPreviousAdvice>, val truncated: Boolean = false,
)
data class InsightPoint(val id: String, val title: String, val explanation: String, val evidence: List<String>)
data class InsightCatalog(val summaries: List<InsightPoint>, val observations: List<InsightPoint>, val actions: List<InsightPoint>)
data class InsightNarrativePoint(val title: String, val explanation: String, val evidenceIds: List<String>)
data class InsightNarrative(val summary: InsightNarrativePoint, val observations: List<InsightNarrativePoint>, val nextActions: List<InsightNarrativePoint>)
data class InsightSelection(val summaryId: String = "", val observationIds: List<String> = emptyList(), val actionIds: List<String> = emptyList(), val narrative: InsightNarrative? = null)
data class WorkoutInsightResult(
    val source: String,
    val summary: InsightPoint,
    val observations: List<InsightPoint>,
    val nextActions: List<InsightPoint>,
    val snapshot: WorkoutInsightSnapshot,
    val methodVersion: String = "grounded-selection-v1",
)
data class WorkoutInsightResponse(
    val id: UUID, val status: String, val createdAt: Instant,
    val result: WorkoutInsightResult?, val message: String?,
)
