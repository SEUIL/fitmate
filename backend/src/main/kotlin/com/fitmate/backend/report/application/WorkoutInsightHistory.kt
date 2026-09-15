package com.fitmate.backend.report.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.infrastructure.WorkoutInsightRepository
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Bounded, owner-scoped history. Stored narratives are advice, never evidence of performance. */
@Service
class WorkoutInsightHistory(
    private val sessions: WorkoutSessionRepository,
    private val reports: WorkoutReportService,
    private val bodies: InbodyRecordRepository,
    private val samsung: ExternalWorkoutSummaryRepository,
    private val insights: WorkoutInsightRepository,
    private val mapper: ObjectMapper,
) {
    fun validate(r: CreateWorkoutInsightRequest) {
        if (r.historyWeeks !in listOf(4, 8, 12) || r.inbodyRecordIds.size > 12 ||
            r.inbodyRecordIds.distinct().size != r.inbodyRecordIds.size ||
            (r.inbodyRecordId != null && r.inbodyRecordIds.isNotEmpty()))
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "기간은 4·8·12주, 인바디는 중복 없이 최대 12개를 선택해 주세요.")
    }

    fun read(user: UUID, current: WorkoutReportResponse, r: CreateWorkoutInsightRequest): InsightLongitudinal {
        validate(r)
        val from = current.endedAt.minus(r.historyWeeks * 7L, ChronoUnit.DAYS)
        val demo = isDemo(current)
        val slice = sessions.insightHistory(user, SportCode.HEALTH, WorkoutSessionStatus.COMPLETED,
            from, current.endedAt, PageRequest.of(0, 40))
        val history = slice.content.filter { apiId(requireNotNull(it.id)) != current.workoutSessionId }
            .map { reports.get(user, apiId(requireNotNull(it.id))) }
            .filter { isDemo(it) == demo && it.endedAt <= current.startedAt }
            .sortedBy { it.startedAt }
        val selected = r.inbodyRecordIds.map { id ->
            val body = bodies.findByIdAndUserId(id, user)
                ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "선택한 인바디 기록을 찾을 수 없습니다.")
            if (body.measuredDate < from.atZone(ZoneOffset.UTC).toLocalDate() || body.measuredDate > current.endedAt.atZone(ZoneOffset.UTC).toLocalDate())
                throw ApiException(ApiErrorCode.VALIDATION_ERROR, "선택 기간 안이며 운동일 이전인 인바디를 선택해 주세요.")
            InsightBodyComposition(body.measuredDate, body.bodyFatPercentage, body.skeletalMuscleMassKg)
        }.sortedBy { it.measuredDate }
        val advice = if (!r.includePreviousReports) emptyList() else history.asReversed().asSequence().mapNotNull { previous ->
            insights.findFirstByOwnerUserIdAndSessionIdAndStatusAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
                user, dbId(previous.workoutSessionId), "READY", current.startedAt)?.let { entity ->
                entity.resultJson?.let { json ->
                    val result = mapper.readValue<WorkoutInsightResult>(json)
                    InsightPreviousAdvice(previous.workoutSessionId, entity.createdAt, result.nextActions.take(2))
                }
            }
        }.take(3).toList()
        return InsightLongitudinal(r.historyWeeks, from, current.endedAt, demo,
            history.map { previous -> InsightHistorySession(previous, if (r.includeSamsungHealth) samsung.findByWorkoutSessionIdAndUserId(
                dbId(previous.workoutSessionId), user)?.let { InsightSamsungSummary(it.activeDurationSeconds, it.caloriesKcal, it.meanHeartRate) } else null) },
            selected, advice, slice.hasNext())
    }

    fun options(user: UUID, current: WorkoutReportResponse, weeks: Int): Map<String, Any> {
        val history = read(user, current, CreateWorkoutInsightRequest(UUID.randomUUID(), historyWeeks = weeks, includeSamsungHealth = true))
        val from = history.from.atZone(ZoneOffset.UTC).toLocalDate()
        val through = history.through.atZone(ZoneOffset.UTC).toLocalDate()
        val available = bodies.findAllByUserIdOrderByMeasuredDateDescCreatedAtDesc(user)
            .filter { it.measuredDate in from..through }.take(12)
        return mapOf("historyWeeks" to weeks, "historySessionCount" to history.sessions.size,
            "historyTruncated" to history.truncated, "demo" to history.demo,
            "fromDate" to from.toString(), "throughDate" to through.toString(),
            "samsungHistoryCount" to history.sessions.count { it.samsungHealth != null },
            "inbodyOptions" to available.map { mapOf("id" to requireNotNull(it.id).toString(), "measuredDate" to it.measuredDate.toString()) })
    }

    companion object {
        fun isDemo(report: WorkoutReportResponse) = report.exercises.any { it.exerciseName.startsWith("[DEMO]") }
        private fun apiId(id: UUID) = UuidBinaryConverter.fromMysqlOrderedUuid(id)
        private fun dbId(id: UUID) = UuidBinaryConverter.toMysqlOrderedUuid(id)
    }
}
