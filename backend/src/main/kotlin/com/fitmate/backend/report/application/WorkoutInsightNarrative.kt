package com.fitmate.backend.report.application

import com.fitmate.backend.report.api.*
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Provider receives local references, never names or persistent identifiers. Evidence is rendered by the server. */
object WorkoutInsightNarrative {
    class ValidationFailure(val code: String) : IllegalArgumentException(code)
    private fun checkRule(valid: Boolean, code: String) { if (!valid) throw ValidationFailure(code) }
    fun facts(s: WorkoutInsightSnapshot, blindFixtureEvaluation: Boolean = false): Map<String, Map<String, Any?>> = buildMap {
        put("profile", mapOf("goal" to s.goal, "skillLevel" to s.skillLevel))
        put("session", mapOf("durationSeconds" to s.report.durationSeconds, "sets" to s.report.completedSetCount,
            "repetitions" to s.report.totalRepetitions, "volumeKg" to s.report.totalVolumeKg))
        s.report.exercises.forEachIndexed { i, e ->
            put("exercise-$i", mapOf("bodyPart" to e.bodyPart?.name, "sets" to e.completedSetCount,
                "repetitions" to e.totalRepetitions, "volumeKg" to e.totalVolumeKg, "maxWeightKg" to e.maxWeightKg,
                "previousVolumeKg" to e.previousComparison?.previousVolumeKg,
                "volumeChangeKg" to e.previousComparison?.volumeChangeKg,
                "completedSets" to e.completedSets))
        }
        s.inbody?.let { put("inbody", mapOf("bodyFatPercentage" to it.bodyFatPercentage,
            "skeletalMuscleMassKg" to it.skeletalMuscleMassKg,
            "daysBeforeWorkout" to ChronoUnit.DAYS.between(it.measuredDate, s.report.endedAt.atZone(ZoneOffset.UTC).toLocalDate()),
            "measurementCount" to 1)) }
        s.samsungHealth?.let { put("samsung", mapOf("activeDurationSeconds" to it.activeDurationSeconds,
            "caloriesKcal" to it.caloriesKcal, "meanHeartRate" to it.meanHeartRate)) }
        s.longitudinal?.let { h ->
            val all = h.sessions.map { it.report } + s.report
            put("history", mapOf("weeksRequested" to h.weeks, "priorSessions" to h.sessions.size,
                "demo" to (h.demo && !blindFixtureEvaluation), "truncated" to h.truncated, "scope" to "HEALTH only; current goal; no planned schedule or goal history",
                "rollingWeekSessionCounts" to (0 until h.weeks).map { week -> all.count { r ->
                    ChronoUnit.DAYS.between(r.endedAt, h.through) / 7 == week.toLong()
                } },
                "sessions" to all.map { r -> mapOf(
                    "daysBeforeCurrent" to ChronoUnit.DAYS.between(r.endedAt, h.through),
                    "durationSeconds" to r.durationSeconds, "completedSets" to r.completedSetCount,
                    "bodyPartSets" to r.exercises.groupBy { it.bodyPart?.name ?: "UNKNOWN" }.mapValues { (_, es) -> es.sumOf { it.completedSetCount } },
                    "currentExerciseComparisons" to s.report.exercises.mapIndexedNotNull { i, current ->
                        r.exercises.firstOrNull { it.exerciseId == current.exerciseId }?.let { e -> mapOf(
                            "reference" to "exercise-$i", "sets" to e.completedSetCount,
                            "repetitions" to e.totalRepetitions, "volumeKg" to e.totalVolumeKg,
                            "maxWeightKg" to e.maxWeightKg,
                            "meanRpe" to e.completedSets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()) }
                    }) }))
            if (h.bodies.isNotEmpty()) put("inbody", mapOf("measurementCount" to h.bodies.size,
                "distinctMeasurementDays" to h.bodies.map { it.measuredDate }.distinct().size,
                "bodyFatPercentagePointChange" to h.bodies.takeIf { it.first().measuredDate < it.last().measuredDate }?.let { it.last().bodyFatPercentage - it.first().bodyFatPercentage },
                "skeletalMuscleMassChangeKg" to h.bodies.takeIf { it.first().measuredDate < it.last().measuredDate }?.let { it.last().skeletalMuscleMassKg - it.first().skeletalMuscleMassKg },
                "measurements" to h.bodies.map { b -> mapOf("daysBeforeWorkout" to ChronoUnit.DAYS.between(b.measuredDate, h.through.atZone(ZoneOffset.UTC).toLocalDate()),
                    "bodyFatPercentage" to b.bodyFatPercentage, "skeletalMuscleMassKg" to b.skeletalMuscleMassKg) }))
            val device = h.sessions.mapNotNull { row -> row.samsungHealth?.let { row.report.endedAt to it } } + listOfNotNull(s.samsungHealth?.let { h.through to it })
            if (device.isNotEmpty()) put("samsung", mapOf("measurements" to device.map { (at, d) -> mapOf(
                "daysBeforeWorkout" to ChronoUnit.DAYS.between(at, h.through), "activeDurationSeconds" to d.activeDurationSeconds,
                "caloriesKcal" to d.caloriesKcal, "meanHeartRate" to d.meanHeartRate) }))
            if (h.previousAdvice.isNotEmpty()) {
                val names = all.flatMap { it.exercises }.map { it.exerciseName }.distinct().sortedByDescending { it.length }
                fun anonymous(text: String): String {
                    val anonymous = names.fold(text) { value, name -> value.replace(name, "해당 운동") }
                    // Only remove our fixture annotations for opt-in blind evaluation; retain the original snapshot.
                    return (if (blindFixtureEvaluation && h.demo) anonymous.removePrefix("[DEMO] ")
                        .replace("시연용 과거 제안이며 실제 이행 여부는 알 수 없습니다.", "실제 이행 여부는 알 수 없습니다.")
                    else anonymous).take(800)
                }
                put("previous-advice", mapOf("notEvidence" to true, "implementationStatus" to "unknown",
                    "suggestions" to h.previousAdvice.map { advice -> mapOf(
                        "daysBeforeCurrent" to ChronoUnit.DAYS.between(advice.createdAt, h.through),
                        "actions" to advice.actions.map { mapOf("title" to anonymous(it.title), "explanation" to anonymous(it.explanation)) }) }))
            }
        }
    }

