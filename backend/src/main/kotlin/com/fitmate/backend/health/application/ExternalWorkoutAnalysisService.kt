package com.fitmate.backend.health.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.health.api.ExternalWorkoutAnalysisResponse
import com.fitmate.backend.health.api.HeartRateAnalysisResponse
import com.fitmate.backend.health.api.HeartRateChangeResponse
import com.fitmate.backend.health.api.PaceAnalysisResponse
import com.fitmate.backend.health.api.TimeOffsetSegmentResponse
import com.fitmate.backend.health.api.TimeSeriesAlignmentResponse
import com.fitmate.backend.health.repository.ExternalWorkoutSampleRepository
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.running.api.RunningRoutePoint
import com.fitmate.backend.running.repository.RunningRecordRepository
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ExternalWorkoutAnalysisService(
    private val sessions: WorkoutSessionRepository,
    private val summaries: ExternalWorkoutSummaryRepository,
    private val samples: ExternalWorkoutSampleRepository,
    private val running: RunningRecordRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun get(userId: UUID, sessionId: UUID): ExternalWorkoutAnalysisResponse {
        val storedId = UuidBinaryConverter.toMysqlOrderedUuid(sessionId)
        sessions.findByIdAndUserId(storedId, userId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val summary = summaries.findByWorkoutSessionIdAndUserId(storedId, userId)
            ?: return unavailable("TIME_SERIES_NOT_CONNECTED")
        val values = samples.findAllByWorkoutSessionIdOrderByMeasuredAtAsc(sessionId)
            .map { TimeSeriesValue(it.measuredAt, it.heartRate, it.speedMetersPerSecond) }
        val route = running.findById(storedId).orElse(null)?.routeJson?.let { json ->
            objectMapper.readValue(json, Array<RunningRoutePoint>::class.java).map { point ->
                RoutePointForAnalysis(point.latitude.toDouble(), point.longitude.toDouble(), point.recordedAt)
            }
        }.orEmpty()
        val result = ExternalWorkoutTimeSeriesAnalyzer.analyze(summary.startedAt, summary.endedAt, values, ExternalWorkoutTimeSeriesAnalyzer.speedFromRoute(route))
        val limitations = buildList {
            result.heartRate.unavailableReason?.let(::add)
            result.pace.unavailableReason?.let(::add)
            if (result.heartRate.longGapCount > 0) add("HEART_RATE_LONG_GAPS")
            if (result.heartRate.unavailableReason == null && result.heartRate.coveragePercent < 80) add("HEART_RATE_LOW_COVERAGE")
            if (result.pace.samsungSampleCount > 0 && result.pace.samsungSampleCount < values.size) add("PACE_PARTIAL_SAMPLES")
            if (result.alignment.alignedPairCount == 0 && result.heartRate.validSampleCount > 0 &&
                result.pace.samsungSampleCount + result.pace.gpsSampleCount > 0) add("HEART_RATE_PACE_NOT_ALIGNED")
        }
        return ExternalWorkoutAnalysisResponse(
            analysisVersion = "external-timeseries-v1",
            available = result.heartRate.unavailableReason == null || result.pace.unavailableReason == null,
            limitations = limitations,
            heartRate = HeartRateAnalysisResponse(result.heartRate.validSampleCount, result.heartRate.coveragePercent,
                result.heartRate.longGapCount, result.heartRate.maxGapSeconds, result.heartRate.peakBpm,
                result.heartRate.peakOffsetSeconds, result.heartRate.peakSegment?.let { TimeOffsetSegmentResponse(it.startOffsetSeconds, it.endOffsetSeconds, it.sampleCount) },
                result.heartRate.biggestRise?.let { HeartRateChangeResponse(it.startOffsetSeconds, it.endOffsetSeconds, it.changeBpm) },
                result.heartRate.biggestFall?.let { HeartRateChangeResponse(it.startOffsetSeconds, it.endOffsetSeconds, it.changeBpm) }, result.heartRate.unavailableReason),
            pace = PaceAnalysisResponse(result.pace.samsungSampleCount, result.pace.gpsSampleCount,
                result.pace.samsungSourceUsed, result.pace.gpsSourceUsed,
                result.pace.samsungFastestPaceSecondsPerKm, result.pace.smartphoneGpsFastestPaceSecondsPerKm,
                result.pace.unavailableReason),
            alignment = TimeSeriesAlignmentResponse(result.alignment.alignedPairCount, result.alignment.windowSeconds),
        )
    }

    private fun unavailable(reason: String) = ExternalWorkoutAnalysisResponse(
        analysisVersion = "external-timeseries-v1", available = false, limitations = listOf(reason),
        heartRate = HeartRateAnalysisResponse(0, 0, 0, 0, unavailableReason = reason),
        pace = PaceAnalysisResponse(0, 0, false, false, null, null, reason), alignment = TimeSeriesAlignmentResponse(0, ALIGNMENT_WINDOW_SECONDS),
    )
}
