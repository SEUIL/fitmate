package com.fitmate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

internal val inbodyExtraFields = linkedMapOf(
    "신장 (cm)" to 250.0, "체지방량 (kg)" to 300.0, "BMI" to 100.0,
    "제지방량 (kg)" to 300.0, "체수분 (L)" to 200.0, "단백질 (kg)" to 100.0,
    "무기질 (kg)" to 100.0, "복부지방률" to 2.0, "내장지방 레벨" to 100.0,
    "기초대사량 (kcal)" to 10000.0, "인바디 점수" to 100.0,
    "오른팔 근육량 (kg)" to 100.0, "왼팔 근육량 (kg)" to 100.0,
    "몸통 근육량 (kg)" to 200.0, "오른다리 근육량 (kg)" to 100.0, "왼다리 근육량 (kg)" to 100.0,
    "오른팔 지방량 (kg)" to 100.0, "왼팔 지방량 (kg)" to 100.0, "몸통 지방량 (kg)" to 300.0,
    "오른다리 지방량 (kg)" to 100.0, "왼다리 지방량 (kg)" to 100.0,
    "비만도 (%)" to 500.0, "권장 체중 (kg)" to 500.0
)
internal data class BodyEntry(
    val id: String = UUID.randomUUID().toString(), val date: LocalDate,
    val weight: Double, val muscle: Double, val fat: Double,
    val extras: Map<String, Double> = emptyMap(), val source: String = "직접 입력"
)
internal fun parseBodyDate(value: String): LocalDate? = runCatching {
    LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(ResolverStyle.STRICT))
}.getOrNull()
internal fun validateBody(date: String, weight: String, muscle: String, fat: String, extras: Map<String, String>, today: LocalDate = LocalDate.now()): String? {
    val parsedDate = parseBodyDate(date) ?: return "측정일을 실제 날짜(YYYY.MM.DD)로 입력하세요."
    if (parsedDate > today) return "미래 측정일은 저장할 수 없어요."
    val w = weight.toDoubleOrNull(); val m = muscle.toDoubleOrNull(); val f = fat.toDoubleOrNull()
    if (w == null || !w.isFinite() || w !in 1.0..500.0) return "체중은 1~500 kg로 입력하세요."
    if (m == null || !m.isFinite() || m !in 0.0..w) return "골격근량은 0 이상, 체중 이하로 입력하세요."
    if (f == null || !f.isFinite() || f !in 0.0..100.0) return "체지방률은 0~100%로 입력하세요."
    extras.forEach { (label, value) ->
        if (value.isNotBlank()) {
            val number = value.toDoubleOrNull()
            if (number == null || !number.isFinite() || number !in 0.0..(inbodyExtraFields[label] ?: 1000.0)) return "$label 값을 확인하세요."
        }
    }
    return null
}

internal class InbodyState {
    val records = mutableStateListOf(BodyEntry(date = LocalDate.of(2026, 8, 15), weight = 68.2, muscle = 29.4, fat = 18.7))
    var phase by mutableStateOf("목록")
    var photo by mutableStateOf<String?>(null)
    var source by mutableStateOf("직접 입력")
    var date by mutableStateOf("")
    var weight by mutableStateOf("")
    var muscle by mutableStateOf("")
    var fat by mutableStateOf("")
    var extras by mutableStateOf(emptyMap<String, String>())
    var acknowledged by mutableStateOf(false)
    var showDetails by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var simulateFailure by mutableStateOf(false)
    var deleting by mutableStateOf<BodyEntry?>(null)
    var metric by mutableStateOf("체중")
    fun openManual() {
        source = "직접 입력"; photo = null; phase = "검수"; error = null; extras = emptyMap()
        date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        weight = ""; muscle = ""; fat = ""; acknowledged = false
    }
    fun selectPhoto(uri: String) { photo = uri; phase = "사진 확인"; source = "사진 예시 판독"; error = null }
}

