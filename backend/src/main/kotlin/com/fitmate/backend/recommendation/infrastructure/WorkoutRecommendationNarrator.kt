package com.fitmate.backend.recommendation.infrastructure

import com.fitmate.backend.inbody.domain.InbodyRecord

/** AI는 검증된 추천의 설명만 보완하며 운동 구성이나 안전 기준을 변경하지 않는다. */
interface WorkoutRecommendationNarrator {
    fun narrate(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String?
}
