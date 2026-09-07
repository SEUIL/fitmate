package com.fitmate.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fitmate.app.BuildConfig
import com.fitmate.app.data.remote.ApiExercise
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface ExerciseCatalogState {
    data object Loading : ExerciseCatalogState
    data class Ready(val exercises: List<ApiExercise>) : ExerciseCatalogState
    data class Failed(val message: String) : ExerciseCatalogState
}

internal class ExerciseCatalog(private val api: FitMateApiClient) {
    var state: ExerciseCatalogState by mutableStateOf(ExerciseCatalogState.Loading)
        private set

    suspend fun refresh() {
        state = ExerciseCatalogState.Loading
        state = runCatching { withContext(Dispatchers.IO) { api.fetchExercises() } }.fold(
            onSuccess = { ExerciseCatalogState.Ready(it) },
            onFailure = { ExerciseCatalogState.Failed(it.message ?: "운동 목록을 불러오지 못했습니다.") }
        )
    }
}

@Composable
internal fun rememberFitMateApiClient(): FitMateApiClient = remember {
    FitMateApiClient(BuildConfig.API_BASE_URL)
}

@Composable
internal fun rememberExerciseCatalog(api: FitMateApiClient): ExerciseCatalog = remember(api) {
    ExerciseCatalog(api)
}
