package com.fitmate.backend.sportrecord.application

import com.fitmate.backend.common.api.ApiErrorField
import com.fitmate.backend.common.api.CursorPageResponse
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.sportrecord.api.CreateSportRecordRequest
import com.fitmate.backend.sportrecord.api.SportRecordResponse
import com.fitmate.backend.sportrecord.domain.SportRecord
import com.fitmate.backend.sportrecord.repository.SportRecordRepository
import com.fitmate.backend.workout.domain.WorkoutSession
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class SportRecordService(private val sessions: WorkoutSessionRepository, private val records: SportRecordRepository, private val persistence: SportRecordPersistenceService) {
    data class CreateResult(val response: SportRecordResponse, val created: Boolean)

    fun create(userId: UUID, request: CreateSportRecordRequest): CreateResult {
        val normalized = normalize(request)
        val id = dbId(requireNotNull(request.clientRecordId))
        val existing = sessions.findById(id).orElse(null)
        if (existing != null) {
            if (existing.userId != userId) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동 기록을 찾을 수 없습니다.")
            val record = records.findById(id).orElseThrow { ApiException(ApiErrorCode.SPORT_RECORD_CONFLICT, "같은 ID가 다른 기록에 사용되었습니다.") }
            if (!same(record, existing, normalized)) throw ApiException(ApiErrorCode.SPORT_RECORD_CONFLICT)
            return CreateResult(toResponse(record), false)
        }
        val session = WorkoutSession(id = id, userId = userId, sportCode = normalized.sportCode,
            status = WorkoutSessionStatus.COMPLETED, startedAt = normalized.startedAt, endedAt = normalized.completedAt, clientUpdatedAt = normalized.completedAt)
        val record = SportRecord(workoutSessionId = id, workoutSession = session, activityType = normalized.activityType,
            activeDurationSeconds = normalized.activeDurationSeconds, placeName = normalized.placeName, memo = normalized.memo,
            gameCount = normalized.gameCount, winCount = normalized.winCount, attemptCount = normalized.attemptCount, completionCount = normalized.completionCount,
            difficultyMemo = normalized.difficultyMemo, courseName = normalized.courseName, distanceMeters = normalized.distanceMeters,
            shotCount = normalized.shotCount, holeCount = normalized.holeCount, strokeCount = normalized.strokeCount)
        return try {
            CreateResult(toResponse(persistence.save(session, record)), true)
        } catch (exception: DataIntegrityViolationException) {
            val saved = records.findOwned(id, userId) ?: throw exception
            if (!same(saved, requireNotNull(saved.workoutSession), normalized)) throw ApiException(ApiErrorCode.SPORT_RECORD_CONFLICT)
            CreateResult(toResponse(saved), false)
        }
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID, recordId: UUID): SportRecordResponse = records.findOwned(dbId(recordId), userId)?.let(::toResponse)
        ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "운동 기록을 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    fun list(userId: UUID, sportCode: SportCode?, cursor: Int, size: Int): CursorPageResponse<SportRecordResponse> {
        if (sportCode != null && sportCode !in supportedSports) invalid("sportCode", "NOT_ALLOWED", "지원하지 않는 종목입니다.")
        if (cursor < 0 || size !in 1..100) invalid("cursor", "OUT_OF_RANGE", "cursor는 0 이상, size는 1부터 100까지 가능합니다.")
        val page = records.findAllOwned(userId, sportCode, PageRequest.of(cursor, size))
        return CursorPageResponse(page.content.map(::toResponse), (cursor + 1).toString().takeIf { page.hasNext() }, page.hasNext())
    }

    private data class Input(val sportCode: SportCode, val activityType: String, val startedAt: Instant, val completedAt: Instant, val activeDurationSeconds: Int,
        val placeName: String?, val memo: String?, val gameCount: Int?, val winCount: Int?, val attemptCount: Int?, val completionCount: Int?, val difficultyMemo: String?, val courseName: String?, val distanceMeters: Int?, val shotCount: Int?, val holeCount: Int?, val strokeCount: Int?)

    private fun normalize(r: CreateSportRecordRequest): Input {
        val sport = requireNotNull(r.sportCode); if (sport !in supportedSports) invalid("sportCode", "NOT_ALLOWED", "지원하지 않는 종목입니다.")
        val type = requireNotNull(r.activityType).trim().uppercase(); val start = truncate(requireNotNull(r.startedAt)); val end = truncate(requireNotNull(r.completedAt)); val active = requireNotNull(r.activeDurationSeconds)
        if (!start.isBefore(end)) invalid("completedAt", "INCONSISTENT_VALUE", "완료 시각은 시작 시각보다 이후여야 합니다.")
        if (Duration.between(start, end).seconds < active) invalid("activeDurationSeconds", "INCONSISTENT_VALUE", "활동 시간은 경과 시간보다 길 수 없습니다.")
        val input = Input(sport, type, start, end, active, clean(r.placeName), clean(r.memo), r.gameCount, r.winCount, r.attemptCount, r.completionCount, clean(r.difficultyMemo), clean(r.courseName), r.distanceMeters, r.shotCount, r.holeCount, r.strokeCount)
        validate(input); return input
    }
    private fun validate(i: Input) {
        val allowed = types[i.sportCode]!!; if (i.activityType !in allowed) invalid("activityType", "NOT_ALLOWED", "종목에 맞지 않는 활동 유형입니다.")
        if (i.winCount != null && i.gameCount == null) invalid("gameCount", "REQUIRED", "승리 수에는 게임 수가 필요합니다.")
        if (i.winCount != null && i.winCount > i.gameCount!!) invalid("winCount", "INCONSISTENT_VALUE", "승리 수는 게임 수를 넘을 수 없습니다.")
        if (i.completionCount != null && i.attemptCount == null) invalid("attemptCount", "REQUIRED", "완등 수에는 시도 수가 필요합니다.")
        if (i.completionCount != null && i.completionCount > i.attemptCount!!) invalid("completionCount", "INCONSISTENT_VALUE", "완등 수는 시도 수를 넘을 수 없습니다.")
        if (i.strokeCount != null && i.holeCount == null) invalid("holeCount", "REQUIRED", "총 타수에는 진행 홀이 필요합니다.")
        val supplied = mapOf(
            "gameCount" to i.gameCount, "winCount" to i.winCount,
            "attemptCount" to i.attemptCount, "completionCount" to i.completionCount,
            "difficultyMemo" to i.difficultyMemo, "courseName" to i.courseName,
            "distanceMeters" to i.distanceMeters, "shotCount" to i.shotCount,
            "holeCount" to i.holeCount, "strokeCount" to i.strokeCount,
        ).filterValues { it != null }.keys
        val applicable = when (i.sportCode) {
            SportCode.BADMINTON -> setOf("gameCount", "winCount")
            SportCode.CLIMBING -> setOf("attemptCount", "completionCount", "difficultyMemo")
            SportCode.HIKING -> setOf("courseName", "distanceMeters")
            SportCode.GOLF -> if (i.activityType == "PRACTICE") setOf("shotCount") else setOf("holeCount", "strokeCount")
            else -> emptySet()
        }
        val disallowed = supplied.firstOrNull { it !in applicable }
        if (disallowed != null) invalid(disallowed, "NOT_ALLOWED", "다른 종목 또는 유형의 결과 필드는 입력할 수 없습니다.")
    }
    private fun same(r: SportRecord, s: WorkoutSession, i: Input) = s.sportCode == i.sportCode && s.startedAt == i.startedAt && s.endedAt == i.completedAt && r.activityType == i.activityType && r.activeDurationSeconds == i.activeDurationSeconds && r.placeName == i.placeName && r.memo == i.memo && r.gameCount == i.gameCount && r.winCount == i.winCount && r.attemptCount == i.attemptCount && r.completionCount == i.completionCount && r.difficultyMemo == i.difficultyMemo && r.courseName == i.courseName && r.distanceMeters == i.distanceMeters && r.shotCount == i.shotCount && r.holeCount == i.holeCount && r.strokeCount == i.strokeCount
    private fun toResponse(r: SportRecord): SportRecordResponse { val s = requireNotNull(r.workoutSession); return SportRecordResponse(apiId(r.workoutSessionId), s.sportCode, r.activityType, s.startedAt, requireNotNull(s.endedAt), r.activeDurationSeconds, Duration.between(s.startedAt, s.endedAt).seconds, r.placeName, r.memo, r.gameCount, r.winCount, r.attemptCount, r.completionCount, r.difficultyMemo, r.courseName, r.distanceMeters, r.shotCount, r.holeCount, r.strokeCount) }
    private fun clean(v: String?) = v?.trim()?.ifEmpty { null }
    private fun truncate(v: Instant) = v.truncatedTo(ChronoUnit.MILLIS)
    private fun invalid(field: String, reason: String, message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message, listOf(ApiErrorField(field, reason)))
    private fun dbId(v: UUID) = UuidBinaryConverter.toMysqlOrderedUuid(v); private fun apiId(v: UUID?) = UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(v))
    companion object { private val supportedSports = setOf(SportCode.BADMINTON, SportCode.CLIMBING, SportCode.HIKING, SportCode.GOLF); private val types = mapOf(SportCode.BADMINTON to setOf("SINGLES", "DOUBLES", "PRACTICE"), SportCode.CLIMBING to setOf("BOULDERING", "LEAD", "TOP_ROPE"), SportCode.HIKING to setOf("HIKE", "TREKKING"), SportCode.GOLF to setOf("PRACTICE", "SCREEN", "FIELD")) }
}
