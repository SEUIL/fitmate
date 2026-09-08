package com.fitmate.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared by draft review and persisted records; all values come from the existing API model. */
@Composable private fun detailBorderColor() = if (MaterialTheme.colorScheme.surface.luminance() > .5f)
    MaterialTheme.colorScheme.onSurface.copy(alpha = .19f)
else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)

// Neutral athletic anatomy traced from the approved reference, not a body-shape assessment.
private fun muscularSilhouette() = Path().apply {
    moveTo(70f, 4f)
    cubicTo(59f, 3f, 55f, 10f, 56f, 22f)
    cubicTo(51f, 23f, 54f, 33f, 58f, 36f)
    lineTo(61f, 45f); lineTo(60f, 53f)
    cubicTo(51f, 58f, 37f, 60f, 33f, 65f)
    cubicTo(25f, 74f, 29f, 87f, 24f, 98f)
    cubicTo(19f, 113f, 20f, 125f, 16f, 137f)
    cubicTo(10f, 149f, 13f, 158f, 7f, 167f)
    cubicTo(2f, 170f, 0f, 182f, 1f, 188f)
    quadraticTo(3f, 192f, 5f, 182f)
    lineTo(5f, 195f); quadraticTo(8f, 199f, 11f, 190f)
    lineTo(16f, 181f); lineTo(17f, 168f)
    cubicTo(24f, 157f, 28f, 145f, 30f, 135f)
    cubicTo(37f, 125f, 39f, 111f, 42f, 94f)
    cubicTo(45f, 110f, 49f, 121f, 48f, 134f)
    cubicTo(46f, 147f, 39f, 164f, 40f, 179f)
    cubicTo(35f, 199f, 40f, 224f, 43f, 244f)
    cubicTo(39f, 256f, 39f, 273f, 43f, 286f)
    lineTo(47f, 321f); lineTo(45f, 333f)
    cubicTo(39f, 342f, 37f, 350f, 44f, 351f)
    lineTo(58f, 352f); quadraticTo(63f, 350f, 59f, 340f)
    lineTo(57f, 328f)
    cubicTo(57f, 301f, 66f, 280f, 61f, 252f)
    cubicTo(64f, 227f, 66f, 207f, 70f, 184f)
    cubicTo(74f, 207f, 76f, 227f, 79f, 252f)
    cubicTo(74f, 280f, 83f, 301f, 83f, 328f)
    lineTo(81f, 340f); quadraticTo(77f, 350f, 82f, 352f)
    lineTo(96f, 351f); cubicTo(103f, 350f, 101f, 342f, 95f, 333f)
    lineTo(93f, 321f); lineTo(97f, 286f)
    cubicTo(101f, 273f, 101f, 256f, 97f, 244f)
    cubicTo(100f, 224f, 105f, 199f, 100f, 179f)
    cubicTo(101f, 164f, 94f, 147f, 92f, 134f)
    cubicTo(91f, 121f, 95f, 110f, 98f, 94f)
    cubicTo(101f, 111f, 103f, 125f, 110f, 135f)
    cubicTo(112f, 145f, 116f, 157f, 123f, 168f)
    lineTo(124f, 181f); lineTo(129f, 190f)
    quadraticTo(132f, 199f, 135f, 195f); lineTo(135f, 182f)
    quadraticTo(137f, 192f, 139f, 188f)
    cubicTo(140f, 182f, 138f, 170f, 133f, 167f)
    cubicTo(127f, 158f, 130f, 149f, 124f, 137f)
    cubicTo(120f, 125f, 121f, 113f, 116f, 98f)
    cubicTo(111f, 87f, 115f, 74f, 107f, 65f)
    cubicTo(103f, 60f, 89f, 58f, 80f, 53f)
    lineTo(79f, 45f); lineTo(82f, 36f)
    cubicTo(86f, 33f, 89f, 23f, 84f, 22f)
    cubicTo(85f, 10f, 81f, 3f, 70f, 4f); close()
}

