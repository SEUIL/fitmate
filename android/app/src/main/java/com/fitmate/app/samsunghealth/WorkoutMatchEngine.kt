package com.fitmate.app.samsunghealth

import java.time.Duration
import java.time.Instant

object WorkoutMatchEngine {
    private val healthExerciseNames = linkedMapOf(
        "BENCH_PRESS" to setOf("벤치 프레스", "덤벨 벤치 프레스", "체스트 프레스 머신"),
        "SQUATS" to setOf("바벨 백 스쿼트", "고블릿 스쿼트"),
        "LUNGES" to setOf("워킹 런지"),
        "LEG_PRESSES" to setOf("레그 프레스"),
        "LEG_EXTENSIONS" to setOf("레그 익스텐션"),
        "LEG_CURLS" to setOf("레그 컬"),
        "LAT_PULLDOWNS" to setOf("랫 풀다운"),
        "DEADLIFTS" to setOf("루마니안 데드리프트"),
        "SHOULDER_PRESSES" to setOf("오버헤드 프레스"),
        "LATERAL_RAISES" to setOf("사이드 레터럴 레이즈"),
        "PUSH_UPS" to setOf("푸시업"),
        "PULL_UPS" to setOf("어시스티드 풀업"),
        "CRUNCH" to setOf("크런치"),
        "PLANK" to setOf("플랭크"),
        "ARM_CURLS" to setOf("바벨 컬"),
        "ARM_EXTENSIONS" to setOf("케이블 트라이셉스 푸시다운"),
    )
    private val tolerance = Duration.ofMinutes(10)

    fun candidates(
        sportCode: String,
        sessionStartedAt: Instant,
        sessionEndedAt: Instant,
        workouts: List<SamsungWorkoutUiModel>,
        excludedSourceKeys: Set<WorkoutSourceKey> = emptySet(),
        sessionActiveDurationSeconds: Long? = null,
    ): List<SamsungWorkoutUiModel> =
        analyze(
            sportCode = sportCode,
            sessionStartedAt = sessionStartedAt,
            sessionEndedAt = sessionEndedAt,
            workouts = workouts,
            excludedSourceKeys = excludedSourceKeys,
            sessionActiveDurationSeconds = sessionActiveDurationSeconds,
        ).candidates

    fun analyze(
        sportCode: String,
        sessionStartedAt: Instant,
        sessionEndedAt: Instant,
        workouts: List<SamsungWorkoutUiModel>,
        sessionExerciseNames: List<String> = emptyList(),
        excludedSourceKeys: Set<WorkoutSourceKey> = emptySet(),
        sessionActiveDurationSeconds: Long? = null,
    ): WorkoutMatchAnalysis {
        val unique = workouts.distinctBy { it.sourceKey() }
        val alreadyLinked = unique.filter { it.sourceKey() in excludedSourceKeys }
        val available = unique.filterNot { it.sourceKey() in excludedSourceKeys }
        val compound = available.filter { it.sessionCount != 1 }
        val incomplete = available.filter { it.sessionCount == 1 && it.activeDurationSeconds <= 0 }
        val eligibleShape = available.filter { it.sessionCount == 1 && it.activeDurationSeconds > 0 }
        val unsupported = eligibleShape.filterNot { isSupported(sportCode, it.exerciseType) }.map { workout ->
            UnsupportedWorkout(
                workout = workout,
                matchingSessionExerciseNames = matchingExerciseNames(workout.customTitle, sessionExerciseNames),
            )
        }
        val supported = eligibleShape.filter { isSupported(sportCode, it.exerciseType) }
        val recommendations = supported.filter { matchesTime(sessionStartedAt, sessionEndedAt, it) }
            .map { workout -> score(
                sportCode,
                sessionStartedAt,
                sessionEndedAt,
                workout,
                sessionExerciseNames,
                sessionActiveDurationSeconds,
            ) }
            .sortedWith(compareByDescending<WorkoutMatchCandidate> { it.score }
                .thenBy { it.startDifferenceSeconds + it.endDifferenceSeconds }
                .thenBy { it.activeDurationDifferenceSeconds }
                .thenBy { it.workout.sourceAppId ?: "" }
                .thenBy { it.workout.uid })
        return WorkoutMatchAnalysis(
            recommendations = recommendations,
            unsupported = unsupported,
            compoundCount = compound.size,
            timeMismatchCount = supported.count { !matchesTime(sessionStartedAt, sessionEndedAt, it) },
            incompleteCount = incomplete.size,
            alreadyLinkedCount = alreadyLinked.size,
        )
    }

    fun isSupported(sportCode: String, exerciseType: String) = when (sportCode) {
        "HEALTH" -> exerciseType in healthExerciseNames
        "RUNNING" -> exerciseType == "RUNNING"
        else -> false
    }

    fun displayName(exerciseType: String): String = when (exerciseType) {
        "RUNNING" -> "러닝"
        else -> healthExerciseNames[exerciseType]?.firstOrNull() ?: exerciseType
    }

