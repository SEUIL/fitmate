package com.fitmate.backend.report.application

import com.fitmate.backend.report.api.*
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Provider receives local references, never names or persistent identifiers. Evidence is rendered by the server. */
object WorkoutInsightNarrative {
    fun facts(s: WorkoutInsightSnapshot): Map<String, Map<String, Any?>> = buildMap {
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
        }
        fun point(p: InsightNarrativePoint, id: String): InsightPoint {
            require(p.title.length in 2..90 && p.explanation.length in 20..800)
            require(p.evidenceIds.size in 1..6 && p.evidenceIds.distinct().size == p.evidenceIds.size)
            require(p.evidenceIds.all { it in facts })
            fun render(text: String): String {
                val tokens = Regex("\\{\\{(exercise-\\d+)}}")
                tokens.findAll(text).forEach { require(it.groupValues[1] in p.evidenceIds) }
                val plain = tokens.replace(text, "운동")
                // Measurements belong in server-owned evidence, not unverified numeric prose.
                require(plain.none { it.isDigit() } && "{{" !in plain && "}}" !in plain)
                require(!Regex("https?://|[<>]|[\\p{Cntrl}&&[^\\n]]").containsMatchIn(plain))
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
        require(n.observations.size in 2..3 && n.nextActions.size in 1..2)
        require(n.summary.evidenceIds.contains("session"))
        if (s.goal != null) require(n.summary.evidenceIds.contains("profile"))
        val points = listOf(n.summary) + n.observations + n.nextActions
        require(points.map { it.title }.distinct().size == points.size)
        val cited = points.flatMap { it.evidenceIds }.toSet()
        if (s.inbody != null) require("inbody" in cited)
        if (s.samsungHealth != null) require("samsung" in cited)
        return WorkoutInsightResult("AI", point(n.summary, "summary"),
            n.observations.mapIndexed { i, p -> point(p, "observation-$i") },
            n.nextActions.mapIndexed { i, p -> point(p, "action-$i") }, s, "grounded-narrative-v2")
    }
}
