package com.fitmate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlin.math.roundToInt

internal enum class RoutePreviewKind { EMPTY, SINGLE_POINT, PATH }

internal data class RoutePreviewPoint(val x: Float, val y: Float)

internal data class RoutePreview(
    val kind: RoutePreviewKind,
    val points: List<RoutePreviewPoint>,
)

/** Produces a display-only, unit-square shape. Original coordinate values never become UI text. */
internal fun routePreview(points: List<Pair<Double, Double>>): RoutePreview {
    val valid = points.filter { (latitude, longitude) ->
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    if (valid.isEmpty()) return RoutePreview(RoutePreviewKind.EMPTY, emptyList())
    if (valid.size == 1) return RoutePreview(RoutePreviewKind.SINGLE_POINT, listOf(RoutePreviewPoint(0.5f, 0.5f)))
    val minLatitude = valid.minOf { it.first }
    val maxLatitude = valid.maxOf { it.first }
    val minLongitude = valid.minOf { it.second }
    val maxLongitude = valid.maxOf { it.second }
    val latitudeRange = (maxLatitude - minLatitude).takeIf { it > 0.0 } ?: 1.0
    val longitudeRange = (maxLongitude - minLongitude).takeIf { it > 0.0 } ?: 1.0
    return RoutePreview(
        RoutePreviewKind.PATH,
        valid.map { (latitude, longitude) ->
            RoutePreviewPoint(
                x = ((longitude - minLongitude) / longitudeRange).toFloat(),
                y = (1.0 - (latitude - minLatitude) / latitudeRange).toFloat(),
            )
        },
    )
}

internal data class RunningAnalysisPresentation(
    val available: Boolean,
    val heartRateLines: List<String>,
    val paceLines: List<String>,
    val limitations: List<String>,
    val analysisVersion: String?,
)

internal fun runningAnalysisPresentation(payload: JsonObject?, totalSampleCount: Int = 0): RunningAnalysisPresentation {
    if (payload == null) return RunningAnalysisPresentation(
        available = false,
        heartRateLines = emptyList(),
        paceLines = emptyList(),
        limitations = listOf("분석 데이터를 불러오지 못했습니다."),
        analysisVersion = null,
    )
    val heart = payload.getAsJsonObject("heartRate") ?: JsonObject()
    val pace = payload.getAsJsonObject("pace") ?: JsonObject()
    val heartCount = heart.intOrZero("validSampleCount")
    val samsungPaceCount = pace.intOrZero("samsungSampleCount")
    val gpsPaceCount = pace.intOrZero("smartphoneGpsSampleCount")
    val heartLines = buildList {
        add("유효 심박 샘플 ${heartCount}개 · 구간 포함률 ${heart.intOrZero("coveragePercent")}%")
        heart.decimalText("peakBpm")?.let { peak ->
            add("최고 심박 ${peak.toBigDecimal().setScale(0, java.math.RoundingMode.HALF_UP)} bpm" +
                heart.longOrNull("peakOffsetSeconds")?.let { " · 시작 후 ${elapsedLabel(it)}" }.orEmpty())
        }
        heart.getAsJsonObject("peakSustained")?.let { segment ->
            add("높은 심박 지속 구간 · ${elapsedLabel(segment.longOrZero("startOffsetSeconds"))}~${elapsedLabel(segment.longOrZero("endOffsetSeconds"))} · ${segment.intOrZero("sampleCount")}개")
        }
        heart.getAsJsonObject("biggestRise")?.let { change ->
            add("가장 큰 상승 구간 · ${elapsedLabel(change.longOrZero("startOffsetSeconds"))}~${elapsedLabel(change.longOrZero("endOffsetSeconds"))} · ${signedDecimal(change.decimalText("changeBpm"))} bpm")
        }
        heart.getAsJsonObject("biggestFall")?.let { change ->
            add("가장 큰 하강 구간 · ${elapsedLabel(change.longOrZero("startOffsetSeconds"))}~${elapsedLabel(change.longOrZero("endOffsetSeconds"))} · ${signedDecimal(change.decimalText("changeBpm"))} bpm")
        }
        val gaps = heart.intOrZero("longGapCount")
        if (gaps > 0) add("심박 데이터의 긴 공백 ${gaps}개 · 최대 ${heart.longOrZero("maxGapSeconds")}초")
    }
    val paceLines = buildList {
        val sources = buildList {
            if (pace.booleanOrFalse("samsungSourceUsed")) add("Samsung Health")
            if (pace.booleanOrFalse("smartphoneGpsSourceUsed")) add("스마트폰 GPS")
        }
        add("페이스 출처 · ${sources.ifEmpty { listOf("사용 가능한 데이터 없음") }.joinToString(" + ")}")
        pace.intOrNull("samsungFastestPaceSecondsPerKm")?.let { add("Samsung 최고 페이스 · ${paceLabel(it)}") }
        pace.intOrNull("smartphoneGpsFastestPaceSecondsPerKm")?.let { add("GPS 최고 페이스 · ${paceLabel(it)}") }
        if (samsungPaceCount > 0) add("Samsung 속도 기반 샘플 ${samsungPaceCount}개")
        if (gpsPaceCount > 0) add("GPS 속도 기반 샘플 ${gpsPaceCount}개")
        if (totalSampleCount > 0 && samsungPaceCount in 1 until totalSampleCount) {
            add("일부 Samsung 샘플에는 속도 값이 없어 있는 구간만 분석했습니다.")
        }
    }
    val limitations = payload.getAsJsonArray("limitations")?.mapNotNull { item ->
        item.takeUnless { it.isJsonNull }?.asString?.let(::analysisLimitationLabel)
    }.orEmpty().distinct()
    val available = payload.booleanOrFalse("available")
    val analysisVersion = payload.get("analysisVersion")?.takeUnless { it.isJsonNull }?.asString
        ?.takeIf { available && it.isNotBlank() }
    return RunningAnalysisPresentation(available, heartLines, paceLines, limitations, analysisVersion)
}

@Composable
internal fun RunningRecordReport(row: JsonObject) {
    WorkoutCard {
        Text("기본 러닝 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        WorkoutMetrics(
            "거리" to "${"%.2f".format((row.recordInt("distanceMeters") ?: 0) / 1000.0)} km",
            "활동 시간" to durationLabel(row.recordInt("activeDurationSeconds") ?: 0),
        )
        WorkoutMetrics(
            "평균 페이스" to paceLabel(row.recordInt("averagePaceSecondsPerKm")),
            "최고 페이스" to paceLabel(row.recordInt("bestPaceSecondsPerKm")),
        )
        row.get("elapsedDurationSeconds")?.takeUnless { it.isJsonNull }?.asInt?.let {
            Text("전체 경과 시간 ${durationLabel(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        row.get("goalAchieved")?.takeUnless { it.isJsonNull }?.let {
            Text(if (it.asBoolean) "목표 달성" else "목표 미달성", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
    WorkoutCard {
        Text("구간·체감 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("RPE ${row.recordInt("rpe") ?: "—"} · 컨디션 ${row.recordInt("conditionScore") ?: "—"}/5")
        row.rows("laps").forEach { lap ->
            Text("${lap.recordInt("lapNo")}랩 · ${lap.recordInt("distanceMeters")}m · ${paceLabel(lap.recordInt("averagePaceSecondsPerKm"))}")
        }
        if (row.rows("laps").isEmpty()) Text("저장된 랩이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.text("memo").isNotBlank()) Text(row.text("memo"))
    }
    val route = row.rows("routePoints").mapNotNull { point ->
        val latitude = point.get("latitude")?.takeUnless { it.isJsonNull }?.asDouble
        val longitude = point.get("longitude")?.takeUnless { it.isJsonNull }?.asDouble
        if (latitude == null || longitude == null) null else latitude to longitude
    }.let(::routePreview)
    PrivateRouteCard(route)
}

@Composable
internal fun RunningAnalysisCard(payload: JsonObject?, totalSampleCount: Int, loading: Boolean, error: String?) {
    WorkoutCard {
        Text("심박수·페이스 분석", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when {
            loading -> Text("분석 결과를 불러오는 중…")
            error != null -> {
                Text(error, color = MaterialTheme.colorScheme.error)
                Text("기본 러닝 기록과 경로는 그대로 확인할 수 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val presentation = runningAnalysisPresentation(payload, totalSampleCount)
                presentation.heartRateLines.forEach { Text(it) }
                presentation.paceLines.forEach { Text(it) }
                if (!presentation.available && presentation.heartRateLines.isEmpty() && presentation.paceLines.isEmpty()) {
                    Text("분석할 수 있는 시계열 데이터가 없습니다.")
                }
                presentation.limitations.forEach { Text("분석 한계 · $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                presentation.analysisVersion?.let {
                    Text("분석 버전 · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("측정된 구간의 기록 요약이며 원인, 회복력 또는 질환을 판단하지 않습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrivateRouteCard(route: RoutePreview) {
    WorkoutCard {
        Text("비공개 경로", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("경로는 나만 볼 수 있으며 실제 좌표 값은 표시하지 않습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (route.kind) {
            RoutePreviewKind.EMPTY -> Text("저장된 경로가 없습니다.")
            RoutePreviewKind.SINGLE_POINT, RoutePreviewKind.PATH -> {
                val lineColor = MaterialTheme.colorScheme.primary
                val pointColor = MaterialTheme.colorScheme.tertiary
                val background = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
                    drawRect(background)
                    val inset = 18.dp.toPx()
                    fun position(point: RoutePreviewPoint) = Offset(
                        inset + point.x * (size.width - inset * 2),
                        inset + point.y * (size.height - inset * 2),
                    )
                    if (route.kind == RoutePreviewKind.SINGLE_POINT) {
                        drawCircle(pointColor, radius = 7.dp.toPx(), center = position(route.points.single()))
                    } else {
                        val path = Path()
                        route.points.forEachIndexed { index, point ->
                            val position = position(point)
                            if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                        drawCircle(pointColor, radius = 5.dp.toPx(), center = position(route.points.first()))
                        drawCircle(pointColor, radius = 5.dp.toPx(), center = position(route.points.last()))
                    }
                }
                Text(if (route.kind == RoutePreviewKind.SINGLE_POINT) "경로 지점이 1개라 선 대신 한 점으로 표시합니다." else "기록된 경로의 상대적인 형태입니다.")
            }
        }
    }
}

private fun JsonObject.intOrNull(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asInt
private fun JsonObject.intOrZero(key: String) = intOrNull(key) ?: 0
private fun JsonObject.longOrNull(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asLong
private fun JsonObject.longOrZero(key: String) = longOrNull(key) ?: 0L
private fun JsonObject.booleanOrFalse(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
private fun JsonObject.decimalText(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString
private fun durationLabel(seconds: Int) = "${seconds / 60}분 ${seconds % 60}초"
private fun elapsedLabel(seconds: Long) = "${seconds / 60}분 ${seconds % 60}초"
private fun paceLabel(seconds: Int?) = seconds?.let { "%d:%02d /km".format(it / 60, it % 60) } ?: "—"
private fun signedDecimal(value: String?) = value?.toBigDecimalOrNull()?.let { if (it.signum() > 0) "+$it" else it.toPlainString() } ?: "—"
private fun analysisLimitationLabel(code: String) = when (code) {
    "TIME_SERIES_NOT_CONNECTED" -> "Samsung Health 시계열이 연결되지 않았습니다."
    "HEART_RATE_INSUFFICIENT_SAMPLES" -> "심박수 샘플이 부족해 구간 변화를 계산하지 않았습니다."
    "HEART_RATE_LONG_GAPS" -> "심박수 기록에 긴 공백이 있어 공백을 가로질러 해석하지 않았습니다."
    "HEART_RATE_LOW_COVERAGE" -> "심박수 기록이 운동 구간의 일부만 포함합니다."
    "PACE_DATA_UNAVAILABLE" -> "Samsung 속도와 GPS 페이스 데이터가 없습니다."
    "PACE_PARTIAL_SAMPLES" -> "일부 Samsung 샘플에 속도 값이 없습니다."
    "HEART_RATE_PACE_NOT_ALIGNED" -> "심박수와 페이스를 같은 시각 구간으로 맞출 수 없습니다."
    else -> "일부 데이터가 부족해 표시 범위가 제한됩니다."
}