    fun matchingExerciseNames(customTitle: String?, sessionExerciseNames: List<String>): List<String> {
        val normalizedTitle = customTitle?.let(::normalizeName)?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return sessionExerciseNames.filter { normalizeName(it) == normalizedTitle }.distinct()
    }

    private fun score(
        sportCode: String,
        sessionStartedAt: Instant,
        sessionEndedAt: Instant,
        workout: SamsungWorkoutUiModel,
        sessionExerciseNames: List<String>,
        sessionActiveDurationSeconds: Long?,
    ): WorkoutMatchCandidate {
        val startDifference = Duration.between(sessionStartedAt, workout.startTime).abs().seconds
        val endDifference = Duration.between(sessionEndedAt, workout.endTime).abs().seconds
        val sessionElapsed = Duration.between(sessionStartedAt, sessionEndedAt).seconds.coerceAtLeast(0)
        val sourceElapsed = Duration.between(workout.startTime, workout.endTime).seconds.coerceAtLeast(0)
        val overlapStart = if (sessionStartedAt.isAfter(workout.startTime)) sessionStartedAt else workout.startTime
        val overlapEnd = if (sessionEndedAt.isBefore(workout.endTime)) sessionEndedAt else workout.endTime
        val overlapSeconds = Duration.between(overlapStart, overlapEnd).seconds.coerceAtLeast(0)
        val overlapBase = maxOf(sessionElapsed, sourceElapsed).coerceAtLeast(1)
        val overlapPercent = ((overlapSeconds * 100) / overlapBase).toInt().coerceIn(0, 100)
        val durationBasis = if (sessionActiveDurationSeconds != null && sessionActiveDurationSeconds > 0) {
            FitMateDurationBasis.ACTIVE_DURATION
        } else {
            FitMateDurationBasis.ELAPSED_FALLBACK
        }
        val fitMateDuration = when (durationBasis) {
            FitMateDurationBasis.ACTIVE_DURATION -> requireNotNull(sessionActiveDurationSeconds)
            FitMateDurationBasis.ELAPSED_FALLBACK -> sessionElapsed
        }
        val activeDifference = (fitMateDuration - workout.activeDurationSeconds).let { kotlin.math.abs(it) }
        val mappedNames = mappedExerciseNames(sportCode, workout.exerciseType, sessionExerciseNames)
        val titleNames = matchingExerciseNames(workout.customTitle, sessionExerciseNames)
        val typeMatch = when {
            sportCode == "RUNNING" -> MatchEvidence.MATCHED
            sessionExerciseNames.isEmpty() -> MatchEvidence.UNAVAILABLE
            mappedNames.isNotEmpty() -> MatchEvidence.MATCHED
            else -> MatchEvidence.NOT_MATCHED
        }
        val titleMatch = when {
            workout.customTitle.isNullOrBlank() || sessionExerciseNames.isEmpty() -> MatchEvidence.UNAVAILABLE
            titleNames.isNotEmpty() -> MatchEvidence.MATCHED
            else -> MatchEvidence.NOT_MATCHED
        }
        val overlapScore = when (overlapPercent) {
            in 90..100 -> 35
            in 75..89 -> 30
            in 50..74 -> 25
            in 25..49 -> 20
            else -> 15
        }
        val typeScore = when (typeMatch) {
            MatchEvidence.MATCHED -> 20
            MatchEvidence.UNAVAILABLE -> 10
            MatchEvidence.NOT_MATCHED -> 0
        }
        val timeScore = when (startDifference + endDifference) {
            in 0L..60L -> 25
            in 61L..300L -> 20
            in 301L..600L -> 15
            in 601L..900L -> 10
            else -> 5
        }
        val durationScore = when (activeDifference) {
            in 0L..60L -> 15
            in 61L..180L -> 12
            in 181L..300L -> 8
            in 301L..600L -> 4
            else -> 0
        }
        val result = overlapScore + typeScore + timeScore + durationScore +
            if (titleMatch == MatchEvidence.MATCHED) 5 else 0
        val confidence = when {
            result >= 80 -> MatchConfidence.HIGH
            result >= 60 -> MatchConfidence.MEDIUM
            else -> MatchConfidence.LOW
        }
        val reasons = buildList {
            add("FitMate 세션과 ${formatDifference(overlapSeconds)} 겹쳐 전체 시간의 ${overlapPercent}%를 포함해요.")
            if (typeMatch == MatchEvidence.MATCHED && sportCode == "RUNNING") add("운동 종류가 러닝으로 일치해요.")
            else if (typeMatch == MatchEvidence.MATCHED) add("운동 종류가 현재 세션의 ${mappedNames.joinToString(" · ")}와 대응해요.")
            else if (typeMatch == MatchEvidence.UNAVAILABLE) add("현재 세션 운동 이름이 없어 종류의 직접 대응 여부는 비교하지 못했어요.")
            else add("지원되는 운동 종류지만 현재 세션 운동 이름과 직접 대응하지 않아요.")
            add("시작 ${formatDifference(startDifference)}, 종료 ${formatDifference(endDifference)} 차이예요.")
            add("${durationBasis.reasonLabel}과 기기 활동 시간은 ${formatDifference(activeDifference)} 차이예요.")
            when (titleMatch) {
                MatchEvidence.MATCHED -> add("Samsung 제목이 ${titleNames.joinToString(" · ")}와 같아요. 제목은 보조 근거로만 사용해요.")
                MatchEvidence.NOT_MATCHED -> add("Samsung 제목은 현재 세션 운동 이름과 일치하지 않아요. 제목은 보조 근거예요.")
                MatchEvidence.UNAVAILABLE -> add("비교할 Samsung 제목 또는 FitMate 운동 이름이 없어요.")
            }
        }
        val cautions = buildList {
            if (overlapPercent < 50) add("시간 겹침이 절반보다 작아요.")
            if (typeMatch == MatchEvidence.NOT_MATCHED) add("운동 종류가 현재 세션 운동과 직접 대응하지 않아요.")
            if (activeDifference > maxOf(600L, fitMateDuration / 2)) add("활동 시간 차이가 커요.")
            if (confidence == MatchConfidence.LOW) add("추천 근거가 약합니다. 연결하지 않기를 선택할 수 있어요.")
        }
        return WorkoutMatchCandidate(
            workout = workout,
            score = result,
            confidence = confidence,
            reasons = reasons,
            cautions = cautions,
            overlapSeconds = overlapSeconds,
            overlapPercent = overlapPercent,
            typeMatch = typeMatch,
            titleMatch = titleMatch,
            startDifferenceSeconds = startDifference,
            endDifferenceSeconds = endDifference,
            activeDurationDifferenceSeconds = activeDifference,
            fitMateDurationBasis = durationBasis,
            matchingExerciseNames = mappedNames,
            matchingTitleExerciseNames = titleNames,
        )
    }

