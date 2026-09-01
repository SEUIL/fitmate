package com.fitmate.app.samsunghealth

enum class SamsungHealthStatus { READY, LOADING, DATA_EMPTY, PERMISSION_MISSING, PLATFORM_ERROR, AUTHORIZATION_ERROR, UNKNOWN_ERROR }

data class SamsungPermissionStatus(val label: String, val granted: Boolean)

data class SamsungHealthUiState(
    val status: SamsungHealthStatus = SamsungHealthStatus.LOADING,
    val message: String? = null,
    val canResolve: Boolean = false,
    val permissions: List<SamsungPermissionStatus> = emptyList(),
    val todaySteps: Long? = null,
    val stepRange: String? = null,
    val workouts: List<SamsungWorkoutUiModel> = emptyList(),
    val lastLoadedAtMillis: Long? = null
)