@Composable internal fun InbodyDetailsDesign(
    modifier: Modifier, date: String, weight: String, muscle: String, fat: String,
    extras: Map<String, String>
) {
    val fatKeys = listOf("체지방량 (kg)", "BMI", "복부지방률", "비만도 (%)")
    val parts = listOf("왼팔", "오른팔", "몸통", "왼다리", "오른다리")
    val muscleKeys = parts.map { "$it 근육량 (kg)" }
    val segmentFatKeys = parts.map { "$it 지방량 (kg)" }
    val goals = listOf("권장 체중 (kg)", "체중 조절 (kg)")
    val remaining = inbodyExtraFields.keys.filter { it !in fatKeys + muscleKeys + segmentFatKeys + goals }
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(date.replace('-', '.'), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            DetailPanel("핵심 요약", Icons.Outlined.BarChart) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Triple("체중", weight, "kg"), Triple("골격근량", muscle, "kg"), Triple("체지방률", fat, "%")).forEach { (label, value, unit) ->
                        Column(Modifier.weight(1f)) { DetailValue(label, value, unit, compact = true) }
                    }
                }
            }
        }
        item { DetailPanel("체지방 · 비만", Icons.Outlined.LocalFireDepartment) { MeasurementGrid(fatKeys, extras) } }
        item {
            DetailPanel("부위별 근육량", Icons.Outlined.FitnessCenter) {
                SegmentFigure(muscleKeys.map { extras[it].orEmpty() })
                Text("좌우는 측정 대상자의 신체 기준입니다", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DetailPanel("부위별 지방량", Icons.Outlined.AccessibilityNew) {
                SegmentFigure(segmentFatKeys.map { extras[it].orEmpty() }, muscular = false)
                Text("신체 그림은 항목 구분용이며 실제 체형을 나타내지 않습니다", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DetailPanel("체중 목표", Icons.Outlined.Flag) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    goals.forEach { key ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .055f), border = BorderStroke(1.dp, detailBorderColor())) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (key == goals.first()) Icons.Outlined.TrackChanges else Icons.Outlined.Balance, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                DetailValue(key.substringBefore(" ("), extras[key].orEmpty(), "kg", compact = true, accent = true)
                            }
                        }
                    }
                }
                Text("결과표에 기재된 참고 수치입니다", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { ExpandableMeasurements("기타 측정 정보", Icons.Outlined.Assessment, remaining, extras) }
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("의료 진단이 아닌 운동 기록 참고용 결과입니다.\n미측정 항목은 임의로 채우지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun DetailPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, detailBorderColor())) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable private fun DetailValue(label: String, raw: String, unit: String, compact: Boolean = false, accent: Boolean = false, alignment: Alignment.Horizontal = Alignment.Start, modifier: Modifier = Modifier) {
    val value = raw.toDoubleOrNull()?.let { java.math.BigDecimal(raw).stripTrailingZeros().toPlainString() } ?: raw
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.isBlank()) Text("미측정", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            // Units wrap below the number on narrow layouts instead of clipping the measurement.
            Text(value, fontSize = if (compact) 25.sp else 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun MeasurementGrid(keys: List<String>, values: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        keys.chunked(2).forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEachIndexed { column, key ->
                    if (column > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    Column(Modifier.weight(1f)) { DetailValue(key.substringBefore(" ("), values[key].orEmpty(), if ('(' in key) key.substringAfter('(').substringBefore(')') else "") }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun ExpandableMeasurements(title: String, icon: ImageVector, keys: List<String>, values: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, detailBorderColor())) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${keys.count { !values[it].isNullOrBlank() }}/${keys.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "접기" else "펼치기", Modifier.size(20.dp))
            }
            AnimatedVisibility(expanded) { Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) { MeasurementGrid(keys, values) } }
        }
    }
}

