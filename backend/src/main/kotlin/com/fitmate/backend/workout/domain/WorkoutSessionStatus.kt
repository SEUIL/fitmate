package com.fitmate.backend.workout.domain

enum class WorkoutSessionStatus {
    READY,
    IN_PROGRESS,
    PAUSED,
    SYNC_WAITING,
    REVIEW_REQUIRED,
    COMPLETED,
    CANCELED,
}
