package com.fitmate.backend.report.application

import com.fitmate.backend.report.api.*
import org.springframework.stereotype.Component

/** Deterministic fallback catalog; generated narrative uses separate evidence validation. */
@Component
class WorkoutInsightRules {
    fun catalog(snapshot: WorkoutInsightSnapshot): InsightCatalog {
        val report = snapshot.report
        val summaries = mutableListOf(InsightPoint("recorded", "오늘의 운동을 기록했어요", "완료한 운동을 다음 운동의 비교 기준으로 남겼어요.", listOf("완료 ${report.completedSetCount}세트 · ${report.totalRepetitions}회")))
        val observations = mutableListOf<InsightPoint>()
        val hasEffort = report.exercises.any { e -> e.completedSets.any { it.rpe != null } }
        val actions = mutableListOf(if (hasEffort)
            InsightPoint("compare-effort", "기록한 체감 강도를 함께 비교해요", "다음 동일 운동에서도 수행 수치와 체감 강도를 함께 비교해 보세요.", listOf("완료 세트에 RPE 기록이 있습니다."))
        else InsightPoint("record-effort", "다음 운동의 체감 강도도 기록해요", "중량·반복 수와 함께 체감 강도를 남기면 다음 기록을 비교하기 쉬워요.", listOf("현재 기록은 수행 수치 중심입니다.")))
        if (report.completedSetCount == 0) {
            summaries.clear()
            summaries += InsightPoint("empty", "완료한 세트 기록이 없어요", "이번 기록만으로 운동 수행을 평가하기 어려워요.", listOf("완료 세트 0개"))
        } else {
            val comparable = report.exercises.count { it.completedSetCount > 0 && it.previousComparison != null }
            if (comparable > 0) summaries += InsightPoint("compare", "이전 기록과 함께 살펴볼 수 있어요", "같은 운동의 기록을 비교하되, 수행 조건이 달랐는지도 함께 확인해 보세요.", listOf("이전 기록이 있는 운동 ${comparable}종"))
            if (snapshot.goal == "HABIT") summaries += InsightPoint("habit", "운동 습관을 위한 기록을 남겼어요", "오늘의 기록에 다음 운동 일정을 이어 보세요.", listOf("프로필 목표: 운동 습관", "완료 ${report.completedSetCount}세트"))
        }
        report.exercises.forEachIndexed { index, exercise ->
            if (exercise.completedSetCount == 0) return@forEachIndexed
            val evidence = listOf("${exercise.exerciseName}: ${exercise.completedSetCount}세트 · ${exercise.totalRepetitions}회 · ${exercise.totalVolumeKg.stripTrailingZeros().toPlainString()}kg 볼륨")
            val previous = exercise.previousComparison
            if (previous == null) observations += InsightPoint("exercise-$index", "${exercise.exerciseName} 비교 기준이 생겼어요", "다음에 같은 운동을 기록하면 변화를 비교할 수 있어요.", evidence)
            else {
                val delta = previous.volumeChangeKg
                val direction = if (delta.signum() > 0) "늘었어요" else if (delta.signum() < 0) "줄었어요" else "같아요"
                observations += InsightPoint("exercise-$index", "${exercise.exerciseName} 기록 볼륨이 $direction", "세트 수·중량·반복 수가 함께 영향을 줍니다. 이 변화만으로 수행 능력 향상이나 저하를 단정하지 않아요.", evidence + "이전 ${previous.previousVolumeKg}kg → 이번 ${previous.currentVolumeKg}kg")
            }
        }
        when (snapshot.goal) {
            "HABIT" -> actions += InsightPoint("goal-habit", "다음 운동 일정을 정해요", "반복할 수 있는 다음 운동 일정을 미리 정해 두세요.", listOf("프로필 목표: 운동 습관"))
            "MUSCLE_GAIN", "RECORD_IMPROVEMENT" -> actions += InsightPoint("goal-compare", "같은 운동의 수행 조건을 함께 확인해요", "다음 동일 운동에서는 세트 수·반복 수·체감 강도를 함께 비교해 보세요. 자동으로 중량을 올리지는 않아요.", listOf("프로필 목표: ${if (snapshot.goal == "MUSCLE_GAIN") "근육 증가" else "기록 향상"}"))
            "DIET" -> actions += InsightPoint("goal-diet", "운동 기록을 꾸준히 쌓아요", "한 번의 운동이나 소비 칼로리만으로 체지방 변화를 판단하지 말고 기록을 이어가세요.", listOf("프로필 목표: 다이어트"))
            "FITNESS" -> actions += InsightPoint("goal-fitness", "다음 운동의 컨디션을 함께 남겨요", "수행 수치와 당시 느낌을 함께 남겨 지속하기 편한 운동 흐름을 확인해 보세요.", listOf("프로필 목표: 체력 증진"))
        }
        if (snapshot.skillLevel in setOf("BEGINNER", "UNDER_6_MONTHS")) actions += InsightPoint("skill-form", "동작과 기록의 일관성을 먼저 확인해요", "다음 운동에서도 같은 동작 기준으로 반복 수를 기록해 보세요.", listOf("프로필: 헬스 초기 숙련 단계"))
        snapshot.inbody?.let { body ->
            observations += InsightPoint("inbody-context", "선택한 인바디를 참고했어요", "한 번의 체성분 측정으로 이번 운동의 효과나 적정 중량을 산출하지 않아요.", listOf("측정일 ${body.measuredDate}", "체지방률 ${body.bodyFatPercentage}% · 골격근량 ${body.skeletalMuscleMassKg}kg"))
        }
        snapshot.samsungHealth?.let { summary ->
            observations += InsightPoint("samsung-context", "같은 운동의 기기 기록이 있어요", "기기 활동 시간은 FitMate 경과 시간과 측정 기준이 달라 별도로 표시해요.", listOf("Samsung Health 활동 ${summary.activeDurationSeconds}초"))
            summary.meanHeartRate?.let { hr -> observations += InsightPoint("samsung-heart", "운동 중 평균 심박이 기록됐어요", "기기 측정 요약입니다. 심박 수치만으로 운동 강도 구간이나 건강 상태를 판정하지 않아요.", listOf("Samsung Health 평균 심박 $hr bpm")) }
        }
        if (observations.isEmpty()) observations += InsightPoint("missing", "비교할 수행 정보가 부족해요", "완료한 세트의 중량과 반복 수를 기록해 주세요.", listOf("완료 ${report.completedSetCount}세트"))
        snapshot.longitudinal?.let { h ->
            summaries.add(0, InsightPoint("history-summary", if (h.demo) "가상 기록의 운동 흐름을 살펴봤어요" else "최근 운동 속에서 이번 기록을 살펴봤어요",
                if (h.sessions.isEmpty()) "기간 내 이전 헬스 기록이 없어 이번 운동을 비교 기준으로 남겨요. 다음 동일 운동의 수행과 체감 강도를 함께 확인해 보세요."
                else "이번 운동을 포함한 헬스 기록을 모았어요. 목표에 맞는 진행 여부는 수행 조건과 꾸준함을 함께 비교해야 해요.",
                listOf("최근 ${h.weeks}주 · 이전 ${h.sessions.size}회 + 이번 운동", "현재 목표 기준 · 다른 종목 제외")))
            observations.add(0, InsightPoint("history-frequency", "기록한 운동 간격을 확인해요",
                "운동을 기록한 날짜를 비교해 다음에 이어갈 일정을 정해 보세요. 기록이 없는 날을 운동하지 않은 날로 단정하지 않아요.",
                listOf((h.sessions.map { it.report.endedAt.toString().take(10) } + report.endedAt.toString().take(10)).joinToString(" · "))))
            actions.add(InsightPoint("history-next", "다음에는 같은 운동의 체감 강도를 비교해요",
                "같은 운동의 반복 수행 여부와 마지막 세트의 체감 강도를 함께 확인해 보세요. 중량 변화만으로 수행이 좋아졌다고 판단하지 않아요.", listOf("이번 완료 ${report.completedSetCount}세트 · 이전 헬스 ${h.sessions.size}회")))
            if (h.previousAdvice.isNotEmpty()) observations.add(1, InsightPoint("prior-advice", "이전 제안을 다시 확인해요",
                "지난 리포트의 제안과 이번 기록을 함께 확인해 보세요. 수행 기록만으로 제안을 실제로 지켰는지는 확인할 수 없어요.",
                h.previousAdvice.flatMap { it.actions }.take(2).map { it.title }))
        }
        return InsightCatalog(summaries, observations, actions)
    }

    fun fallback(catalog: InsightCatalog) = InsightSelection(catalog.summaries.first().id, catalog.observations.take(3).map { it.id }, catalog.actions.takeLast(2).map { it.id })

    fun result(snapshot: WorkoutInsightSnapshot, catalog: InsightCatalog, selection: InsightSelection, source: String): WorkoutInsightResult {
        if (source == "AI") return WorkoutInsightNarrative.result(snapshot, requireNotNull(selection.narrative))
        fun select(ids: List<String>, options: List<InsightPoint>): List<InsightPoint> {
            require(ids.size in 1..3 && ids.distinct().size == ids.size)
            return ids.map { id -> requireNotNull(options.singleOrNull { it.id == id }) }
        }
        return WorkoutInsightResult(source, requireNotNull(catalog.summaries.singleOrNull { it.id == selection.summaryId }),
            select(selection.observationIds, catalog.observations), select(selection.actionIds, catalog.actions), snapshot)
    }
}
