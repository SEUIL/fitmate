package com.fitmate.backend.report.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.health.repository.ExternalWorkoutSummaryRepository
import com.fitmate.backend.inbody.repository.InbodyRecordRepository
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.report.api.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.util.UUID

@Service
class WorkoutInsightReader(
    private val reports: WorkoutReportService,
    private val goals: UserGoalRepository,
    private val levels: UserSportRepository,
    private val sports: SportRepository,
    private val inbody: InbodyRecordRepository,
    private val summaries: ExternalWorkoutSummaryRepository,
    private val historyReader: WorkoutInsightHistory,
) {
    @Transactional(readOnly = true)
    fun hasSamsungSummary(userId: UUID, sessionId: UUID): Boolean =
        summaries.findByWorkoutSessionIdAndUserId(UuidBinaryConverter.toMysqlOrderedUuid(sessionId), userId) != null

    @Transactional(readOnly = true)
    fun read(userId: UUID, sessionId: UUID, request: CreateWorkoutInsightRequest): WorkoutInsightSnapshot {
        val originalReport = reports.get(userId, sessionId)
        val history = historyReader.read(userId, originalReport, request)
        val eligible = history.sessions.map { it.report.workoutSessionId }.toSet()
        val report = originalReport.copy(exercises = originalReport.exercises.map { e ->
            e.copy(previousComparison = e.previousComparison?.takeIf { it.previousWorkoutSessionId in eligible })
        })
        val goal = goals.findAllByIdUserId(userId).singleOrNull { it.isPrimary }?.id?.goalCode?.name
        val sport = sports.findByCodeAndIsActiveTrue(SportCode.HEALTH)
        val skill = sport?.let { s -> levels.findAllByIdUserId(userId).firstOrNull { it.id.sportId == s.id }?.skillLevel?.name }
        val body = history.bodies.lastOrNull() ?: request.inbodyRecordId?.let { id ->
            val record = inbody.findByIdAndUserId(id, userId)
                ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "선택한 인바디 기록을 찾을 수 없습니다.")
            InsightBodyComposition(record.measuredDate, record.bodyFatPercentage, record.skeletalMuscleMassKg)
        }
        val samsung = if (request.includeSamsungHealth) summaries.findByWorkoutSessionIdAndUserId(
            UuidBinaryConverter.toMysqlOrderedUuid(sessionId), userId,
        )?.let { InsightSamsungSummary(it.activeDurationSeconds, it.caloriesKcal, it.meanHeartRate) } else null
        val notices = buildList {
            add("분석 범위: 이번 운동과 최근 ${history.weeks}주 헬스 기록 ${history.sessions.size}회. 다른 종목과 미기록 활동은 포함하지 않습니다.")
            add("현재 목표를 기준으로 해석합니다. 과거 목표 변경 이력·계획 이행·통증·자세·회복 상태는 확인할 수 없습니다.")
            if (history.demo) add("[DEMO] 가상 운동 데이터입니다. 실제 건강·운동 상태를 평가하지 않습니다. 선택 인바디는 별도 사용자 기록입니다.")
            if (history.truncated) add("최근 40개 세션 후보로 제한되어 기간 전체 기록을 대표하지 않을 수 있습니다.")
            if (history.previousAdvice.isNotEmpty()) add("이전 리포트는 당시 제안만 참고합니다. 제안 이행 여부와 효과를 단정하지 않습니다.")
            if (history.bodies.size > 1) add("여러 인바디의 측정 간격과 추이를 참고하되 운동의 인과적 효과로 해석하지 않습니다.")
            add("운동 시간은 FitMate 세션의 경과 시간입니다. Samsung Health 활동 시간과 합산하지 않습니다.")
            add("운동량 변화만으로 근력 향상·운동 효과·적정 중량을 판단하지 않습니다.")
            if (goal == null || skill == null) add("프로필 목표 또는 숙련도 정보가 부족해 일부 맞춤 해석을 제외했습니다.")
            if (body == null) add("인바디를 사용하지 않았습니다.") else {
                add("최근 선택 인바디 측정일: ${body.measuredDate}. 측정값만으로 운동 효과나 건강 상태를 판단하지 않습니다.")
                val days = java.time.temporal.ChronoUnit.DAYS.between(body.measuredDate, report.endedAt.atZone(ZoneOffset.UTC).toLocalDate())
                if (days < 0) add("선택 인바디는 운동 이후 측정값입니다. 운동 당시 상태로 해석하지 않습니다.")
                if (days > 90) add("선택 인바디는 운동일보다 90일 이상 오래된 기록입니다.")
            }
            if (samsung == null) add(if (request.includeSamsungHealth) "이 운동에 연결해 저장한 Samsung Health 요약이 없어 제외했습니다." else "Samsung Health를 사용하지 않았습니다.")
            else if (samsung.meanHeartRate == null) add("Samsung Health 심박 정보가 없어 심박 관련 해석을 제외했습니다.")
            if (report.exercises.none { it.previousComparison != null }) add("비교할 이전 동일 운동 기록이 없습니다.")
        }
        return WorkoutInsightSnapshot(report, goal, skill, body, samsung, notices, history)
    }
}
