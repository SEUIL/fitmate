package com.fitmate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.remote.FitMateApiClient

internal data class MateFilters(
    val sport: String = "전체", val difficulty: String = "전체", val status: String = "RECRUITING",
    val region: String = "", val regionLabel: String = "전체 지역", val date: String = "전체 기간",
)

@Composable internal fun mateButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)

@Composable internal fun MateHeader(title: String, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        action()
    }
}

@Composable internal fun MateChoices(options: List<String>, selected: String, change: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { label -> FilterChip(selected == label, { change(label) }, { Text(label) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) }
    }
}

@Composable internal fun MateSportTabs(selected: String, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("전체", "헬스", "러닝", "배드민턴", "클라이밍", "등산", "골프").forEach { label ->
            FilterChip(selected == label, { change(label) }, { Text(label) }, modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape)
        }
    }
}

@Composable internal fun MateTag(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun MateStatus(label: String, highlighted: Boolean = false) {
    Surface(shape = RoundedCornerShape(10.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun MateSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    WorkoutCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable internal fun MateOptionField(label: String, value: String, options: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Outlined.ExpandMore, null)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false }) }
        }
    }
}

@Composable internal fun MateInfo(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun MateHost(name: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(name.take(1).ifBlank { "M" }, fontWeight = FontWeight.SemiBold) }
        }
        Text(name.ifBlank { "메이트" }, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
    }
}

internal fun mateCost(cost: String): String = cost.toLongOrNull()?.let {
    if (it == 0L) "무료" else "1인 ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(it)}원"
} ?: "비용 미정"

@Composable internal fun MateListingCard(post: MatePost, status: String, open: () -> Unit) {
    OutlinedCard(open, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                MateTag(post.sport.label)
                MateStatus(status, post.serverStatus == "RECRUITING")
            }
            Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MateInfo(Icons.Outlined.CalendarToday, "${post.dateTime} · ${post.duration}")
            MateInfo(Icons.Outlined.Place, post.details.location)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MateTag(if (post.isBeginnerWelcome) "초보 환영" else post.difficulty)
                MateTag(mateCost(post.details.cost))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MateHost(post.authorName, Modifier.weight(1f))
                Text("${post.participantCount} / ${post.capacity}명 참여", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun MateMetric(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable internal fun MateDetailSummary(post: MatePost, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MateTag(post.sport.label); MateStatus(status, post.serverStatus == "RECRUITING") }
    Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    MateHost(post.authorName)
    WorkoutCard {
        if (LocalDensity.current.fontScale > 1.3f) {
            MateMetric("일정", post.dateTime, Icons.Outlined.CalendarToday, Modifier)
            MateMetric("예상 시간", post.duration, Icons.Outlined.Schedule, Modifier)
            MateMetric("참여 인원", "${post.participantCount} / ${post.capacity}명", Icons.Outlined.PeopleOutline, Modifier)
            MateMetric("예상 비용", mateCost(post.details.cost), Icons.Outlined.Payments, Modifier)
        } else {
            Row { MateMetric("일정", post.dateTime, Icons.Outlined.CalendarToday, Modifier.weight(1f)); MateMetric("예상 시간", post.duration, Icons.Outlined.Schedule, Modifier.weight(1f)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row { MateMetric("참여 인원", "${post.participantCount} / ${post.capacity}명", Icons.Outlined.PeopleOutline, Modifier.weight(1f)); MateMetric("예상 비용", mateCost(post.details.cost), Icons.Outlined.Payments, Modifier.weight(1f)) }
        }
    }
    MateSection("어디서 만나나요?") {
    MateInfo(Icons.Outlined.Place, post.details.location)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
            Text(if (post.details.meetingPoint.isNotBlank()) "집결 위치 · ${post.details.meetingPoint}" else "정확한 집결 위치는 참가 확정 후 공개돼요.", style = MaterialTheme.typography.bodySmall)
        }
    }
    }
    MateSection("어떤 운동을 하나요?") {
    Text(post.description, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MateTag(post.difficulty)
        if (post.isBeginnerWelcome) MateTag("초보 환영")
        MateTag(post.details.visibility)
    }
    }
    if (post.details.sportFields.values.any { it.isNotBlank() }) MateSection("운동 상세 정보") {
    post.details.sportFields.filterValues { it.isNotBlank() }.forEach { (name, value) ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    }
}

@Composable internal fun MateFilterPage(modifier: Modifier, api: FitMateApiClient, initial: MateFilters, back: () -> Unit, apply: (MateFilters) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    Column(modifier.fillMaxSize()) {
        Screen(Modifier.weight(1f), background = MaterialTheme.colorScheme.background) {
            MateHeader("필터", back) { TextButton({ draft = MateFilters(status = "전체") }) { Text("초기화") } }
            MateSection("언제 운동할까요?") {
            MateChoices(listOf("전체 기간", "오늘", "내일", "7일 이내"), draft.date) { draft = draft.copy(date = it) }
            }
            MateSection("어디서 운동할까요?") {
            KakaoRegionPicker(api, draft.regionLabel, compact = true) { id, label -> draft = draft.copy(region = id, regionLabel = label) }
            if (draft.region.isNotBlank()) TextButton({ draft = draft.copy(region = "", regionLabel = "전체 지역") }) { Text("전체 지역") }
            }
            MateSection("운동 종목") {
            MateChoices(listOf("전체", "헬스", "러닝", "배드민턴", "클라이밍", "등산", "골프"), sportFilterLabel(draft.sport)) { draft = draft.copy(sport = sportFilterCode(it)) }
            }
            MateSection("운동 수준") {
            MateChoices(listOf("전체", "초보", "중급", "상급"), difficultyFilterLabel(draft.difficulty)) { draft = draft.copy(difficulty = difficultyFilterCode(it)) }
            }
            MateSection("모집 상태") {
            MateChoices(listOf("전체", "모집 중", "정원 마감", "모집 마감"), postLabel(draft.status)) {
                draft = draft.copy(status = when(it) { "모집 중" -> "RECRUITING"; "정원 마감" -> "FULL"; "모집 마감" -> "CLOSED"; else -> "전체" })
            }
            }
        }
        MateActionBar { Button({ apply(draft) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp), colors = mateButtonColors()) { Text("선택한 조건 적용") } }
    }
}

@Composable internal fun MateActionBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
