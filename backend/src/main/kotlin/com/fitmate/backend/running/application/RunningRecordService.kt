package com.fitmate.backend.running.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.running.api.CreateRunningRecordRequest
import com.fitmate.backend.running.api.RunningLapResponse
import com.fitmate.backend.running.api.RunningRecordResponse
import com.fitmate.backend.running.api.RunningRecordSummaryResponse
import com.fitmate.backend.running.api.RunningRoutePoint
import com.fitmate.backend.running.domain.RunType
import com.fitmate.backend.running.domain.RouteVisibility
import com.fitmate.backend.running.domain.RunningLap
import com.fitmate.backend.running.domain.RunningRecord
import com.fitmate.backend.running.repository.RunningLapRepository
import com.fitmate.backend.running.repository.RunningRecordRepository
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RunningRecordService(
    private val sessions: WorkoutSessionRepository,
    private val records: RunningRecordRepository,
    private val laps: RunningLapRepository,
    private val externalSummaries: ExternalWorkoutSummaryRepository,
    private val objectMapper: ObjectMapper,
    private val entityManager: jakarta.persistence.EntityManager,
) {
    @Transactional
    fun create(userId: UUID, request: CreateRunningRecordRequest): RunningRecordResponse {
        request.clientRecordId?.let { id ->
            if (sessions.existsById(dbId(id))) return get(userId, id)
        }
        validateTarget(request)
        val startedAt = request.startedAt ?: request.completedAt.minusSeconds(request.activeDurationSeconds.toLong())
        if (!startedAt.isBefore(request.completedAt)) invalid("시작 시각은 완료 시각보다 이전이어야 합니다.")
        if (Duration.between(startedAt, request.completedAt).seconds < request.activeDurationSeconds) {
            invalid("활동 시간은 시작부터 완료까지의 시간보다 길 수 없습니다.")
        }
        validateLaps(request)
        validateRoute(request.routePoints, request.routeVisibility, startedAt, request.completedAt)

        val averagePace = pace(request.activeDurationSeconds, request.distanceMeters)
        val lapPaces = request.laps.map { pace(it.durationSeconds, it.distanceMeters)!! }
        val bestPace = if (request.distanceMeters == 0) null else lapPaces.minOrNull() ?: averagePace
        val averageSpeed = if (request.distanceMeters == 0) null else BigDecimal(request.distanceMeters)
            .multiply(BigDecimal("3.6"))
            .divide(BigDecimal(request.activeDurationSeconds), 2, RoundingMode.HALF_UP)
        val apiRecordId = request.clientRecordId ?: UUID.randomUUID()
        val recordId = dbId(apiRecordId)
        val session = WorkoutSession(
                id = recordId,
                userId = userId,
                sportCode = SportCode.RUNNING,
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = request.completedAt,
                clientUpdatedAt = request.completedAt,
            ).also { entityManager.persist(it); entityManager.flush() }
        val record = records.saveAndFlush(
            RunningRecord(
                workoutSessionId = recordId,
                runType = request.runType,
                targetDistanceMeters = request.targetDistanceMeters,
                targetDurationSeconds = request.targetDurationSeconds,
                targetPaceSecondsPerKm = request.targetPaceSecondsPerKm,
                intervalSeconds = request.intervalSeconds,
                routeVisibility = request.routeVisibility,
                autoPauseEnabled = request.autoPauseEnabled,
                activeDurationSeconds = request.activeDurationSeconds,
                totalDistanceMeters = request.distanceMeters,
                averagePaceSecondsPerKm = averagePace,
                bestPaceSecondsPerKm = bestPace,
                averageSpeedKmh = averageSpeed,
                caloriesKcal = request.caloriesKcal,
                cadenceSpm = request.cadenceSpm,
                strideLengthCm = request.strideLengthCm,
                elevationGainMeters = request.elevationGainMeters,
                rpe = request.rpe,
                conditionScore = request.conditionScore,
                memo = request.memo?.trim()?.ifEmpty { null },
                routeJson = request.routePoints.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString),
            ),
        ).also { it.workoutSession = session }
        var lapStartedAt = startedAt
        val savedLaps = request.laps.map { item ->
            val lapEndedAt = lapStartedAt.plusSeconds(item.durationSeconds.toLong())
            RunningLap(
                id = dbId(UUID.randomUUID()),
                // This scalar foreign key uses the converter, so it receives the logical API UUID.
                workoutSessionId = apiRecordId,
                lapNo = item.lapNo,
                startedAt = lapStartedAt,
                endedAt = lapEndedAt,
                distanceMeters = item.distanceMeters,
                durationSeconds = item.durationSeconds,
                averagePaceSecondsPerKm = pace(item.durationSeconds, item.distanceMeters)!!,
            ).also { lapStartedAt = lapEndedAt }
        }.let(laps::saveAllAndFlush)
        return toResponse(record, savedLaps)
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID, recordId: UUID): RunningRecordResponse {
        val record = owned(userId, recordId)
        return toResponse(record, laps.findAllByWorkoutSessionIdOrderByLapNoAsc(apiId(record.workoutSessionId)))
    }

    @Transactional
    fun delete(userId: UUID, recordId: UUID) {
        val record = owned(userId, recordId)
        val session = requireNotNull(record.workoutSession)
        val storedSessionId = requireNotNull(record.workoutSessionId)
        externalSummaries.deleteByWorkoutSessionIdAndUserId(storedSessionId, userId)
        laps.deleteAll(laps.findAllByWorkoutSessionIdOrderByLapNoAsc(apiId(record.workoutSessionId)))
        records.delete(record)
        sessions.delete(session)
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID, cursor: Int, size: Int): CursorPageResponse<RunningRecordSummaryResponse> {
        if (cursor < 0 || size !in 1..100) invalid("cursor는 0 이상, size는 1부터 100까지 가능합니다.")
        val found = records.findAllOwned(userId, PageRequest.of(cursor, size))
        return CursorPageResponse(
            items = found.content.map(::toSummary),
            nextCursor = (cursor + 1).toString().takeIf { found.hasNext() },
            hasNext = found.hasNext(),
        )
    }

    private fun validateTarget(request: CreateRunningRecordRequest) {
        val valid = when (request.runType) {
            RunType.FREE -> true
            RunType.DISTANCE_TARGET -> request.targetDistanceMeters != null
            RunType.TIME_TARGET -> request.targetDurationSeconds != null
            RunType.PACE_TARGET -> request.targetDistanceMeters != null && request.targetPaceSecondsPerKm != null
            RunType.INTERVAL -> request.targetDurationSeconds != null && request.intervalSeconds != null
        }
        if (!valid) invalid("러닝 유형에 필요한 목표값을 입력해야 합니다.")
    }

    private fun validateLaps(request: CreateRunningRecordRequest) {
        if (request.distanceMeters == 0 && request.laps.isNotEmpty()) invalid("거리가 0인 기록에는 랩을 저장할 수 없습니다.")
        if (request.laps.map { it.lapNo } != (1..request.laps.size).toList()) {
            invalid("랩 번호는 1부터 빠짐없이 순서대로 입력해야 합니다.")
        }
        if (request.laps.sumOf { it.durationSeconds } > request.activeDurationSeconds) {
            invalid("랩 시간 합계는 활동 시간보다 길 수 없습니다.")
        }
        if (request.laps.sumOf { it.distanceMeters } > request.distanceMeters) {
            invalid("랩 거리 합계는 전체 거리보다 클 수 없습니다.")
        }
    }

    private fun validateRoute(points: List<RunningRoutePoint>, visibility: RouteVisibility, startedAt: Instant, completedAt: Instant) {
        if (points.isNotEmpty() && visibility != RouteVisibility.PRIVATE) {
            invalid("경로 기록은 나만 보기로만 저장할 수 있습니다.")
        }
        if (points.any { it.recordedAt.isBefore(startedAt) || it.recordedAt.isAfter(completedAt) }) {
            invalid("경로 좌표 시각은 러닝 시작과 완료 시각 사이여야 합니다.")
        }
        if (points.zipWithNext().any { (before, after) -> !after.recordedAt.isAfter(before.recordedAt) }) {
            invalid("경로 좌표 시각은 중복 없이 오름차순이어야 합니다.")
        }
    }

    private fun toResponse(record: RunningRecord, foundLaps: List<RunningLap>): RunningRecordResponse {
        val session = requireNotNull(record.workoutSession)
        return RunningRecordResponse(
            id = apiId(record.workoutSessionId),
            runType = record.runType,
            targetDistanceMeters = record.targetDistanceMeters,
            targetDurationSeconds = record.targetDurationSeconds,
            targetPaceSecondsPerKm = record.targetPaceSecondsPerKm,
            intervalSeconds = record.intervalSeconds,
            routeVisibility = record.routeVisibility,
            autoPauseEnabled = record.autoPauseEnabled,
            startedAt = session.startedAt,
            completedAt = requireNotNull(session.endedAt),
            elapsedDurationSeconds = Duration.between(session.startedAt, session.endedAt).seconds,
            activeDurationSeconds = record.activeDurationSeconds,
            distanceMeters = record.totalDistanceMeters,
            averagePaceSecondsPerKm = record.averagePaceSecondsPerKm,
            bestPaceSecondsPerKm = record.bestPaceSecondsPerKm,
            averageSpeedKmh = record.averageSpeedKmh,
            caloriesKcal = record.caloriesKcal,
            cadenceSpm = record.cadenceSpm,
            strideLengthCm = record.strideLengthCm,
            elevationGainMeters = record.elevationGainMeters,
            rpe = record.rpe,
            conditionScore = record.conditionScore,
            memo = record.memo,
            goalAchieved = goalAchieved(record),
            routePoints = decodeRoute(record.routeJson),
            laps = foundLaps.map(::toLapResponse),
            version = record.version,
        )
    }

    private fun toSummary(record: RunningRecord) = RunningRecordSummaryResponse(
        id = apiId(record.workoutSessionId),
        runType = record.runType,
        completedAt = requireNotNull(record.workoutSession?.endedAt),
        activeDurationSeconds = record.activeDurationSeconds,
        distanceMeters = record.totalDistanceMeters,
        averagePaceSecondsPerKm = record.averagePaceSecondsPerKm,
        bestPaceSecondsPerKm = record.bestPaceSecondsPerKm,
        goalAchieved = goalAchieved(record),
    )

    private fun toLapResponse(lap: RunningLap) = RunningLapResponse(
        id = apiId(lap.id),
        lapNo = lap.lapNo,
        startedAt = lap.startedAt,
        endedAt = lap.endedAt,
        distanceMeters = lap.distanceMeters,
        durationSeconds = lap.durationSeconds,
        averagePaceSecondsPerKm = lap.averagePaceSecondsPerKm,
    )

    private fun goalAchieved(record: RunningRecord): Boolean? = when (record.runType) {
        RunType.FREE -> null
        RunType.DISTANCE_TARGET -> record.totalDistanceMeters >= requireNotNull(record.targetDistanceMeters)
        RunType.TIME_TARGET -> record.activeDurationSeconds >= requireNotNull(record.targetDurationSeconds)
        RunType.PACE_TARGET -> record.totalDistanceMeters >= requireNotNull(record.targetDistanceMeters) &&
            (record.averagePaceSecondsPerKm?.let { it <= requireNotNull(record.targetPaceSecondsPerKm) } ?: false)
        RunType.INTERVAL -> record.activeDurationSeconds >= requireNotNull(record.targetDurationSeconds)
    }

    private fun decodeRoute(routeJson: String?): List<RunningRoutePoint> = routeJson?.let {
        objectMapper.readValue(it, Array<RunningRoutePoint>::class.java).toList()
    } ?: emptyList()

    private fun pace(durationSeconds: Int, distanceMeters: Int): Int? = if (distanceMeters == 0) null else
        BigDecimal(durationSeconds).multiply(BigDecimal(1_000))
            .divide(BigDecimal(distanceMeters), 0, RoundingMode.HALF_UP).intValueExact()

    private fun owned(userId: UUID, recordId: UUID): RunningRecord = records.findOwned(dbId(recordId), userId)
        ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "러닝 기록을 찾을 수 없습니다.")

    private fun dbId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun apiId(value: UUID?): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(value))
    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
