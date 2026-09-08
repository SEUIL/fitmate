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
import androidx.compose.ui.platform.LocalContext
import com.fitmate.app.data.remote.ServerInbodyApi
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    "비만도 (%)" to 500.0, "권장 체중 (kg)" to 500.0, "체중 조절 (kg)" to 500.0
).mapValues { (label, maximum) ->
    when {
        label.endsWith("(kg)") || label.endsWith("(L)") -> 500.0
        label == "복부지방률" -> 5.0
        label == "비만도 (%)" -> 100.0
        else -> maximum
    }
}
internal val inbodyFieldLabels = mapOf(
    "HEIGHT_CM" to "신장 (cm)", "BODY_FAT_MASS_KG" to "체지방량 (kg)", "BMI" to "BMI",
    "FAT_FREE_MASS_KG" to "제지방량 (kg)", "BODY_WATER_L" to "체수분 (L)", "PROTEIN_KG" to "단백질 (kg)",
    "MINERALS_KG" to "무기질 (kg)", "WAIST_HIP_RATIO" to "복부지방률", "VISCERAL_FAT_LEVEL" to "내장지방 레벨",
    "BASAL_METABOLIC_RATE_KCAL" to "기초대사량 (kcal)", "INBODY_SCORE" to "인바디 점수",
    "RIGHT_ARM_MUSCLE_KG" to "오른팔 근육량 (kg)", "LEFT_ARM_MUSCLE_KG" to "왼팔 근육량 (kg)", "TRUNK_MUSCLE_KG" to "몸통 근육량 (kg)",
    "RIGHT_LEG_MUSCLE_KG" to "오른다리 근육량 (kg)", "LEFT_LEG_MUSCLE_KG" to "왼다리 근육량 (kg)",
    "RIGHT_ARM_FAT_KG" to "오른팔 지방량 (kg)", "LEFT_ARM_FAT_KG" to "왼팔 지방량 (kg)", "TRUNK_FAT_KG" to "몸통 지방량 (kg)",
    "RIGHT_LEG_FAT_KG" to "오른다리 지방량 (kg)", "LEFT_LEG_FAT_KG" to "왼다리 지방량 (kg)",
    "OBESITY_DEGREE_PERCENT" to "비만도 (%)", "RECOMMENDED_WEIGHT_KG" to "권장 체중 (kg)", "WEIGHT_CONTROL_KG" to "체중 조절 (kg)"
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
            if (number == null || !number.isFinite() || number !in (when(label) { "체중 조절 (kg)" -> -500.0; "신장 (cm)" -> 50.0; else -> 0.0 })..(when(label) { "비만도 (%)" -> 100.0; "복부지방률" -> 5.0; else -> inbodyExtraFields[label] ?: 500.0 })) return "$label 값을 확인하세요."
        }
    }
    return null
}

internal class InbodyState {
    val records = mutableStateListOf<BodyEntry>()
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
    var analysisId by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var listError by mutableStateOf<String?>(null)
    var analysisStatus by mutableStateOf("")
    var mediaId by mutableStateOf<String?>(null)
    var createUncertain by mutableStateOf(false)
    var saveUncertain by mutableStateOf(false)
    var reviewNotice by mutableStateOf("")
    var selectedRecord by mutableStateOf<BodyEntry?>(null)
    fun resetAnalysis() { analysisId = null; mediaId = null; analysisStatus = ""; createUncertain = false; saveUncertain = false; reviewNotice = "" }
    var deleting by mutableStateOf<BodyEntry?>(null)
    var metric by mutableStateOf("체중")
    fun openManual() {
        resetAnalysis()
        source = "직접 입력"; photo = null; phase = "검수"; error = null; extras = emptyMap()
        date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        weight = ""; muscle = ""; fat = ""; acknowledged = false
    }
    fun selectPhoto(uri: String) { resetAnalysis(); acknowledged = false; photo = uri; phase = "사진 확인"; source = "사진 판독"; error = null }
}

