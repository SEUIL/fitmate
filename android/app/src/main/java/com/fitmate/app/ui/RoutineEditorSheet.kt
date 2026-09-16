package com.fitmate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.remote.ApiExercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun RoutineEditorSheet(
    editing: Boolean, name: String, onName: (String) -> Unit,
    exercises: List<ApiExercise>, selected: List<String>, onSelection: (List<String>) -> Unit,
    originalNames: Map<String, String>, busy: Boolean, error: String?,
    dismiss: () -> Unit, detail: (String) -> Unit, save: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("전체") }
    val categories = listOf("전체") + exercises.map { exerciseBodyLabel(it.bodyPart) }.distinct()
    val filtered = exercises.filter {
        (category == "전체" || exerciseBodyLabel(it.bodyPart) == category) &&
            it.name.replace(" ", "").contains(query.replace(" ", ""), ignoreCase = true)
    }
    fun toggle(id: String) { onSelection(if (id in selected) selected - id else selected + id) }
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "루틴 편집" else "새 루틴 만들기", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(dismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "루틴 편집 닫기") }
            }
            // Keep the save action visible while the catalogue scrolls, including with the IME open.
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("routine-exercise-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    OutlinedTextField(name, onName, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                        label = { Text("루틴 이름") }, placeholder = { Text("예: 월요일 상체 루틴") }, shape = RoundedCornerShape(12.dp))
                }
                item { Text("선택한 운동 ${selected.size}개", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium) }
                if (selected.isEmpty()) item { Text("아래에서 운동을 골라주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(selected, key = { index, id -> "selected-$index-$id" }) { index, id ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. ${exercises.firstOrNull { it.id == id }?.name ?: originalNames[id].orEmpty()}",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton({ onSelection(selected.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = !busy && index > 0) { Text("위로") }
                            IconButton({ onSelection(selected.filterIndexed { i, _ -> i != index }) }, enabled = !busy) {
                                Icon(Icons.Outlined.Close, "선택한 운동 ${index + 1} 제거")
                            }
                        }
                    }
                }
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("운동 이름 검색") }, shape = RoundedCornerShape(12.dp)) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { body -> FilterChip(category == body, { category = body }, label = { Text(body, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(20.dp), colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) }
                } }
                item { Text("추가할 운동 · ${filtered.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (filtered.isEmpty()) item { Text("검색 결과가 없어요. 다른 이름이나 부위를 찾아보세요.", Modifier.padding(vertical = 24.dp)) }
                items(filtered, key = { it.id }) { exercise ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(enabled = !busy) { detail(exercise.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExerciseThumbnail(exercise.id, exercise.name)
                            Column {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                Text("${exerciseBodyLabel(exercise.bodyPart)} · ${exerciseEquipmentLabel(exercise.equipmentType)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconToggleButton(exercise.id in selected, { toggle(exercise.id) }, enabled = !busy) {
                            Icon(if (exercise.id in selected) Icons.Outlined.Check else Icons.Outlined.Add,
                                "${exercise.name} ${if (exercise.id in selected) "선택 해제" else "추가"}", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item { Text("새 운동은 3세트로 추가해요. 기존 운동의 목표 무게·횟수·휴식은 유지돼요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    error?.let { ErrorText(it) }
                    Button(save, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !busy && name.isNotBlank() && selected.isNotEmpty(), shape = RoundedCornerShape(12.dp)) {
                        Text(if (busy) "저장 중…" else if (selected.isEmpty()) "운동을 선택해주세요" else "${selected.size}개 운동으로 ${if (editing) "루틴 저장" else "루틴 만들기"}")
                    }
                }
            }
        }
    }
}
