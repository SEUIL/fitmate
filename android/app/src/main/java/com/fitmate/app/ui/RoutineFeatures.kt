package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.fitmate.app.data.model.WorkoutSummary
import com.fitmate.app.data.model.WorkoutSet
import kotlinx.coroutines.launch

@Composable internal fun WorkoutAction(label: String, action: () -> Unit) {
    Button(action, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Text(label) }
}

internal val PreviewWorkouts = listOf(
    WorkoutSummary(48, listOf(WorkoutSet("스쿼트", 50.0, 10, 7, "안정적으로 마무리"), WorkoutSet("스쿼트", 50.0, 10, 8), WorkoutSet("랫풀다운", 35.0, 12, 7)), false, 3),
    WorkoutSummary(42, listOf(WorkoutSet("스쿼트", 45.0, 10, 7), WorkoutSet("스쿼트", 45.0, 8, 8), WorkoutSet("랫풀다운", 30.0, 12, 7)), false, 3)
)

@Composable internal fun WorkoutPreviewCards() {
    var selected by remember { mutableStateOf<Int?>(null) }
    var runDetails by remember { mutableStateOf(false) }
    Text("디자인 확인용 예시 · 실제 운동 기록/통계에 합산하지 않습니다.", style = MaterialTheme.typography.bodySmall)
    PreviewWorkouts.forEachIndexed { index, item -> SoftCard {
        Text(if (index == 0) "전신 웨이트 · 예시" else "이전 전신 웨이트 · 예시", fontWeight = FontWeight.SemiBold)
        Text("${item.durationMinutes}분  ·  ${item.sets.size}세트  ·  ${item.totalVolumeKg.toInt()}kg")
        TextButton({ selected = index }) { Text("예시 상세·비교") }
    } }
    SoftCard {
        Text("저녁 러닝 · 예시", fontWeight = FontWeight.SemiBold)
        Text("5.00km  ·  30분  ·  6:00/km")
        TextButton({ runDetails = true }) { Text("러닝 예시 상세") }
    }
    if (runDetails) SocialSheet({ runDetails = false }) {
        Text("러닝 리포트 · 예시", style = MaterialTheme.typography.titleLarge)
        Text("거리 목표 5km 달성 · 총 30분 · 평균 6:00/km")
        listOf("6:10", "6:05", "6:00", "5:55", "5:50").forEachIndexed { i, pace -> Text("${i + 1}km  ·  $pace/km") }
        Text("RPE 6 · 컨디션 4/5 · 무리하지 않고 일정하게 달렸어요.")
        Text("디자인 확인용 고정 데이터이며 GPS·실제 운동 기록과 무관합니다.", style = MaterialTheme.typography.bodySmall)
    }
    selected?.let { index -> SocialSheet({ selected = null }) {
        val item = PreviewWorkouts[index]
        Text("운동 리포트 · 예시", style = MaterialTheme.typography.titleLarge)
        Text("${item.durationMinutes}분 · ${item.sets.size}세트 · 목표 ${item.targetSetCount}세트 달성")
        item.sets.forEach { Text("${it.exerciseName} · ${it.weightKg}kg × ${it.repetitions}회 · RPE ${it.rpe}"); if (it.memo.isNotBlank()) Text(it.memo) }
        PreviewWorkouts.getOrNull(index + 1)?.let { prior -> Text("이전 예시 대비 총 볼륨 +${(item.totalVolumeKg - prior.totalVolumeKg).toInt()}kg") }
        Text("실제 기록은 변경되지 않습니다.", style = MaterialTheme.typography.bodySmall)
    } }
}

internal data class ExerciseGuide(val name: String, val body: String, val description: String, val alternative: String)
internal val ExerciseGuides = listOf(
    ExerciseGuide("스쿼트", "하체", "발을 안정적으로 지지하고 무릎과 발끝 방향을 맞춰 앉았다 일어납니다. 불편하면 범위를 줄이거나 중단하세요.", "레그 프레스"),
    ExerciseGuide("레그 프레스", "하체", "등과 골반을 패드에 지지하고 발판을 천천히 밀고 돌아옵니다. 사용 기구의 안내를 먼저 확인하세요.", "스쿼트"),
    ExerciseGuide("랫풀다운", "등", "상체를 안정적으로 유지하고 바를 몸 앞쪽으로 당겼다가 천천히 돌려보냅니다.", "시티드 로우"),
    ExerciseGuide("시티드 로우", "등", "몸통 반동을 줄이고 손잡이를 몸 쪽으로 당겼다가 천천히 돌려보냅니다.", "랫풀다운"),
    ExerciseGuide("체스트 프레스", "가슴", "등을 패드에 지지하고 손잡이를 앞으로 밀었다가 천천히 돌아옵니다.", "푸시업"),
    ExerciseGuide("푸시업", "가슴", "몸통을 안정적으로 유지하며 팔을 굽혔다 폅니다. 벽이나 높은 지지대를 활용해 난도를 조절할 수 있습니다.", "체스트 프레스"),
    ExerciseGuide("숄더 프레스", "어깨", "허리가 과하게 꺾이지 않도록 몸통을 지지하고 손잡이를 위로 밀었다가 돌아옵니다.", "레터럴 레이즈"),
    ExerciseGuide("레그 컬", "하체", "기구의 회전축과 무릎 위치를 맞추고 무릎을 굽혔다가 천천히 돌아옵니다.", "레그 프레스"),
    ExerciseGuide("케이블 컬", "팔", "팔꿈치 위치를 안정적으로 유지하며 손잡이를 당겼다가 천천히 돌아옵니다.", "덤벨 컬")
)
internal val ExampleRoutines = listOf(
    SavedRoutine("전신 기본", listOf("레그 프레스", "랫풀다운", "체스트 프레스", "숄더 프레스", "레그 컬", "케이블 컬")),
    SavedRoutine("2분할 · 상체", listOf("체스트 프레스", "시티드 로우", "숄더 프레스", "케이블 컬")),
    SavedRoutine("2분할 · 하체", listOf("스쿼트", "레그 프레스", "레그 컬")),
    SavedRoutine("3분할 · 등/이두", listOf("랫풀다운", "시티드 로우", "케이블 컬"))
)