private fun JsonObject.bodyText(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.numberText(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
internal fun recordEntry(row: JsonObject) = BodyEntry(
    id = row.bodyText("id"), date = LocalDate.parse(row.bodyText("measuredDate")), weight = row.numberText("weightKg").toDouble(),
    muscle = row.numberText("skeletalMuscleMassKg").toDouble(), fat = row.numberText("bodyFatPercentage").toDouble(),
    extras = row.getAsJsonObject("optionalValues")?.entrySet()?.mapNotNull { (code, value) -> inbodyFieldLabels[code]?.let { it to value.asDouble } }?.toMap().orEmpty(),
    source = if (row.bodyText("sourceType") == "AI_OCR") "사진 판독" else "직접 입력"
)

@Composable internal fun InbodyFlow(modifier: Modifier, state: InbodyState, server: ServerInbodyApi) = with(state) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() = scope.launch {
        if (refreshing) return@launch
        refreshing = true; listError = null
        try {
            val entries = withContext(Dispatchers.IO) { server.records().map(::recordEntry) }
            records.clear(); records.addAll(entries); loaded = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { listError = e.message ?: "인바디 기록을 불러오지 못했습니다." }
        finally { refreshing = false }
    }
    fun applyAnalysis(row: JsonObject) {
        val fields = row.getAsJsonArray("fields")?.map { it.asJsonObject }.orEmpty()
        fun field(code: String) = fields.firstOrNull { it.bodyText("code") == code }?.bodyText("value").orEmpty()
        date = field("MEASUREMENT_DATE").replace('-', '.')
        weight = field("WEIGHT_KG"); muscle = field("SKELETAL_MUSCLE_MASS_KG"); fat = field("BODY_FAT_PERCENT")
        extras = fields.mapNotNull { item -> inbodyFieldLabels[item.bodyText("code")]?.let { it to item.bodyText("value") } }.toMap()
        val labels = inbodyFieldLabels + mapOf("MEASUREMENT_DATE" to "측정일", "WEIGHT_KG" to "체중", "SKELETAL_MUSCLE_MASS_KG" to "골격근량", "BODY_FAT_PERCENT" to "체지방률")
        val review = fields.filter { it.bodyText("status") != "RECOGNIZED" }.mapNotNull { labels[it.bodyText("code")] }
        reviewNotice = (if (row.bodyText("provider") == "MOCK") "서버 예시 판독입니다. 실제 AI 분석이 아닙니다.\n" else "") +
            if (review.isNotEmpty()) "재확인 항목: ${review.joinToString()}" else "모든 수치를 실제 결과표와 비교해 주세요."
        acknowledged = false; phase = "검수"
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) { onDispose { busy = false } }
    BackHandler(busy || phase != "목록") { if (!busy) { phase = if (phase == "상세") "검수" else "목록"; error = null } }
    deleting?.let { entry -> ConfirmAction("측정 기록 삭제", "${entry.date} 기록을 삭제할까요? 삭제 후 변화 차트에도 반영됩니다.", { if (!busy) deleting = null }) {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { server.delete(entry.id) }
                    records.removeAll { it.id == entry.id }; deleting = null; refresh()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { listError = e.message ?: "기록을 삭제하지 못했습니다."; deleting = null }
                finally { busy = false }
            }
        }
    } }
    if (phase == "상세" || phase == "기록 상세") {
        val entry = selectedRecord.takeIf { phase == "기록 상세" }
        InbodyDetailsDesign(modifier, entry?.date?.toString() ?: date,
            entry?.weight?.toString() ?: weight, entry?.muscle?.toString() ?: muscle,
            entry?.fat?.toString() ?: fat,
            entry?.extras?.mapValues { it.value.toString() } ?: extras)
        return@with
    }
    Screen(modifier) {
        Text("의료 진단이 아닌 운동 기록입니다. 측정값을 직접 확인하세요.", style = MaterialTheme.typography.bodySmall)
        when (phase) {
            "목록" -> {
                if (refreshing || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                ErrorText(listError)
                TextButton({ refresh() }, enabled = !refreshing && !busy) { Text("기록 새로고침") }
                if (analysisId != null && analysisStatus !in setOf("COMPLETED", "REVIEW_REQUIRED")) {
                    OutlinedButton({ phase = "판독 중" }) { Text("진행 중인 판독 확인") }
                }
                SoftCard {
                    Text("결과표 등록", fontWeight = FontWeight.Bold)
                    Text("사진을 업로드해 판독한 뒤 측정값을 직접 검수합니다.", style = MaterialTheme.typography.bodySmall)
                    if (!busy) PhotoActions(::selectPhoto, camera = true)
                    OutlinedButton(::openManual, Modifier.fillMaxWidth(), enabled = !busy) { Text("직접 입력") }
                }
                val sorted = records.sortedByDescending { it.date }
                if (loaded && !refreshing && listError == null && sorted.isEmpty()) SoftCard { Text("아직 측정 기록이 없어요. 첫 기록을 등록해 보세요.") }
                else {
                    sorted.firstOrNull()?.let { latest ->
                        SoftCard {
                            Text("최근 인바디", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${latest.date} · ${latest.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InbodySummaryMetric("체중", latest.weight, "kg")
                                InbodySummaryMetric("골격근량", latest.muscle, "kg")
                                InbodySummaryMetric("체지방률", latest.fat, "%")
                            }
                            TextButton({ selectedRecord = latest; phase = "기록 상세" }) { Text("상세 결과 보기") }
                        }
                    }
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
                    Text("측정 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    sorted.take(3).forEachIndexed { index, entry ->
                        BodyRecordCard(entry, sorted.getOrNull(index + 1), { selectedRecord = entry; phase = "기록 상세" }, { deleting = entry })
                    }
                    if (sorted.size > 3) {
                        var older by remember { mutableStateOf(false) }
                        OutlinedButton({ older = !older }) { Text(if (older) "이전 기록 접기" else "이전 기록 ${sorted.size - 3}개 보기") }
                        if (older) sorted.drop(3).forEachIndexed { index, entry -> BodyRecordCard(entry, sorted.getOrNull(index + 4), { selectedRecord = entry; phase = "기록 상세" }, { deleting = entry }) }
                    }
                }
            }
            "사진 확인", "판독 실패" -> {
                photo?.let { LocalPhoto(it) }
                Text("전체 결과표의 글자가 선명하게 보이는지 확인하세요.")
                Text("판독 시작 시 결과표 사진이 서버와 외부 AI 서비스로 전송될 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                ErrorText(error)
                if (!busy) PhotoActions(::selectPhoto, camera = true)
                Button({
                    if (busy) return@Button
                    val uri = photo ?: return@Button
                    val existing = analysisId
                    busy = true; error = null
                    scope.launch {
                        try {
                            if (existing != null) {
                                val current = withContext(Dispatchers.IO) { server.analysis(existing) }
                                analysisStatus = current.bodyText("status")
                                if (analysisStatus == "FAILED") withContext(Dispatchers.IO) { server.retry(existing) }
                            } else {
                                if (mediaId == null) mediaId = withContext(Dispatchers.IO) { server.upload(context, uri) }
                                createUncertain = true
                                val created = withContext(Dispatchers.IO) { server.createAnalysis(requireNotNull(mediaId)) }
                                analysisId = created.bodyText("id")
                                createUncertain = false
                            }
                            phase = "판독 중"
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) {
                            phase = "판독 실패"
                            error = if (createUncertain) "판독 요청의 접수 여부를 확인하지 못했습니다. 중복 분석을 막기 위해 자동 재요청하지 않습니다. 잠시 후 확인하거나 직접 입력하세요." else e.message
                        } finally { busy = false }
                    }
                }, enabled = !busy && !createUncertain) { Text(if (busy) "요청 중…" else if (analysisId != null) "상태 확인 / 실패한 판독 재시도" else "사진 전송 및 판독 시작") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(::openManual, enabled = !busy) { Text("직접 입력으로 전환") }
                OutlinedButton({ phase = "목록" }, enabled = !busy) { Text("목록으로") }
            }
            "업로드 준비", "판독 중" -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("결과표를 분석하고 있습니다. 1분 후에도 완료되지 않으면 다시 상태를 확인할 수 있어요.")
                OutlinedButton({ phase = "목록" }) { Text("목록에서 기다리기") }
                Text("화면을 나가도 서버의 분석은 계속됩니다.", style = MaterialTheme.typography.bodySmall)
                LaunchedEffect(analysisId, phase) {
                    val id = analysisId ?: run { phase = "사진 확인"; return@LaunchedEffect }
                    try {
                        repeat(30) {
                            val result = withContext(Dispatchers.IO) { server.analysis(id) }
                            analysisStatus = result.bodyText("status")
                            when (analysisStatus) {
                                "COMPLETED", "REVIEW_REQUIRED" -> { applyAnalysis(result); return@LaunchedEffect }
                                "FAILED" -> { phase = "판독 실패"; error = result.get("failure")?.takeIf { it.isJsonObject }?.asJsonObject?.bodyText("message") ?: "판독에 실패했습니다."; return@LaunchedEffect }
                            }
                            delay(2_000)
                        }
                        phase = "판독 실패"; error = "서버의 판독이 아직 완료되지 않았어요. 같은 판독의 상태를 다시 확인하세요."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { phase = "판독 실패"; error = e.message ?: "판독 상태를 확인하지 못했습니다." }
                }
            }
            else -> {
                InbodyReviewContent(state)
                Button({
                    if (busy) return@Button
                    error = validateBody(date, weight, muscle, fat, extras)
                    if (error == null && source != "직접 입력" && !acknowledged) error = "판독 수치를 확인하고 검수 완료를 선택하세요."
                    if (error == null) {
                        val body = inbodySaveBody(state)
                        busy = true; saveUncertain = true
                        scope.launch {
                            try {
                                val saved = withContext(Dispatchers.IO) { server.save(body) }
                                val entry = recordEntry(saved)
                                records.removeAll { it.id == entry.id }; records.add(0, entry)
                                phase = "목록"; photo = null; resetAnalysis(); refresh()
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) {
                                saveUncertain = e !is com.fitmate.app.data.remote.ApiFailure || e.status >= 500
                                error = if (saveUncertain) "저장 응답을 확인하지 못했습니다. 중복 저장 전에 목록을 새로고침해 확인하세요." else e.message
                            } finally { busy = false }
                        }
                    }
                }, enabled = !busy && !saveUncertain) { Text(if (busy) "저장 중…" else "검수 후 저장") }
                if (!busy && source != "직접 입력") PhotoActions(::selectPhoto, camera = true)
                OutlinedButton({ phase = "목록"; error = null; refresh() }, enabled = !busy) { Text(if (saveUncertain) "목록에서 저장 여부 확인" else "목록으로") }

            }
        }
    }
}

@Composable private fun InbodyReviewContent(state: InbodyState) = with(state) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    fun valueFor(key: String) = when (key) {
        "측정일" -> date; "체중" -> weight; "골격근량" -> muscle; else -> fat
    }
    fun update(key: String, value: String) {
        when (key) {
            "측정일" -> date = value; "체중" -> weight = value; "골격근량" -> muscle = value; else -> fat = value
        }
    }
    editingKey?.let { key ->
        var draft by remember(key) { mutableStateOf(valueFor(key)) }
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("$key 수정") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text(if (key == "측정일") "YYYY.MM.DD" else key) }, singleLine = true) },
            confirmButton = { TextButton({ update(key, draft); editingKey = null }) { Text("완료") } },
            dismissButton = { TextButton({ editingKey = null }) { Text("취소") } }
        )
    }
    Text("인바디 결과 검수", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    SoftCard {
        Text(if (source == "직접 입력") "직접 입력 기록" else "AI 판독 완료", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("측정일 $date · 사진 속 수치를 확인·수정해 주세요.", style = MaterialTheme.typography.bodySmall)
        if (source != "직접 입력") {
            Text(reviewNotice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            photo?.let { LocalPhoto(it) }
        }
    }
    SoftCard {
        Text("핵심 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ReviewMetricRow("체중", "$weight kg", "지난 기록 대비는 저장 후 확인", { editingKey = "체중" })
        HorizontalDivider()
        ReviewMetricRow("골격근량", "$muscle kg", "운동 변화의 핵심 지표", { editingKey = "골격근량" })
        HorizontalDivider()
        ReviewMetricRow("체지방률", "$fat %", "사진의 수치와 비교해 주세요", { editingKey = "체지방률" })
    }
    OutlinedButton({ editingKey = "측정일" }, Modifier.fillMaxWidth()) { Text("측정일 수정") }
    OutlinedButton({ phase = "상세" }, Modifier.fillMaxWidth()) { Text("신체 구성 상세 보기") }
    if (showDetails) {
        SoftCard {
            Text("상세 측정 항목 수정", fontWeight = FontWeight.Bold)
            inbodyExtraFields.keys.forEach { label ->
                OutlinedTextField(extras[label].orEmpty(), { extras = extras + (label to it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
    }
    OutlinedButton({ showDetails = !showDetails }, Modifier.fillMaxWidth()) { Text(if (showDetails) "상세 항목 수정 닫기" else "상세 항목 직접 수정") }
    if (source != "직접 입력") ToggleLine("결과표 수치를 직접 확인했어요", acknowledged) { acknowledged = it }
    ErrorText(error)
}

@Composable private fun ReviewMetricRow(label: String, value: String, helper: String, edit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(helper, style = MaterialTheme.typography.bodySmall) }
        TextButton(edit) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
    }
}

internal fun inbodySaveBody(state: InbodyState): Map<String, Any?> = with(state) {
    mapOf("analysisId" to analysisId, "measuredDate" to requireNotNull(parseBodyDate(date)).toString(),
        "weightKg" to weight.toDouble(), "skeletalMuscleMassKg" to muscle.toDouble(), "bodyFatPercentage" to fat.toDouble(),
        "optionalValues" to extras.mapNotNull { (label, value) ->
            if (value.isBlank()) null else requireNotNull(inbodyFieldLabels.entries.firstOrNull { it.value == label }?.key) to value.toDouble()
        }.toMap())
}

@Composable private fun InbodySummaryMetric(label: String, value: Double, unit: String) = Column {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text("${"%.1f".format(value)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable private fun BodyRecordCard(entry: BodyEntry, previous: BodyEntry?, open: () -> Unit, delete: () -> Unit) = SoftCard {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(entry.date.toString(), fontWeight = FontWeight.Bold); Text(entry.source, style = MaterialTheme.typography.bodySmall) }
        Text(previous?.let { "체중 ${"%+.1f".format(entry.weight - it.weight)} kg" } ?: "첫 기록", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        InbodySummaryMetric("체중", entry.weight, "kg")
        InbodySummaryMetric("골격근량", entry.muscle, "kg")
        InbodySummaryMetric("체지방률", entry.fat, "%")
    }
    Row { TextButton(open) { Text("상세 결과 보기") }; TextButton(delete) { Text("삭제") } }
}
