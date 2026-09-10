package com.fitmate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitmate.app.samsunghealth.SamsungHealthStatus
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.fitmate.app.samsunghealth.SamsungWorkoutMetrics
import com.fitmate.app.samsunghealth.SamsungWorkoutUiModel
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SamsungHealthTestScreen(state: SamsungHealthUiState, onRequestPermissions: () -> Unit, onLoadSteps: () -> Unit, onLoadWorkouts: () -> Unit, onRefresh: () -> Unit, onResolve: () -> Unit, modifier: Modifier = Modifier) {
    val loading = state.status == SamsungHealthStatus.LOADING
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Samsung Health Data SDK 테스트", style = MaterialTheme.typography.headlineSmall)
        Text("상태: ${statusText(state.status)}")
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("개발 모드: 로컬 테스트에는 Samsung Health Data SDK의 Data Read 개발 모드가 필요합니다.")
        Text("권한 상태", style = MaterialTheme.typography.titleMedium)
        state.permissions.forEach { Text("${it.label}: ${if (it.granted) "허용" else "허용 안 됨"}") }
        Button(onClick = onRequestPermissions, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Samsung Health 읽기 권한 요청") }
        if (state.canResolve) OutlinedButton(onClick = onResolve, modifier = Modifier.fillMaxWidth()) { Text("Samsung Health 문제 해결 화면 열기") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLoadSteps, enabled = !loading) { Text("오늘 걸음 수 불러오기") }
            Button(onClick = onLoadWorkouts, enabled = !loading) { Text("최근 7일 운동 불러오기") }
        }
        OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("전체 새로고침") }
        state.lastLoadedAtMillis?.let { Text("마지막 조회: ${java.text.DateFormat.getDateTimeInstance().format(it)}") }
        Text("오늘 전체 걸음 수: ${state.todaySteps ?: "기록 없음"}", style = MaterialTheme.typography.titleMedium)
        state.stepRange?.let { Text("조회 기준: $it") }
        Text("최근 운동", style = MaterialTheme.typography.titleMedium)
        if (state.workouts.isEmpty() && state.status == SamsungHealthStatus.DATA_EMPTY) Text("최근 7일 운동 기록이 없습니다.")
        state.workouts.forEach { WorkoutCard(it) }
        Text("Samsung Health 앱에서 데이터 권한을 언제든 변경할 수 있습니다.")
    }
}

@Composable private fun WorkoutCard(workout: SamsungWorkoutUiModel) {
    val duration = Duration.between(workout.startTime, workout.endTime)
    val pace = SamsungWorkoutMetrics.paceSecondsPerKm(workout.distanceMeters, duration.seconds)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(workout.customTitle ?: workout.exerciseType, style = MaterialTheme.typography.titleMedium)
        Text("원본 데이터 UID: ${mask(workout.uid)}")
        Text("출처 앱: ${workout.sourceAppId ?: "기록 없음"}")
        Text("시작/종료: ${formatTime(workout.startTime)} ~ ${formatTime(workout.endTime)}")
        Text("운동 시간: ${duration.toMinutes()}분 · 실제 활동 ${workout.activeDurationSeconds}초 · 구간 ${workout.sessionCount}개")
        Text("원본 수정 시각: ${workout.sourceModifiedAt?.let(::formatTime) ?: "기록 없음"}")
        Text("거리: ${SamsungWorkoutMetrics.kilometers(workout.distanceMeters)?.let { "%.2f km".format(it) } ?: "기록 없음"}")
        Text("칼로리: ${workout.calories?.let { "%.0f kcal".format(it) } ?: "기록 없음"}")
        Text("심박수(평균/최저/최고): ${workout.meanHeartRate?.let { "%.0f".format(it) } ?: "-"} / ${workout.minHeartRate?.let { "%.0f".format(it) } ?: "-"} / ${workout.maxHeartRate?.let { "%.0f bpm".format(it) } ?: "-"}")
        Text("속도(평균/최고): ${SamsungWorkoutMetrics.kilometersPerHour(workout.meanSpeedMetersPerSecond)?.let { "%.1f".format(it) } ?: "-"} / ${SamsungWorkoutMetrics.kilometersPerHour(workout.maxSpeedMetersPerSecond)?.let { "%.1f km/h".format(it) } ?: "-"}")
        Text("평균 페이스: ${pace?.let(::formatPace) ?: "기록 없음"}")
        Text("고도 상승/하강: ${workout.altitudeGainMeters ?: "기록 없음"} / ${workout.altitudeLossMeters ?: "기록 없음"}")
        Text("케이던스: ${workout.meanCadence ?: "기록 없음"}, VO2 max: ${workout.vo2Max ?: "기록 없음"}")
        Text("ExerciseLog: ${workout.logSampleCount}개 샘플")
        workout.device?.let { device -> Text("원본 기기: ${device.label}\nID: ${device.maskedId ?: "기록 없음"}, ${device.type ?: ""} / ${device.manufacturer ?: ""} / ${device.model ?: ""} / ${device.name ?: ""}") } ?: Text("원본 기기: 기기 확인 불가")
    } }
}

private fun statusText(value: SamsungHealthStatus) = when (value) { SamsungHealthStatus.READY -> "사용 가능"; SamsungHealthStatus.LOADING -> "로딩 중"; SamsungHealthStatus.DATA_EMPTY -> "데이터 없음"; SamsungHealthStatus.PERMISSION_MISSING -> "권한 부족"; SamsungHealthStatus.PLATFORM_ERROR -> "Samsung Health 확인 필요"; SamsungHealthStatus.AUTHORIZATION_ERROR -> "개발 모드 또는 앱 인증 필요"; SamsungHealthStatus.UNKNOWN_ERROR -> "SDK 오류" }
private fun formatTime(value: java.time.Instant) = value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
private fun formatPace(seconds: Double): String { val value = seconds.toLong(); return "%d:%02d /km".format(value / 60, value % 60) }
private fun mask(value: String) = if (value.length <= 8) "••••" else "${value.take(4)}••••${value.takeLast(4)}"
