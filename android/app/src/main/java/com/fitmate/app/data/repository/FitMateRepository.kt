package com.fitmate.app.data.repository

import com.fitmate.app.data.model.MatePost
import com.fitmate.app.data.model.CommunityPost
import com.fitmate.app.data.model.DirectMessage
import com.fitmate.app.data.model.DmConversation
import com.fitmate.app.data.model.Sport
import com.fitmate.app.data.model.UserProfile

interface FitMateRepository {
    fun initialProfile(): UserProfile
    fun matePosts(): List<MatePost>
    fun communityPosts(): List<CommunityPost>
    fun dmConversations(): List<DmConversation>
}

object MockFitMateRepository : FitMateRepository {
    override fun initialProfile() = UserProfile(
        nickname = "세일",
        region = "성수동",
        interests = setOf(Sport.FITNESS, Sport.RUNNING),
        isProfileComplete = false
    )

    override fun matePosts() = listOf(
        MatePost(1, "오늘 저녁 헬스 같이 해요", Sport.FITNESS, "성수동", "오늘 19:30", "1.5시간", 2, 1, "초중급", "웨이트와 유산소를 함께 진행해요. 각자 루틴도 환영합니다.", "민준", true),
        MatePost(2, "한강 5 km 러닝 메이트", Sport.RUNNING, "뚝섬", "오늘 20:00", "약 40분", 4, 2, "초보", "5 km 완주가 목표예요. 천천히 같이 달려요.", "지현", true),
        MatePost(3, "주말 클라이밍 파트너", Sport.CLIMBING, "건대입구", "토요일 14:00", "2시간", 2, 1, "중급", "볼더링 위주로 함께 운동할 분을 찾아요.", "도윤", false)
    )

    override fun communityPosts() = listOf(
        CommunityPost(1, "민준", "오늘 러닝 완료! 5 km를 달리고 목표 페이스를 달성했어요. #러닝 #습관만들기", true, 24, 3, "10분 전"),
        CommunityPost(2, "수연", "처음으로 스쿼트 50 kg에 성공했어요. 다음 목표는 60 kg!", false, 16, 2, "1시간 전")
    )

    override fun dmConversations() = listOf(
        DmConversation(1, "민준", 1, listOf(DirectMessage(1, "오늘 운동 수고했어요!", false))),
        DmConversation(2, "지현", 0, listOf(DirectMessage(2, "러닝 일정 확인 부탁드려요.", false)))
    )
}