    fun result(s: WorkoutInsightSnapshot, n: InsightNarrative): WorkoutInsightResult {
        val facts = facts(s)
        val labels = buildMap {
            val goals = mapOf("MUSCLE_GAIN" to "근육 증가", "DIET" to "다이어트", "FITNESS" to "체력 증진", "HABIT" to "운동 습관", "RECORD_IMPROVEMENT" to "기록 향상")
            val skills = mapOf("BEGINNER" to "입문", "UNDER_6_MONTHS" to "6개월 미만", "SIX_MONTHS_TO_ONE_YEAR" to "6개월~1년", "ONE_TO_THREE_YEARS" to "1~3년", "OVER_THREE_YEARS" to "3년 이상")
            put("profile", "프로필 목표 ${goals[s.goal] ?: "미설정"} · 숙련도 ${skills[s.skillLevel] ?: "미설정"}")
            put("session", "이번 운동 ${s.report.completedSetCount}세트 · ${s.report.totalRepetitions}회 · ${s.report.totalVolumeKg.stripTrailingZeros().toPlainString()}kg 볼륨 · ${s.report.durationSeconds}초")
            s.report.exercises.forEachIndexed { i, e ->
                put("exercise-$i", "${e.exerciseName}: ${e.completedSetCount}세트 · ${e.totalRepetitions}회 · ${e.totalVolumeKg.stripTrailingZeros().toPlainString()}kg" +
                    (e.previousComparison?.let { " (이전 ${it.previousVolumeKg.stripTrailingZeros().toPlainString()}kg)" } ?: " · 이전 비교 없음") +
                    e.completedSets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.let { " · 기록 RPE ${it.joinToString("/")}" }.orEmpty() +
                    if (e.completedSets.isEmpty()) "" else " · 세트별 휴식 ${e.completedSets.joinToString("/") { it.restSeconds?.let { seconds -> "${seconds}초" } ?: "미기록" }}")
            }
            s.inbody?.let { put("inbody", "인바디 ${it.measuredDate} · 체지방률 ${it.bodyFatPercentage}% · 골격근량 ${it.skeletalMuscleMassKg}kg") }
            s.samsungHealth?.let { put("samsung", "Samsung Health 활동 ${it.activeDurationSeconds}초 · 평균 심박 ${it.meanHeartRate ?: "미기록"} · 소비 칼로리 ${it.caloriesKcal ?: "미기록"}kcal") }
            s.longitudinal?.let { h ->
                put("history", "최근 ${h.weeks}주 헬스 ${h.sessions.size + 1}회(이번 운동 포함) · ${if (h.demo) "가상 기록" else "저장된 기록"}${if (h.truncated) " · 일부 기록만 분석" else ""}")
                if (h.bodies.isNotEmpty()) put("inbody", "선택 인바디 ${h.bodies.size}건 · ${h.bodies.first().measuredDate} ~ ${h.bodies.last().measuredDate}")
                val count = h.sessions.count { it.samsungHealth != null } + if (s.samsungHealth != null) 1 else 0
                if (count > 0) put("samsung", "연결된 Samsung Health 요약 ${count}건 · 세션 시간과 합산하지 않음")
                if (h.previousAdvice.isNotEmpty()) put("previous-advice", "이전 리포트 ${h.previousAdvice.size}건의 제안 · 실제 이행 여부는 확인 불가")
            }
        }
        fun point(p: InsightNarrativePoint, id: String): InsightPoint {
            checkRule(p.title.length in 2..90 && p.explanation.length in 20..800, "TEXT_LENGTH")
            checkRule(p.evidenceIds.size in 1..6 && p.evidenceIds.distinct().size == p.evidenceIds.size, "EVIDENCE_COUNT_OR_DUPLICATE")
            checkRule(p.evidenceIds.all { it in facts }, "UNKNOWN_EVIDENCE")
            fun render(text: String): String {
                val tokens = Regex("\\{\\{(exercise-\\d+)}}")
                tokens.findAll(text).forEach { checkRule(it.groupValues[1] in p.evidenceIds, "EXERCISE_CITATION_MISSING") }
                val plain = tokens.replace(text, "운동")
                // Measurements belong in server-owned evidence, not unverified numeric prose.
                checkRule(plain.none { it.isDigit() }, "NUMERIC_PROSE")
                checkRule("{{" !in plain && "}}" !in plain, "INVALID_PLACEHOLDER")
                checkRule(!Regex("https?://|[<>]|[\\p{Cntrl}&&[^\\n]]").containsMatchIn(plain), "UNSAFE_TEXT")
                return Regex("\\{\\{(exercise-\\d+)}}(으로|은|는|이|가|을|를|과|와|로)?").replace(text) {
                    val name = s.report.exercises[it.groupValues[1].substringAfter('-').toInt()].exerciseName
                    val ending = name.lastOrNull()?.code?.minus(0xAC00)
                    val jong = ending?.takeIf { code -> code in 0..11171 }?.rem(28)
                    val particle = it.groupValues[2]
                    val adjusted = if (jong == null) particle else when (particle) {
                        "은", "는" -> if (jong == 0) "는" else "은"
                        "이", "가" -> if (jong == 0) "가" else "이"
                        "을", "를" -> if (jong == 0) "를" else "을"
                        "과", "와" -> if (jong == 0) "와" else "과"
                        "으로", "로" -> if (jong == 0 || jong == 8) "로" else "으로"
                        else -> particle
                    }
                    name + adjusted
                }
            }
            return InsightPoint(id, render(p.title), render(p.explanation), p.evidenceIds.map { labels.getValue(it) })
        }
        checkRule(n.observations.size in 2..3 && n.nextActions.size in 1..2, "SECTION_COUNT")
        checkRule(n.summary.evidenceIds.contains("session"), "SUMMARY_SESSION_MISSING")
        if (s.goal != null) checkRule(n.summary.evidenceIds.contains("profile"), "SUMMARY_PROFILE_MISSING")
        val points = listOf(n.summary) + n.observations + n.nextActions
        checkRule(points.map { it.title }.distinct().size == points.size, "DUPLICATE_TITLE")
        val cited = points.flatMap { it.evidenceIds }.toSet()
        if (s.inbody != null) checkRule("inbody" in cited, "INBODY_COVERAGE_MISSING")
        if (s.samsungHealth != null) checkRule("samsung" in cited, "SAMSUNG_COVERAGE_MISSING")
        if (s.longitudinal != null) {
            checkRule("history" in cited, "HISTORY_COVERAGE_MISSING")
            if (s.longitudinal.sessions.any { it.samsungHealth != null }) checkRule("samsung" in cited, "SAMSUNG_COVERAGE_MISSING")
            if (s.longitudinal.bodies.isNotEmpty()) checkRule("inbody" in cited, "INBODY_COVERAGE_MISSING")
            if (s.longitudinal.previousAdvice.isNotEmpty()) checkRule("previous-advice" in cited, "PREVIOUS_ADVICE_COVERAGE_MISSING")
        }
        return WorkoutInsightResult("AI", point(n.summary, "summary"),
            n.observations.mapIndexed { i, p -> point(p, "observation-$i") },
            n.nextActions.mapIndexed { i, p -> point(p, "action-$i") }, s, if (s.longitudinal == null) "grounded-narrative-v2" else "longitudinal-narrative-v3")
    }
}
