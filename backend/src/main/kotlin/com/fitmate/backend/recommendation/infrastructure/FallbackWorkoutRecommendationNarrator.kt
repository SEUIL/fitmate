package com.fitmate.backend.recommendation.infrastructure

import com.fitmate.backend.inbody.domain.InbodyRecord

class FallbackWorkoutRecommendationNarrator : WorkoutRecommendationNarrator {
    override fun narrate(name: String, reasons: List<String>, cardioMinutes: Int, inbody: InbodyRecord?): String? = null
}
