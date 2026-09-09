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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.remote.FitMateApiClient

internal data class MateFilters(
    val sport: String = "전체", val difficulty: String = "전체", val status: String = "RECRUITING",
    val region: String = "", val regionLabel: String = "전체 지역", val date: String = "전체 기간",
)

@Composable internal fun mateButtonColors() = ButtonDefaults.buttonColors(containerColor = Color(0xFF527D6D), contentColor = Color.White)

@Composable internal fun MateHeader(title: String, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로가기") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        action()
    }
}

@Composable internal fun MateChoices(options: List<String>, selected: String, change: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { label -> FilterChip(selected == label, { change(label) }, { Text(label) }, shape = RoundedCornerShape(12.dp)) }
    }
}

@Composable internal fun MateSportTabs(selected: String, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("전체", "헬스", "러닝", "배드민턴", "클라이밍", "등산", "골프").forEach { label ->
            FilterChip(selected == label, { change(label) }, { Text(label) }, shape = CircleShape)
        }
    }
}

@Composable internal fun MateTag(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun MateInfo(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun MateHost(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text(name.take(1).ifBlank { "M" }, fontWeight = FontWeight.SemiBold) }
        }
        Text(name.ifBlank { "메이트" }, style = MaterialTheme.typography.labelLarge)
    }
}

internal fun mateCost(cost: String): String = cost.toLongOrNull()?.let {
    if (it == 0L) "무료" else "1인 ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(it)}원"
} ?: "비용 미정"

@Composable internal fun MateListingCard(post: MatePost, status: String, open: () -> Unit) {
    OutlinedCard(open, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.sport.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                MateHost(post.authorName)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MateTag(post.sport.label); Text(status, color = MaterialTheme.colorScheme.primary) }
    Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    MateHost(post.authorName)
    OutlinedCard(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row { MateMetric("일정", post.dateTime, Icons.Outlined.CalendarToday, Modifier.weight(1f)); MateMetric("예상 시간", post.duration, Icons.Outlined.Schedule, Modifier.weight(1f)) }
            HorizontalDivider()
            Row { MateMetric("참여 인원", "${post.participantCount} / ${post.capacity}명", Icons.Outlined.PeopleOutline, Modifier.weight(1f)); MateMetric("예상 비용", mateCost(post.details.cost), Icons.Outlined.Payments, Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("어디서 만나나요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    MateInfo(Icons.Outlined.Place, post.details.location)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
            Text(if (post.details.meetingPoint.isNotBlank()) "집결 위치 · ${post.details.meetingPoint}" else "정확한 집결 위치는 참가 확정 후 공개돼요.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("어떤 운동을 하나요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(post.description, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MateTag(post.difficulty)
        if (post.isBeginnerWelcome) MateTag("초보 환영")
        MateTag(post.details.visibility)
    }
    post.details.sportFields.filterValues { it.isNotBlank() }.forEach { (name, value) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun MateFilterPage(modifier: Modifier, api: FitMateApiClient, initial: MateFilters, back: () -> Unit, apply: (MateFilters) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    Column(modifier.fillMaxSize()) {
        Screen(Modifier.weight(1f), background = MaterialTheme.colorScheme.background) {
            MateHeader("필터", back) { TextButton({ draft = MateFilters(status = "전체") }) { Text("초기화") } }
            Text("언제 운동할까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MateChoices(listOf("전체 기간", "오늘", "내일", "7일 이내"), draft.date) { draft = draft.copy(date = it) }
            Text("어디서 운동할까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            KakaoRegionPicker(api, draft.regionLabel, compact = true) { id, label -> draft = draft.copy(region = id, regionLabel = label) }
            if (draft.region.isNotBlank()) TextButton({ draft = draft.copy(region = "", regionLabel = "전체 지역") }) { Text("전체 지역") }
            Text("운동 종목", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MateChoices(listOf("전체", "헬스", "러닝", "배드민턴", "클라이밍", "등산", "골프"), sportFilterLabel(draft.sport)) { draft = draft.copy(sport = sportFilterCode(it)) }
            Text("운동 수준", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MateChoices(listOf("전체", "초보", "중급", "상급"), difficultyFilterLabel(draft.difficulty)) { draft = draft.copy(difficulty = difficultyFilterCode(it)) }
            Text("모집 상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MateChoices(listOf("전체", "모집 중", "정원 마감", "모집 마감"), postLabel(draft.status)) {
                draft = draft.copy(status = when(it) { "모집 중" -> "RECRUITING"; "정원 마감" -> "FULL"; "모집 마감" -> "CLOSED"; else -> "전체" })
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