@Composable internal fun RoutineLibrary(
    modifier: Modifier,
    saved: List<SavedRoutine>,
    update: (List<SavedRoutine>) -> Unit,
    exerciseCatalog: ExerciseCatalog,
    select: (SavedRoutine) -> Unit
) = Screen(modifier) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("기본 루틴") }
    var editing by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var exercises by remember { mutableStateOf(emptyList<String>()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    SocialTabs(listOf("기본 루틴", "저장 루틴"), tab) { tab = it }
    Text("기본 루틴은 UI 검증용 예시이며 개인 맞춤 AI 추천이 아닙니다. 운동 구성은 자유롭게 수정할 수 있어요.", style = MaterialTheme.typography.bodySmall)
    when (val catalog = exerciseCatalog.state) {
        ExerciseCatalogState.Loading -> Text("서버 운동 목록을 불러오는 중입니다.", style = MaterialTheme.typography.bodySmall)
        is ExerciseCatalogState.Ready -> Text("서버 운동 목록 ${catalog.exercises.size}개를 사용 중입니다.", style = MaterialTheme.typography.bodySmall)
        is ExerciseCatalogState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("서버 목록을 불러오지 못해 기본 목록을 표시합니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton({ scope.launch { exerciseCatalog.refresh() } }) { Text("다시 시도") }
        }
    }
    val rows = if (tab == "기본 루틴") ExampleRoutines else saved
    if (rows.isEmpty()) Text("저장한 루틴이 없어요. 직접 구성해 보세요.")
    rows.forEach { routine -> SoftCard {
        Text(routine.name, fontWeight = FontWeight.Bold)
        Text(routine.exercises.joinToString(" → "))
        WorkoutAction("이 루틴 선택") { select(routine) }
        TextButton({ original = if (tab == "저장 루틴") routine.name else null; name = routine.name; exercises = routine.exercises; error = null; editing = true }) { Text(if (tab == "저장 루틴") "편집" else "수정해서 저장") }
        if (tab == "저장 루틴") TextButton({ deleting = routine.name }) { Text("루틴 삭제") }
    } }
    WorkoutAction("새 루틴 만들기") { original = null; name = ""; exercises = emptyList(); error = null; editing = true }
    OutlinedButton({ select(SavedRoutine("직접 구성", emptyList())) }, Modifier.fillMaxWidth()) { Text("저장 없이 직접 운동 구성") }
    deleting?.let { target -> ConfirmAction("루틴 삭제", "저장한 루틴을 삭제할까요? 완료한 운동 기록은 유지됩니다.", { deleting = null }) { update(saved.filterNot { it.name == target }); deleting = null } }
    if (editing) SocialSheet({ editing = false }) {
        Text("루틴 구성", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("루틴 이름") })
        exercises.forEachIndexed { index, exercise -> Row {
            Text("${index + 1}. $exercise", Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton({ exercises = exercises.toMutableList().apply { add(index - 1, removeAt(index)) } }, enabled = index > 0) { Text("↑") }
            TextButton({ exercises = exercises.toMutableList().apply { add(index + 1, removeAt(index)) } }, enabled = index < exercises.lastIndex) { Text("↓") }
            TextButton({ exercises = exercises.filterIndexed { i, _ -> i != index } }) { Text("삭제") }
        } }
        val serverExerciseNames = (exerciseCatalog.state as? ExerciseCatalogState.Ready)
            ?.exercises
            ?.map { it.name }
            ?.distinct()
            .orEmpty()
        Choices(if (serverExerciseNames.isEmpty()) ExerciseGuides.map { it.name } else serverExerciseNames, "") { if (it !in exercises) exercises = exercises + it }
        OutlinedTextField(draft, { draft = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("직접 운동명 입력") })
        TextButton({ if (draft.isNotBlank() && draft.trim() !in exercises) { exercises = exercises + draft.trim(); draft = "" } }) { Text("운동 추가") }
        ErrorText(error)
        PrimaryAction("루틴 저장") {
            error = when { name.isBlank() -> "루틴 이름을 입력하세요."; exercises.isEmpty() -> "운동을 한 개 이상 추가하세요."; saved.any { it.name == name.trim() && it.name != original } -> "같은 이름의 루틴이 있어요."; else -> null }
            if (error == null) { update(saved.filterNot { it.name == original } + SavedRoutine(name.trim(), exercises)); tab = "저장 루틴"; editing = false }
        }
    }
}

@Composable internal fun ExerciseHelp(session: WorkoutSession, history: List<WorkoutSummary>, saveRoutine: (SavedRoutine) -> Boolean) {
    var help by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Row {
        TextButton({ help = true }) { Text("운동 설명·대체") }
        TextButton({ saving = true }) { Text("현재 구성을 루틴 저장") }
    }
    val previous = history.firstNotNullOfOrNull { summary -> summary.sets.lastOrNull { it.exerciseName == session.exerciseName } }
    TextButton({ previous?.let {
        session.weightText = "%.2f".format(java.util.Locale.US, if (session.unit == "lb") it.weightKg * 2.2046226218 else it.weightKg)
        session.repetitionText = it.repetitions.toString(); session.rpeText = it.rpe?.toString().orEmpty()
        session.memo = it.memo; message = "이전 동일 운동의 마지막 세트를 불러왔어요. 기록 버튼을 눌러야 추가됩니다."
    } }, enabled = previous != null) { Text(if (previous == null) "이전 동일 운동 기록 없음" else "이전 동일 운동 기록 불러오기") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (help) SocialSheet({ help = false }) {
        val guide = ExerciseGuides.firstOrNull { it.name == session.exerciseName }
        Text(session.exerciseName.ifBlank { "운동을 먼저 추가하세요" }, style = MaterialTheme.typography.titleLarge)
        Text(guide?.let { "${it.body} · ${it.description}" } ?: "등록된 설명이 없는 직접 입력 운동입니다. 기구 안내와 전문가의 지도를 확인하세요.")
        Text("불편함이나 통증이 있으면 중단하세요. 예시 대체 운동이 모든 사용자에게 적합한 것은 아닙니다.")
        guide?.let { g -> OutlinedButton({
            val i = session.selectedExerciseIndex
            if (i in session.sessionExercises.indices) { session.sessionExercises[i] = g.alternative; session.exerciseName = g.alternative }
            help = false
        }) { Text("${g.alternative}(으)로 대체") } }
    }
    if (saving) SocialSheet({ saving = false }) {
        Text("운동 구성을 저장해 다음에 다시 사용하세요.")
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("루틴 이름") })
        PrimaryAction("구성 저장") {
            if (name.isBlank() || session.sessionExercises.isEmpty()) message = "이름과 운동 구성을 확인하세요."
            else if (saveRoutine(SavedRoutine(name.trim(), session.sessionExercises.toList()))) { saving = false; message = "루틴을 기기에 저장했어요." }
            else message = "같은 이름의 루틴이 있어요. 다른 이름을 입력하세요."
        }
        message?.let { Text(it) }
    }
}

