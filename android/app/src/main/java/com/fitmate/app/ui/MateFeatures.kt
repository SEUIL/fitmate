package com.fitmate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitmate.app.data.model.*

internal fun mateFields(sport: Sport): Map<String, List<String>> = when (sport) {
    Sport.FITNESS -> linkedMapOf("운동 형태" to listOf("웨이트", "유산소", "혼합", "스트레칭/회복"), "운동 부위" to listOf("전신", "가슴", "등", "어깨", "하체", "팔", "코어"), "진행 방식" to listOf("같은 루틴", "각자 루틴", "추천받기", "초보자 안내"), "루틴 상태" to listOf("준비 완료", "함께 정하기"), "주 사용 기구" to listOf("머신", "프리웨이트", "케이블", "유산소 기구"), "보조 여부" to listOf("없음", "자세 피드백", "고중량 보조"), "헬스장 이용 조건" to emptyList())
    Sport.RUNNING -> linkedMapOf("러닝 유형" to listOf("자유", "거리 목표", "시간 목표", "인터벌"), "목표 거리 (km)" to emptyList(), "목표 페이스" to listOf("5분/km", "6분/km", "7분/km", "자유"), "진행 방식" to listOf("함께 달리기", "각자 달리기", "초보자 맞춤"), "코스" to listOf("공원", "하천", "트랙", "도심", "트레일"), "GPS 기록" to listOf("사용", "미사용"), "우천 시" to listOf("취소", "연기", "협의"))
    Sport.BADMINTON -> linkedMapOf("경기 형태" to listOf("단식", "복식"), "목적" to listOf("랠리", "게임", "기본기", "가벼운 운동"), "셔틀콕" to listOf("깃털", "나일론"), "장비" to listOf("개인 지참", "대여 가능"), "예약" to listOf("예약 완료", "예약 예정"), "정산" to listOf("각자 결제", "균등 분담", "작성자 부담"))
    Sport.CLIMBING -> linkedMapOf("형태" to listOf("볼더링", "리드", "탑로프"), "목적" to listOf("자유 클라이밍", "난이도 도전", "기술 연습", "초보 체험"), "희망 난이도" to emptyList(), "장비" to listOf("개인 지참", "대여"), "빌레이" to listOf("해당 없음", "가능", "도움 필요"), "진행 방식" to listOf("함께", "각자"), "입장" to listOf("회원권", "일일권", "동반 입장"))
    Sport.HIKING -> linkedMapOf("형태" to listOf("등산", "트레킹", "둘레길", "야간 산행"), "코스명" to emptyList(), "목적" to listOf("정상", "산책", "빠른 산행", "사진/휴식"), "이동" to listOf("개별 이동", "대중교통", "협의"), "준비물" to emptyList(), "날씨 악화 시" to listOf("취소", "연기", "협의"))
    Sport.GOLF -> linkedMapOf("장소 형태" to listOf("스크린", "실내 연습장", "실외 연습장", "필드"), "목적" to listOf("게임", "스윙", "레슨 복습", "가벼운 연습"), "평균 타수/핸디캡" to emptyList(), "장비" to listOf("개인 지참", "대여"), "예약" to listOf("예약 완료", "예약 예정"), "정산" to listOf("각자 결제", "균등 분담"), "홀" to listOf("해당 없음", "9홀", "18홀"), "이동" to listOf("개별 이동", "협의"))
}

