package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun HomeWorkoutCounts(api: FitMateApiClient?) {
    var counts by remember(api) { mutableStateOf<Pair<Int, Int>?>(null) }
    var failed by remember(api) { mutableStateOf(false) }
    var attempt by remember(api) { mutableIntStateOf(0) }
    LaunchedEffect(api, attempt) {
        counts = null; failed = false
        try {
            requireNotNull(api)
            counts = withContext(Dispatchers.IO) {
                val records = ServerRecordApi(api)
                records.completedCount("workout-sessions?status=COMPLETED&sportCode=HEALTH") to
                    records.completedCount("running-records")
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            failed = true
        }
    }
    WorkoutCard {
        Text("누적 운동 기록", style = MaterialTheme.typography.titleMedium)
        Text("서버에 저장된 완료 운동 기준", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            failed -> {
                Text("운동 횟수를 불러오지 못했어요.", color = MaterialTheme.colorScheme.error)
                TextButton({ attempt++ }) { Text("횟수 다시 불러오기") }
            }
            counts == null -> {
                Text("운동 횟수 불러오는 중…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WorkoutMetric("헬스", "${counts!!.first}회", Modifier.weight(1f))
                WorkoutMetric("러닝", "${counts!!.second}회", Modifier.weight(1f))
            }
        }
    }
}