@Composable internal fun WorkoutHistoryFlow(modifier: Modifier, history: List<WorkoutSummary>) = Screen(modifier) {
    var selected by remember { mutableStateOf<Int?>(null) }
    if (history.isEmpty()) Text("완료한 헬스 기록이 없습니다.")
    history.forEachIndexed { i, summary -> SoftCard {
        Text("기록 ${history.size - i} · ${summary.durationMinutes}분 · ${summary.sets.size}세트")
        Text(summary.sets.map { it.exerciseName }.distinct().joinToString())
        TextButton({ selected = i }) { Text("상세·이전 동일 운동 비교") }
    } }
    selected?.let { index -> history.getOrNull(index)?.let { summary -> SocialSheet({ selected = null }) {
        Text("운동 상세", style = MaterialTheme.typography.titleLarge)
        summary.sets.groupBy { it.exerciseName }.forEach { (name, sets) ->
            Text(name, fontWeight = FontWeight.Bold)
            sets.forEachIndexed { i, set -> Text("${i + 1}세트 · ${set.weightKg} kg × ${set.repetitions}회 · RPE ${set.rpe ?: "—"}${if (set.memo.isBlank()) "" else " · ${set.memo}"}") }
            val prior = history.drop(index + 1).firstOrNull { old -> old.sets.any { it.exerciseName == name } }?.sets?.filter { it.exerciseName == name }
            if (prior == null) Text("이전 동일 운동 기록 없음") else {
                val delta = sets.sumOf { it.weightKg * it.repetitions } - prior.sumOf { it.weightKg * it.repetitions }
                Text("이전 동일 운동 대비 총 볼륨 ${"%+.1f".format(delta)} kg · 반복 ${sets.sumOf { it.repetitions } - prior.sumOf { it.repetitions }}회")
            }
        }
    } } }
}
