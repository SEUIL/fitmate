package com.fitmate.backend.recommendation.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.ExerciseCatalogRepository
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.recommendation.api.CreateWorkoutPlanRequest
import com.fitmate.backend.recommendation.api.PlanCatalogExercise
import com.fitmate.backend.running.repository.RunningRecordRepository
import com.fitmate.backend.running.domain.RunType
import com.fitmate.backend.running.domain.RunningRecord
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.health.application.ExternalWorkoutAnalysisService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkoutPlanContextReader(
    private val sports: SportRepository,
    private val goals: UserGoalRepository,
    private val levels: UserSportRepository,
    private val exercises: ExerciseCatalogRepository,
    private val inbody: InbodyRecordRepository,
    private val running: RunningRecordRepository,
    private val externalWorkouts: ExternalWorkoutSummaryRepository,
    private val externalWorkoutAnalysis: ExternalWorkoutAnalysisService,
) {
    @Transactional(readOnly = true)
    fun read(userId: UUID, request: CreateWorkoutPlanRequest): WorkoutPlanContext {
        val sport = sports.findByCodeAndIsActiveTrue(SportCode.HEALTH)
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "헬스 종목을 사용할 수 없습니다.")
        val goal = goals.findAllByIdUserId(userId).singleOrNull { it.isPrimary }?.id?.goalCode
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "프로필의 주 운동 목표를 설정해 주세요.")
        val skill = levels.findAllByIdUserId(userId).firstOrNull { it.id.sportId == sport.id }?.skillLevel
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "프로필의 헬스 숙련도를 설정해 주세요.")
        val record = request.inbodyRecordId?.let {
            inbody.findByIdAndUserId(it, userId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "선택한 인바디 기록을 찾을 수 없습니다.")
        }
        val catalog = exercises.findActiveExercises(SportCode.HEALTH, null, null, Pageable.unpaged()).map {
            PlanCatalogExercise(UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(it.id)), it.name, it.bodyPart, it.equipmentType)
        }.filter { it.id != TIMED_PLANK_ID }
        val recentRunning = running.findAllOwned(userId, PageRequest.of(0, 3)).content.map { run ->
            val storedId = requireNotNull(run.workoutSessionId)
            val external = if (request.externalAiConsent) externalWorkouts.findByWorkoutSessionIdAndUserId(storedId, userId) else null
            val deterministic = external?.let {
                runCatching { externalWorkoutAnalysis.get(userId, UuidBinaryConverter.fromMysqlOrderedUuid(storedId)) }.getOrNull()
            }
            RecentRunningRecommendationContext(run.runType.name, recommendationGoalAchieved(run),
                run.totalDistanceMeters, run.activeDurationSeconds, run.averagePaceSecondsPerKm, run.rpe, run.conditionScore,
                external?.let { summary -> SamsungRecommendationContext(
                    activeDurationSeconds = summary.activeDurationSeconds,
                    meanHeartRate = summary.meanHeartRate,
                    peakHeartRate = deterministic?.heartRate?.peakBpm,
                    heartRateCoveragePercent = deterministic?.heartRate?.coveragePercent,
                    heartRateLongGapCount = deterministic?.heartRate?.longGapCount,
                    paceSources = buildList {
                        if (deterministic?.pace?.samsungSourceUsed == true) add("SAMSUNG_HEALTH")
                        if (deterministic?.pace?.smartphoneGpsSourceUsed == true) add("SMARTPHONE_GPS")
                    },
                    analysisVersion = deterministic?.analysisVersion,
                    limitations = deterministic?.limitations.orEmpty(),
                ) })
        }
        return WorkoutPlanContext(request, goal, skill, catalog, record?.measuredDate, record?.bodyFatPercentage, record?.skeletalMuscleMassKg, recentRunning)
    }

    companion object {
        // V2's plank is a timed hold. The existing routine/session contract only supports repetitions;
        // do not silently prescribe it as repetitions until timed exercise targets are supported.
        private val TIMED_PLANK_ID = UUID.fromString("30000000-0000-0000-0000-000000000018")
    }
}

internal fun recommendationGoalAchieved(run: RunningRecord): Boolean? {
    return when (run.runType) {
        RunType.FREE -> null
        RunType.DISTANCE_TARGET -> run.targetDistanceMeters?.takeIf { it > 0 }
            ?.let { run.totalDistanceMeters >= it }
        RunType.TIME_TARGET,
        RunType.INTERVAL,
        -> run.targetDurationSeconds?.takeIf { it > 0 }
            ?.let { run.activeDurationSeconds >= it }
        RunType.PACE_TARGET -> {
            val targetDistance = run.targetDistanceMeters?.takeIf { it > 0 } ?: return null
            val targetPace = run.targetPaceSecondsPerKm?.takeIf { it > 0 } ?: return null
            val actualPace = run.averagePaceSecondsPerKm?.takeIf { it > 0 } ?: return null
            if (run.totalDistanceMeters <= 0 || run.activeDurationSeconds <= 0) return null
            run.totalDistanceMeters >= targetDistance && actualPace <= targetPace
        }
    }
}