@Composable internal fun MateDetailsEditor(sport: Sport, details: MateDetails, update: (MateDetails) -> Unit) {
    SoftCard {
        Text("장소·비용·공개 범위", fontWeight = FontWeight.Bold)
        OutlinedTextField(details.location, { update(details.copy(location = it)) }, label = { Text("공개 장소 (예: 성수역 인근)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(details.meetingPoint, { update(details.copy(meetingPoint = it)) }, label = { Text("확정자에게 공개할 집결 위치") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(details.cost, { update(details.copy(cost = it)) }, label = { Text("1인 예상 비용 (원, 무료는 0)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Choices(listOf("전체 공개", "맞팔 공개"), details.visibility) { update(details.copy(visibility = it)) }
    }
    SoftCard {
        Text("${sport.label} 상세 조건", fontWeight = FontWeight.Bold)
        mateFields(sport).forEach { (label, options) ->
            val value = details.sportFields[label] ?: options.firstOrNull().orEmpty()
            if (options.isEmpty()) OutlinedTextField(value, { update(details.copy(sportFields = details.sportFields + (label to it))) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
            else { Text(label); Choices(options, value) { update(details.copy(sportFields = details.sportFields + (label to it))) } }
        }
    }
}

internal data class MateApplicant(val id: Int, val name: String, val status: String = "신청 대기")
internal data class MateEvaluation(val scores: List<Int>, val privateNote: String)
internal class MateRoom {
    var myStatus by mutableStateOf("미신청")
    var applicants by mutableStateOf(emptyList<MateApplicant>())
    var completed by mutableStateOf(false)
    var canceled by mutableStateOf(false)
    var closed by mutableStateOf(false)
    var block by mutableStateOf(false)
    var evaluated by mutableStateOf(emptySet<String>())
    var evaluations by mutableStateOf(emptyMap<String, MateEvaluation>())
    var notice by mutableStateOf<String?>(null)
    var revision by mutableStateOf(0)
    fun respond(applicantId: Int, accept: Boolean, post: MatePost) {
        if (canceled || completed) return
        if (accept && (closed || post.participantCount + applicants.count { it.status == "참가 확정" } >= post.capacity)) {
            notice = "정원이 가득 차 더 수락할 수 없어요."
            return
        }
        applicants = applicants.map {
            if (it.id == applicantId && it.status == "신청 대기") it.copy(status = if (accept) "참가 확정" else "거절") else it
        }
    }
}
internal class MateFeatureState {
    internal val rooms = mutableStateMapOf<Long, MateRoom>()
    var activeWorkoutId by mutableStateOf<Long?>(null)
    fun room(id: Long) = rooms.getOrPut(id) { MateRoom() }
    fun completeWorkout() { activeWorkoutId?.let { room(it).completed = true }; activeWorkoutId = null }
}

@Composable internal fun MateDetailFlow(modifier: Modifier, post: MatePost, nickname: String, room: MateRoom, blocked: Boolean, start: () -> Unit, dm: (String) -> Unit, onBlock: (Boolean) -> Unit) = with(room) {
    LaunchedEffect(blocked) { block = blocked }
    val owner = post.authorName == nickname
    val confirmed = myStatus == "참가 확정"
    val count = post.participantCount + applicants.count { it.status == "참가 확정" } + if (confirmed) 1 else 0
    val full = count >= post.capacity
    var confirm by remember { mutableStateOf<String?>(null) }
    var evaluating by remember { mutableStateOf<String?>(null) }
    var ratings by remember { mutableStateOf(List(4) { "5" }) }
    var privateNote by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("노쇼") }
    var reporting by remember { mutableStateOf(false) }
    confirm?.let { action -> ConfirmAction(action, "계속 진행할까요?", { confirm = null }) {
        when (action) {
            "모임 취소" -> { canceled = true; notice = "모임이 취소되었습니다. 확정자 알림은 서버 연동 후 제공됩니다." }
            "차단" -> { block = true; onBlock(true); notice = "차단되어 참가와 DM을 이용할 수 없어요." }
            "참가 취소" -> myStatus = "미신청"
        }
        confirm = null
    } }
    Screen(modifier) {
        SectionTitle(post.title, "${post.sport.label} · ${post.dateTime} · ${post.duration}")
        Text("${if (canceled) "취소" else if (completed) "운동 완료" else if (full || closed) "모집 마감" else "모집 중"} · $count/${post.capacity}명", fontWeight = FontWeight.Bold)
        SoftCard {
            Text(post.description)
            Text("공개 장소 · ${post.details.location.ifBlank { post.region }}")
            Text("1인 예상 비용 · ${post.details.cost}원 · ${post.details.visibility}")
            Text("${post.difficulty}${if (post.isBeginnerWelcome) " · 초보 환영" else ""}")
            post.details.sportFields.filterValues { it.isNotBlank() }.forEach { (label, value) -> Text("$label · $value") }
            if ((owner || confirmed) && !block && !canceled) Text("집결 위치 · ${post.details.meetingPoint.ifBlank { "작성자가 아직 등록하지 않았어요." }}")
            else Text("정확한 집결 위치는 참가 확정 후 확인할 수 있어요.")
        }
        notice?.let { Text(it) }
        when {
            block -> OutlinedButton({ block = false; onBlock(false); notice = "차단을 해제했어요." }) { Text("차단 해제") }
            canceled -> Text("취소된 모임에는 참가할 수 없습니다.")
            owner -> {
                SoftCard {
                    Text("작성자 · 참가 신청 관리", fontWeight = FontWeight.Bold)
                    if (applicants.none { it.status == "신청 대기" }) Text("대기 중인 신청이 없습니다.")
                    applicants.forEach { applicant ->
                        Text("${applicant.name} · ${applicant.status}")
                        if (applicant.status == "신청 대기") Row {
                            TextButton({ respond(applicant.id, true, post) }, enabled = !full && !closed && !completed) { Text("수락") }
                            TextButton({ respond(applicant.id, false, post) }) { Text("거절") }
                        }
                    }
                    if (!completed) {
                        OutlinedButton({ closed = !closed }) { Text(if (closed) "모집 재개" else "모집 마감") }
                        TextButton({ confirm = "모임 취소" }) { Text("모임 취소") }
                    }
                    DebugOnly {
                        TextButton({
                            val id = (applicants.maxOfOrNull { it.id } ?: 0) + 1
                            applicants = applicants + MateApplicant(id, "테스트 메이트 $id")
                        }, enabled = !completed && !closed && !full) { Text("테스트: 신청 1건 받기") }
                    }
                }
            }
            confirmed -> {
                Text("참가 확정 · 마감된 모임도 확정자는 이용할 수 있어요.")
                OutlinedButton({ dm(post.authorName) }) { Text("작성자와 DM") }
                if (!completed) TextButton({ confirm = "참가 취소" }) { Text("참가 취소") }
            }
            myStatus == "신청 대기" -> {
                Text("신청 대기 중")
                OutlinedButton({ myStatus = "미신청" }) { Text("신청 취소") }
                DebugOnly {
                    Text("테스트: 작성자 응답")
                    Row {
                        TextButton({ myStatus = "참가 확정" }, enabled = !full && !closed && !completed) { Text("수락 받기") }
                        TextButton({ myStatus = "거절" }) { Text("거절 받기") }
                    }
                }
            }
            full || closed || completed -> Text("모집이 마감되었습니다.")
            myStatus == "거절" -> Text("신청이 거절되었습니다. 다른 모임을 찾아보세요.")
            else -> PrimaryAction("참가 신청") { myStatus = "신청 대기" }
        }
        if (!block && !canceled && (owner || confirmed)) {
            SoftCard {
                Text("확정 참가자", fontWeight = FontWeight.Bold)
                Text("${post.authorName} · 작성자")
                if (confirmed) Text("$nickname · 나")
                applicants.filter { it.status == "참가 확정" }.forEach { applicant ->
                    Row { Text(applicant.name, Modifier.weight(1f)); TextButton({ dm(applicant.name) }) { Text("DM") } }
                }
                if (post.participantCount > 1) Text("기존 참가자 ${post.participantCount - 1}명 · 상세 명단 수신 대기")
            }
            if (!completed) {
                if (post.sport in listOf(Sport.FITNESS, Sport.RUNNING)) PrimaryAction("내 운동 시작") { start() }
                else Text("현재 운동 기록은 헬스·러닝을 지원합니다.")
                DebugOnly { TextButton({ completed = true }) { Text("테스트: 운동 완료 상태") } }
            } else {
                Text("운동 후 메이트 평가", fontWeight = FontWeight.Bold)
                val targets = if (owner) applicants.filter { it.status == "참가 확정" }.map { it.name } else listOf(post.authorName)
                if (targets.isEmpty()) Text("평가할 확정 참가자가 없습니다.")
                targets.forEach { name -> OutlinedButton({ evaluating = name; ratings = List(4) { "5" }; privateNote = "" }, enabled = name !in evaluated) { Text(if (name in evaluated) "$name 평가 완료" else "$name 평가") } }
                evaluating?.let { name -> SoftCard {
                    Text("$name 평가")
                    listOf("시간 약속", "매너", "게시물 내용 일치", "다시 함께할 의향").forEachIndexed { index, label ->
                        Text(label); Choices((1..5).map(Int::toString), ratings[index]) { value -> ratings = ratings.mapIndexed { i, old -> if (i == index) value else old } }
                    }
                    OutlinedTextField(privateNote, { privateNote = it.take(500) }, label = { Text("운영 검토용 비공개 메모") }, modifier = Modifier.fillMaxWidth())
                    PrimaryAction("평가 저장") {
                        evaluations = evaluations + (name to MateEvaluation(ratings.map(String::toInt), privateNote))
                        evaluated = evaluated + name; evaluating = null; notice = "평가를 저장했어요. (앱 실행 중 보관)"
                    }
                } }
            }
        }
        if (!owner) {
            Row {
                OutlinedButton({ reporting = !reporting }) { Text("신고") }
                TextButton({ confirm = "차단" }) { Text("차단") }
            }
            if (reporting) SoftCard {
                Choices(listOf("노쇼", "괴롭힘", "허위 게시물"), reportReason) { reportReason = it }
                PrimaryAction("신고 내용 확인") { notice = "$reportReason 신고를 임시 접수했어요. 실제 전송은 서버 연결 후 가능합니다."; reporting = false }
            }
        }
    }
}

@Composable internal fun MateListFlow(modifier: Modifier, posts: List<MatePost>, rooms: MateFeatureState, open: (MatePost) -> Unit, write: () -> Unit) = Screen(modifier) {
    var sport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("전체") }
    SocialTitle("같이 운동하기", Icons.Outlined.AddBox, "메이트 모집 글 작성", write)
    Choices(listOf("전체") + Sport.entries.map { it.label }, sport) { sport = it }
    val visible = posts.filter { sport == "전체" || it.sport.label == sport }
    if (visible.isEmpty()) SoftCard { Text("아직 모집 글이 없어요. 첫 모임을 만들어 보세요.") }
    visible.forEach { post ->
        val room = rooms.room(post.id)
        val count = post.participantCount + room.applicants.count { it.status == "참가 확정" } + if (room.myStatus == "참가 확정") 1 else 0
        Column(Modifier.fillMaxWidth().clickable { open(post) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, fontWeight = FontWeight.Bold)
            Text("${post.sport.label} · ${post.dateTime} · ${post.duration}", style = MaterialTheme.typography.bodySmall)
            Text(post.details.location.ifBlank { post.region }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count/${post.capacity}명 · ${when { room.canceled -> "취소"; room.completed -> "완료"; room.closed || count >= post.capacity -> "마감"; else -> "모집 중" }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