@Composable internal fun InbodyFlow(modifier: Modifier, state: InbodyState) = with(state) {
    BackHandler(phase != "목록") { phase = "목록"; error = null }
    LaunchedEffect(phase) {
        if (phase == "업로드 준비") {
            delay(800)
            phase = "판독 중"
        } else if (phase == "판독 중") {
            delay(1200)
            if (simulateFailure) { phase = "판독 실패"; error = "결과표를 판독하지 못했어요. 재촬영하거나 직접 입력해 주세요." }
            else {
                date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                weight = "68.2"; muscle = "29.4"; fat = "18.7"; extras = emptyMap()
                acknowledged = false; phase = "검수"
            }
        }
    }
    deleting?.let { entry -> ConfirmAction("측정 기록 삭제", "${entry.date} 기록을 삭제할까요? 삭제 후 변화 차트에도 반영됩니다.", { deleting = null }) { records.removeAll { it.id == entry.id }; deleting = null } }
    Screen(modifier) {
        Text("의료 진단이 아닌 운동 기록입니다. 측정값을 직접 확인하세요.", style = MaterialTheme.typography.bodySmall)
        when (phase) {
            "목록" -> {
                SoftCard {
                    Text("결과표 등록", fontWeight = FontWeight.Bold)
                    Text("사진 선택·촬영은 기기에서 동작합니다. OCR 연결 전이라 판독은 예시 값으로 진행됩니다.", style = MaterialTheme.typography.bodySmall)
                    PhotoActions(::selectPhoto, camera = true)
                    OutlinedButton(::openManual, Modifier.fillMaxWidth()) { Text("직접 입력") }
                }
                val sorted = records.sortedByDescending { it.date }
                if (sorted.isEmpty()) SoftCard { Text("아직 측정 기록이 없어요. 첫 기록을 등록해 보세요.") }
                else {
                    SoftCard {
                        Text("변화 차트", fontWeight = FontWeight.Bold)
                        Choices(listOf("체중", "골격근량", "체지방률"), metric) { metric = it }
                        val chronological = sorted.reversed()
                        val values = chronological.map { when (metric) { "골격근량" -> it.muscle; "체지방률" -> it.fat; else -> it.weight } }
                        val color = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val min = values.minOrNull() ?: 0.0
                            val span = ((values.maxOrNull() ?: 0.0) - min).coerceAtLeast(1.0)
                            val points = values.mapIndexed { i, value -> Offset(12f + i.toFloat() / (values.size - 1).coerceAtLeast(1) * (size.width - 24f), size.height - 12f - ((value - min) / span).toFloat() * (size.height - 24f)) }
                            points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 4f) }
                            points.forEach { drawCircle(color, 6f, it) }
                        }
                        chronological.forEachIndexed { i, entry -> Text("${entry.date} · ${values[i]} ${if (metric == "체지방률") "%" else "kg"}", style = MaterialTheme.typography.bodySmall) }
                        if (sorted.size == 1) Text("두 번째 기록부터 변화가 연결됩니다.")
                    }
                    Text("최근 기록 3개", fontWeight = FontWeight.Bold)
                    sorted.take(3).forEachIndexed { index, entry ->
                        BodyRecordCard(entry, sorted.getOrNull(index + 1), { deleting = entry })
                    }
                    if (sorted.size > 3) {
                        var older by remember { mutableStateOf(false) }
                        OutlinedButton({ older = !older }) { Text(if (older) "이전 기록 접기" else "이전 기록 ${sorted.size - 3}개 보기") }
                        if (older) sorted.drop(3).forEachIndexed { index, entry -> BodyRecordCard(entry, sorted.getOrNull(index + 4), { deleting = entry }) }
                    }
                }
            }
            "사진 확인", "판독 실패" -> {
                photo?.let { LocalPhoto(it) }
                Text("전체 결과표의 글자가 선명하게 보이는지 확인하세요.")
                ErrorText(error)
                PhotoActions(::selectPhoto, camera = true)
                DebugOnly { ToggleLine("테스트: 판독 실패 재현", simulateFailure) { simulateFailure = it } }
                PrimaryAction(if (phase == "판독 실패") "다시 판독" else "예시 판독 시작") { error = null; phase = "업로드 준비" }
                OutlinedButton(::openManual) { Text("직접 입력으로 전환") }
                OutlinedButton({ phase = "목록" }) { Text("등록 취소") }
            }
            "업로드 준비", "판독 중" -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (phase == "업로드 준비") "업로드 준비 · 서버 연결 전으로 실제 전송하지 않습니다." else "판독 중 · OCR 연동 전 예시 결과를 준비하고 있어요.")
                OutlinedButton({ phase = "사진 확인" }) { Text("판독 취소") }
            }
            else -> {
                SoftCard {
                    Text("판독 결과 검수 · $source", fontWeight = FontWeight.Bold)
                    if (source != "직접 입력") {
                        Text("예시 판독값입니다. 실제 사진의 수치로 수정하세요. 골격근량·체지방률은 재확인이 필요합니다.", color = MaterialTheme.colorScheme.error)
                        photo?.let { LocalPhoto(it) }
                    }
                    OutlinedTextField(date, { date = it }, label = { Text("측정일 (YYYY.MM.DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(weight, { weight = it }, label = { Text("체중 (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(muscle, { muscle = it }, label = { Text("골격근량 (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(fat, { fat = it }, label = { Text("체지방률 (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton({ showDetails = !showDetails }) { Text(if (showDetails) "상세 항목 접기" else "상세 측정 항목 입력 (선택)") }
                    if (showDetails) inbodyExtraFields.keys.forEach { label -> OutlinedTextField(extras[label].orEmpty(), { extras = extras + (label to it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    if (source != "직접 입력") ToggleLine("확인이 필요한 수치를 검수했어요", acknowledged) { acknowledged = it }
                    ErrorText(error)
                }
                PrimaryAction("검수 후 저장") {
                    error = validateBody(date, weight, muscle, fat, extras)
                    if (error == null && source != "직접 입력" && !acknowledged) error = "예시 판독 수치를 확인하고 검수 완료를 선택하세요."
                    if (error == null) {
                        records.add(0, BodyEntry(date = parseBodyDate(date)!!, weight = weight.toDouble(), muscle = muscle.toDouble(), fat = fat.toDouble(), extras = extras.filterValues { it.isNotBlank() }.mapValues { it.value.toDouble() }, source = source))
                        phase = "목록"; photo = null
                    }
                }
                if (source != "직접 입력") PhotoActions(::selectPhoto, camera = true)
                OutlinedButton({ phase = "목록"; error = null }) { Text("등록 취소") }
            }
        }
    }
}

@Composable private fun BodyRecordCard(entry: BodyEntry, previous: BodyEntry?, delete: () -> Unit) = SoftCard {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Text("${entry.date} · ${entry.source}", fontWeight = FontWeight.Bold)
    Text("체중 ${entry.weight} kg · 골격근량 ${entry.muscle} kg · 체지방률 ${entry.fat}%")
    Text(previous?.let { "이전 기록 대비 체중 ${"%+.1f".format(entry.weight - it.weight)} kg" } ?: "비교할 이전 기록이 없어요.")
    Row {
        TextButton({ expanded = !expanded }) { Text(if (expanded) "접기" else "상세 보기") }
        TextButton(delete) { Text("삭제") }
    }
    if (expanded) {
        if (entry.extras.isEmpty()) Text("추가 측정 항목이 없습니다.")
        entry.extras.forEach { (label, value) -> Text("$label · $value") }
    }
}