@Composable private fun SegmentFigure(values: List<String>, muscular: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f).heightIn(min = 320.dp).padding(top = 48.dp, bottom = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            DetailValue("오른팔", values[1], "kg", compact = true, accent = true, alignment = Alignment.End, modifier = Modifier.width(50.dp))
            DetailValue("오른다리", values[4], "kg", compact = true, accent = true, alignment = Alignment.End, modifier = Modifier.width(50.dp))
        }
        Canvas(Modifier.weight(1.35f).fillMaxHeight()) {
            withTransform({ scale(size.width / 140f, size.height / 360f, Offset.Zero) }) {
                // Curved symmetric contour, front view. Subject's right is viewer's left.
                val body = Path().apply {
                    moveTo(70f, 9f); cubicTo(49f, 8f, 49f, 27f, 51f, 43f)
                    cubicTo(48f, 46f, 50f, 56f, 55f, 58f); lineTo(57f, 70f)
                    cubicTo(46f, 76f, 32f, 77f, 28f, 91f)
                    cubicTo(24f, 113f, 22f, 140f, 17f, 158f)
                    lineTo(5f, 204f); cubicTo(1f, 216f, 2f, 231f, 8f, 229f)
                    lineTo(15f, 216f); lineTo(16f, 223f); lineTo(20f, 216f)
                    lineTo(31f, 166f); lineTo(40f, 125f)
                    cubicTo(43f, 151f, 39f, 167f, 38f, 185f)
                    cubicTo(34f, 208f, 40f, 237f, 42f, 259f)
                    cubicTo(38f, 282f, 45f, 317f, 47f, 333f)
                    cubicTo(39f, 341f, 40f, 350f, 52f, 351f)
                    cubicTo(66f, 354f, 64f, 342f, 62f, 332f)
                    lineTo(64f, 267f); lineTo(70f, 216f)
                    lineTo(76f, 267f); lineTo(78f, 332f)
                    cubicTo(76f, 342f, 74f, 354f, 88f, 351f)
                    cubicTo(100f, 350f, 101f, 341f, 93f, 333f)
                    cubicTo(95f, 317f, 102f, 282f, 98f, 259f)
                    cubicTo(100f, 237f, 106f, 208f, 102f, 185f)
                    cubicTo(101f, 167f, 97f, 151f, 100f, 125f)
                    lineTo(109f, 166f); lineTo(120f, 216f); lineTo(124f, 223f)
                    lineTo(125f, 216f); lineTo(132f, 229f)
                    cubicTo(138f, 231f, 139f, 216f, 135f, 204f)
                    lineTo(123f, 158f); cubicTo(118f, 140f, 116f, 113f, 112f, 91f)
                    cubicTo(108f, 77f, 94f, 76f, 83f, 70f); lineTo(85f, 58f)
                    cubicTo(90f, 56f, 92f, 46f, 89f, 43f)
                    cubicTo(91f, 27f, 91f, 8f, 70f, 9f); close()
                }
                val silhouette = if (muscular) muscularSilhouette() else body
                drawPath(silhouette, accent.copy(alpha = .16f)); drawPath(silhouette, outline, style = Stroke(1f))
                if (muscular) {
                    val muscles = Path().apply {
                        moveTo(42f, 62f); lineTo(46f, 88f); lineTo(45f, 102f)
                        moveTo(98f, 62f); lineTo(94f, 88f); lineTo(95f, 102f)
                        moveTo(46f, 64f); lineTo(70f, 70f); lineTo(94f, 64f)
                        moveTo(70f, 70f); lineTo(70f, 103f)
                        moveTo(42f, 106f); lineTo(70f, 103f); lineTo(98f, 106f)
                        moveTo(24f, 100f); lineTo(39f, 108f)
                        moveTo(116f, 100f); lineTo(101f, 108f)
                        moveTo(43f, 145f); lineTo(70f, 184f); lineTo(97f, 145f)
                        moveTo(43f, 244f); quadraticTo(52f, 239f, 62f, 245f)
                        moveTo(78f, 245f); quadraticTo(88f, 239f, 97f, 244f)
                    }
                    drawPath(muscles, outline.copy(alpha = .7f), style = Stroke(.85f))
                }
                if (!muscular) listOf(Offset(40f, 86f) to Offset(70f, 95f), Offset(70f, 95f) to Offset(100f, 86f),
                    Offset(70f, 95f) to Offset(70f, 173f), Offset(40f, 183f) to Offset(70f, 213f),
                    Offset(70f, 213f) to Offset(100f, 183f)).forEach { (a,b) -> drawLine(outline.copy(alpha = .25f), a,b,1f) }
                // Leaders extend into the side gutters, leaving the labels at the card edges.
                listOf(
                    Triple(Offset(29f, 108f), Offset(0f, 88f), Offset(-48f, 88f)),
                    Triple(Offset(111f, 108f), Offset(140f, 88f), Offset(188f, 88f)),
                    Triple(Offset(70f, 165f), Offset(140f, 176f), Offset(188f, 176f)),
                    Triple(Offset(51f, 285f), Offset(0f, 268f), Offset(-48f, 268f)),
                    Triple(Offset(89f, 285f), Offset(140f, 268f), Offset(188f, 268f))
                ).forEach { (anchor, elbow, end) ->
                    drawPath(Path().apply { moveTo(anchor.x, anchor.y); lineTo(elbow.x, elbow.y); lineTo(end.x, end.y) }, outline, style = Stroke(1f))
                    drawCircle(accent, 3f, anchor)
                }
            }
        }
        Column(Modifier.weight(1f).heightIn(min = 320.dp).padding(top = 48.dp, bottom = 18.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            DetailValue("왼팔", values[0], "kg", compact = true, accent = true, alignment = Alignment.Start, modifier = Modifier.width(50.dp))
            DetailValue("몸통", values[2], "kg", compact = true, accent = true, alignment = Alignment.Start, modifier = Modifier.width(50.dp))
            DetailValue("왼다리", values[3], "kg", compact = true, accent = true, alignment = Alignment.Start, modifier = Modifier.width(50.dp))
        }
    }
}
