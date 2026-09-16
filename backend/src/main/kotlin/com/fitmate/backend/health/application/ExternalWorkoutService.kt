package com.fitmate.backend.health.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.health.api.ExternalWorkoutResponse
import com.fitmate.backend.health.api.PutExternalWorkoutRequest
import com.fitmate.backend.health.api.toResponse
import com.fitmate.backend.health.domain.ExternalWorkoutSample
import com.fitmate.backend.health.domain.ExternalWorkoutSummary
import com.fitmate.backend.health.repository.ExternalWorkoutSampleRepository
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ExternalWorkoutService(
    private val sessions: WorkoutSessionRepository,
    private val summaries: ExternalWorkoutSummaryRepository,
    private val samples: ExternalWorkoutSampleRepository,
) {
    @Transactional(readOnly = true)
    fun get(userId: UUID, sessionId: UUID): ExternalWorkoutResponse? {
        val storedSessionId = dbId(sessionId)
        sessions.findByIdAndUserId(storedSessionId, userId) ?: notFound()
        return summaries.findByWorkoutSessionIdAndUserId(storedSessionId, userId)?.asResponse()
    }

    @Transactional
    fun put(userId: UUID, sessionId: UUID, request: PutExternalWorkoutRequest): ExternalWorkoutResponse {
        val storedSessionId = dbId(sessionId)
        val session = sessions.findByIdAndUserIdForUpdate(storedSessionId, userId) ?: notFound()
        validate(request)
        if (session.status != WorkoutSessionStatus.COMPLETED || session.endedAt == null) conflict("완료된 운동에만 연결할 수 있습니다.")
        if (!allowed(session.sportCode, request.exerciseType)) badRequest("이 세션과 연결할 수 없는 운동 종류입니다.")
        if (!matchesTime(session.startedAt, requireNotNull(session.endedAt), request.startedAt, request.endedAt)) {
            badRequest("운동 시작·종료 시각이 세션과 일치하지 않습니다.")
        }

        val normalized = normalize(request)
        val existing = summaries.findOwnedForUpdate(storedSessionId, userId)
        if (existing != null) return updateExisting(existing, normalized, request.expectedVersion)

        summaries.findByUserIdAndProviderAndSourceAppIdAndSourceRecordId(
            userId, normalized.provider, normalized.sourceAppId, normalized.sourceRecordId,
        )?.let { conflict("이 원본 운동은 다른 세션에 이미 연결되어 있습니다.") }
        if (request.expectedVersion != null) conflict("새 연결에는 expectedVersion을 보내지 않습니다.")

        val entity = ExternalWorkoutSummary(workoutSessionId = storedSessionId, userId = userId).also { apply(it, normalized) }
        return try {
            summaries.saveAndFlush(entity)
            replaceSamples(storedSessionId, normalized)
            entity.asResponse()
        }
        catch (_: DataIntegrityViolationException) { conflict("이 원본 운동은 이미 연결되어 있습니다.") }
        catch (_: CannotAcquireLockException) { conflict("동시에 같은 원본 운동을 연결할 수 없습니다.") }
    }

    private fun updateExisting(
        entity: ExternalWorkoutSummary,
        request: NormalizedWorkout,
        expectedVersion: Long?,
    ): ExternalWorkoutResponse {
        if (entity.sourceRecordId != request.sourceRecordId || entity.sourceAppId != request.sourceAppId || entity.provider != request.provider) {
            conflict("이미 연결된 원본을 다른 원본으로 교체할 수 없습니다.")
        }
        val existingSeriesHash = entity.timeSeriesHash.ifBlank { sha256("") }
        if (entity.payloadHash == request.payloadHash && existingSeriesHash == request.timeSeriesHash) return entity.asResponse()
        if (expectedVersion == null || expectedVersion != entity.version) conflict("저장된 요약 버전이 변경되었습니다.")
        if (entity.sourceModifiedAt != null && request.sourceModifiedAt != null && request.sourceModifiedAt.isBefore(entity.sourceModifiedAt)) {
            conflict("기기에 저장된 최신 원본보다 오래된 내용입니다.")
        }
        val seriesChanged = existingSeriesHash != request.timeSeriesHash
        apply(entity, request)
        summaries.saveAndFlush(entity)
        if (seriesChanged) replaceSamples(requireNotNull(entity.workoutSessionId), request)
        return entity.asResponse()
    }

    @Transactional
    fun delete(userId: UUID, sessionId: UUID) {
        val storedSessionId = dbId(sessionId)
        sessions.findByIdAndUserIdForUpdate(storedSessionId, userId) ?: notFound()
        summaries.findOwnedForUpdate(storedSessionId, userId)?.let(summaries::delete)
    }

    private fun validate(r: PutExternalWorkoutRequest) {
        if (r.provider != ExternalWorkoutSummary.PROVIDER) badRequest("지원하지 않는 provider입니다.")
        if (r.sourceAppId != null && r.sourceAppId.isEmpty()) badRequest("sourceAppId는 빈 문자열일 수 없습니다.")
        if (!r.startedAt.isBefore(r.endedAt)) badRequest("startedAt은 endedAt보다 이전이어야 합니다.")
        val elapsed = Duration.between(r.startedAt, r.endedAt).seconds
        if (r.activeDurationSeconds > elapsed + 1) badRequest("활동 시간이 전체 경과 시간을 초과합니다.")
        listOf(r.meanHeartRate, r.minHeartRate, r.maxHeartRate).filterNotNull().forEach {
            if (it > BigDecimal("300")) badRequest("심박수는 300 bpm 이하여야 합니다.")
        }
        if (r.minHeartRate != null && r.maxHeartRate != null && r.minHeartRate > r.maxHeartRate) badRequest("최저 심박수가 최고 심박수보다 큽니다.")
        if (r.meanHeartRate != null && r.minHeartRate != null && r.meanHeartRate < r.minHeartRate) badRequest("평균 심박수가 최저 심박수보다 작습니다.")
        if (r.meanHeartRate != null && r.maxHeartRate != null && r.meanHeartRate > r.maxHeartRate) badRequest("평균 심박수가 최고 심박수보다 큽니다.")
        if (r.samples.size > MAX_SAMPLES) badRequest("시계열 샘플 수가 너무 많습니다.")
        if (r.samples.any { it.heartRate == null && it.speedMetersPerSecond == null }) badRequest("시계열 샘플에는 심박수 또는 속도가 필요합니다.")
        if (r.samples.any { it.heartRate != null && (it.heartRate <= BigDecimal.ZERO || it.heartRate > BigDecimal("300")) }) badRequest("시계열 심박수 범위가 올바르지 않습니다.")
        if (r.samples.any { it.speedMetersPerSecond != null && (it.speedMetersPerSecond < BigDecimal.ZERO || it.speedMetersPerSecond > BigDecimal("15")) }) badRequest("시계열 속도 범위가 올바르지 않습니다.")
        if (r.samples.any { it.measuredAt.isBefore(r.startedAt) || it.measuredAt.isAfter(r.endedAt) }) badRequest("시계열 시각은 원본 운동 구간 안이어야 합니다.")
        if (r.samples.zipWithNext().any { (before, after) -> !after.measuredAt.isAfter(before.measuredAt) }) {
            badRequest("시계열 시각은 중복 없이 오름차순이어야 합니다.")
        }
    }

    private fun allowed(sport: SportCode, type: String): Boolean = when (sport) {
        SportCode.HEALTH -> type in HEALTH_EXERCISE_TYPES
        SportCode.RUNNING -> type == "RUNNING"
        else -> false
    }

    private fun matchesTime(sessionStart: Instant, sessionEnd: Instant, sourceStart: Instant, sourceEnd: Instant): Boolean {
        val tolerance = Duration.ofMinutes(10)
        return Duration.between(sessionStart, sourceStart).abs() <= tolerance &&
            Duration.between(sessionEnd, sourceEnd).abs() <= tolerance &&
            sourceStart.isBefore(sessionEnd) && sessionStart.isBefore(sourceEnd)
    }

    private fun normalize(r: PutExternalWorkoutRequest): NormalizedWorkout {
        fun decimal(value: BigDecimal?) = value?.setScale(2, RoundingMode.HALF_UP)
        val series = r.samples.map {
            NormalizedSample(
                it.measuredAt.truncatedTo(ChronoUnit.MILLIS),
                it.heartRate?.setScale(2, RoundingMode.HALF_UP),
                it.speedMetersPerSecond?.setScale(3, RoundingMode.HALF_UP),
            )
        }
        if (series.zipWithNext().any { (before, after) -> !after.measuredAt.isAfter(before.measuredAt) }) {
            badRequest("밀리초 기준으로 중복되거나 역순인 시계열 시각은 저장할 수 없습니다.")
        }
        val value = NormalizedWorkout(
            r.provider, r.sourceRecordId, r.sourceAppId, r.sourceModifiedAt?.truncatedTo(ChronoUnit.MILLIS),
            r.exerciseType, r.startedAt.truncatedTo(ChronoUnit.MILLIS), r.endedAt.truncatedTo(ChronoUnit.MILLIS),
            r.activeDurationSeconds, decimal(r.distanceMeters), decimal(r.caloriesKcal), decimal(r.meanHeartRate),
            decimal(r.minHeartRate), decimal(r.maxHeartRate), "", series, "",
        )
        return value.copy(payloadHash = sha256(value.canonical()), timeSeriesHash = sha256(value.seriesCanonical()))
    }

    private fun apply(entity: ExternalWorkoutSummary, r: NormalizedWorkout) {
        entity.provider = r.provider; entity.sourceRecordId = r.sourceRecordId; entity.sourceAppId = r.sourceAppId
        entity.sourceModifiedAt = r.sourceModifiedAt; entity.exerciseType = r.exerciseType
        entity.startedAt = r.startedAt; entity.endedAt = r.endedAt; entity.activeDurationSeconds = r.activeDurationSeconds
        entity.distanceMeters = r.distanceMeters; entity.caloriesKcal = r.caloriesKcal
        entity.meanHeartRate = r.meanHeartRate; entity.minHeartRate = r.minHeartRate; entity.maxHeartRate = r.maxHeartRate
        entity.payloadHash = r.payloadHash
        entity.timeSeriesHash = r.timeSeriesHash
        entity.timeSeriesVersion = TIME_SERIES_VERSION
        entity.timeSeriesSampleCount = r.samples.size
    }

    private fun replaceSamples(sessionId: UUID, request: NormalizedWorkout) {
        val apiSessionId = apiId(sessionId)
        samples.deleteAllByWorkoutSessionId(apiSessionId)
        samples.flush()
        samples.saveAll(request.samples.map { sample ->
            ExternalWorkoutSample(
                id = dbId(UUID.randomUUID()), workoutSessionId = apiSessionId,
                measuredAt = sample.measuredAt, heartRate = sample.heartRate, speedMetersPerSecond = sample.speedMetersPerSecond,
            )
        })
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun notFound(): Nothing = throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
    private fun badRequest(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
    private fun conflict(message: String): Nothing = throw ApiException(ApiErrorCode.EXTERNAL_WORKOUT_CONFLICT, message)
    private fun dbId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun apiId(value: UUID?): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(value))
    private fun ExternalWorkoutSummary.asResponse() = toResponse(apiId(workoutSessionId))

    companion object {
        private val HEALTH_EXERCISE_TYPES = setOf(
            "BENCH_PRESS",
            "SQUATS",
            "LUNGES",
            "LEG_PRESSES",
            "LEG_EXTENSIONS",
            "LEG_CURLS",
            "LAT_PULLDOWNS",
            "DEADLIFTS",
            "SHOULDER_PRESSES",
            "LATERAL_RAISES",
            "PUSH_UPS",
            "PULL_UPS",
            "CRUNCH",
            "PLANK",
            "ARM_CURLS",
            "ARM_EXTENSIONS",
        )
        private const val MAX_SAMPLES = 10_000
        private const val TIME_SERIES_VERSION = 1
    }
}

private data class NormalizedWorkout(
    val provider: String, val sourceRecordId: String, val sourceAppId: String?, val sourceModifiedAt: Instant?,
    val exerciseType: String, val startedAt: Instant, val endedAt: Instant, val activeDurationSeconds: Long,
    val distanceMeters: BigDecimal?, val caloriesKcal: BigDecimal?, val meanHeartRate: BigDecimal?,
    val minHeartRate: BigDecimal?, val maxHeartRate: BigDecimal?, val payloadHash: String,
    val samples: List<NormalizedSample>, val timeSeriesHash: String,
) {
    fun canonical() = listOf(provider, sourceRecordId, sourceAppId ?: "<null>", sourceModifiedAt?.toString() ?: "<null>",
        exerciseType, startedAt.toString(), endedAt.toString(), activeDurationSeconds.toString(),
        distanceMeters?.toPlainString() ?: "<null>", caloriesKcal?.toPlainString() ?: "<null>",
        meanHeartRate?.toPlainString() ?: "<null>", minHeartRate?.toPlainString() ?: "<null>",
        maxHeartRate?.toPlainString() ?: "<null>").joinToString("\u001f")

    fun seriesCanonical() = samples.joinToString("\u001e") {
        listOf(it.measuredAt.toString(), it.heartRate?.toPlainString() ?: "<null>", it.speedMetersPerSecond?.toPlainString() ?: "<null>")
            .joinToString("\u001f")
    }
}

private data class NormalizedSample(val measuredAt: Instant, val heartRate: BigDecimal?, val speedMetersPerSecond: BigDecimal?)