    private fun mappedExerciseNames(
        sportCode: String,
        exerciseType: String,
        sessionExerciseNames: List<String>,
    ): List<String> {
        if (sportCode != "HEALTH") return emptyList()
        val aliases = healthExerciseNames[exerciseType].orEmpty().map(::normalizeName).toSet()
        return sessionExerciseNames.filter { normalizeName(it) in aliases }.distinct()
    }

    private fun formatDifference(seconds: Long): String = when {
        seconds < 60 -> "${seconds}초"
        seconds % 60 == 0L -> "${seconds / 60}분"
        else -> "${seconds / 60}분 ${seconds % 60}초"
    }

    private fun matchesTime(sessionStartedAt: Instant, sessionEndedAt: Instant, workout: SamsungWorkoutUiModel) =
        Duration.between(sessionStartedAt, workout.startTime).abs() <= tolerance &&
            Duration.between(sessionEndedAt, workout.endTime).abs() <= tolerance &&
            workout.startTime.isBefore(sessionEndedAt) && sessionStartedAt.isBefore(workout.endTime)

    private fun normalizeName(value: String) = value.trim().lowercase().filterNot(Char::isWhitespace)
}

data class WorkoutMatchAnalysis(
    val recommendations: List<WorkoutMatchCandidate>,
    val unsupported: List<UnsupportedWorkout>,
    val compoundCount: Int,
    val timeMismatchCount: Int,
    val incompleteCount: Int,
    val alreadyLinkedCount: Int,
) {
    val candidates: List<SamsungWorkoutUiModel> get() = recommendations.map { it.workout }
}

enum class MatchConfidence(val label: String) {
    HIGH("높음"),
    MEDIUM("보통"),
    LOW("낮음"),
}

enum class MatchEvidence(val label: String) {
    MATCHED("일치"),
    NOT_MATCHED("불일치"),
    UNAVAILABLE("비교 정보 없음"),
}

enum class FitMateDurationBasis(val label: String, val reasonLabel: String) {
    ACTIVE_DURATION("FitMate 활동 시간", "FitMate 활동 시간"),
    ELAPSED_FALLBACK("FitMate 경과 시간 대체값", "활동 시간이 없어 사용한 FitMate 경과 시간"),
}

data class WorkoutMatchCandidate(
    val workout: SamsungWorkoutUiModel,
    val score: Int,
    val confidence: MatchConfidence,
    val reasons: List<String>,
    val cautions: List<String>,
    val overlapSeconds: Long,
    val overlapPercent: Int,
    val typeMatch: MatchEvidence,
    val titleMatch: MatchEvidence,
    val startDifferenceSeconds: Long,
    val endDifferenceSeconds: Long,
    val activeDurationDifferenceSeconds: Long,
    val fitMateDurationBasis: FitMateDurationBasis,
    val matchingExerciseNames: List<String>,
    val matchingTitleExerciseNames: List<String>,
) {
    val requiresExplicitConfirmation: Boolean get() = confidence == MatchConfidence.LOW
}

data class UnsupportedWorkout(
    val workout: SamsungWorkoutUiModel,
    val matchingSessionExerciseNames: List<String>,
)

data class WorkoutSourceKey(val sourceAppKey: String, val sourceRecordId: String)

fun SamsungWorkoutUiModel.sourceKey() = WorkoutSourceKey(sourceAppId ?: "", uid)
