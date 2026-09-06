package com.fitmate.app.ui

import java.time.LocalDate
import com.fitmate.app.data.repository.MockFitMateRepository
import org.junit.Assert.*
import org.junit.Test

class FrontendStateTest {
    @Test fun `custom workout requires an exercise before recording`() {
        val session = WorkoutSession("", false)
        assertTrue(session.sessionExercises.isEmpty())
        assertFalse(session.recordSet())
        assertTrue(session.recordedSets.isEmpty())
        assertNotNull(session.error)
        session.exerciseName = "스쿼트"
        session.sessionExercises.add("스쿼트")
        assertTrue(session.recordSet())
        assertEquals("스쿼트", session.recordedSets.single().exerciseName)
    }

    @Test fun `pausing keeps sets timer input and remaining rest`() {
        val session = WorkoutSession("랫풀다운", true)
        session.memo = "마지막 세트"
        assertTrue(session.recordSet())
        session.elapsedSeconds = 75
        session.remainingRestSeconds = 42
        session.pause()
        assertFalse(session.isTimerRunning)
        assertFalse(session.isRestTimerRunning)
        assertEquals(75, session.elapsedSeconds)
        assertEquals(42, session.remainingRestSeconds)
        assertEquals("마지막 세트", session.recordedSets.single().memo)
        session.weightText = "NaN"
        assertFalse(session.recordSet())
        assertEquals(1, session.recordedSets.size)
    }

    @Test fun `composer retains draft until explicitly cleared`() {
        val social = SocialState(emptyList(), emptyList())
        social.composerText.value = "작성 중"
        social.composerPhoto.value = "content://example/photo"
        social.composerWorkout.value = "헬스 1세트"
        social.composerVisibility.value = "맞팔 공개"
        social.communityTab.value = "내 게시물"
        social.openComposer = true
        social.openComposer = false
        assertEquals("작성 중", social.composerText.value)
        assertNotNull(social.composerPhoto.value)
        social.clearComposer()
        assertEquals("", social.composerText.value)
        assertNull(social.composerPhoto.value)
        assertNull(social.composerWorkout.value)
        assertEquals("전체 공개", social.composerVisibility.value)
        assertEquals("내 게시물", social.communityTab.value)
    }
    @Test fun `inbody rejects impossible future and non finite measurements`() {
        val today = LocalDate.of(2026, 9, 6)
        assertNotNull(validateBody("2026.02.30", "68", "29", "18", emptyMap(), today))
        assertNotNull(validateBody("2026.09.07", "68", "29", "18", emptyMap(), today))
        assertNotNull(validateBody("2026.09.06", "NaN", "29", "18", emptyMap(), today))
        assertNotNull(validateBody("2026.09.06", "68", "69", "18", emptyMap(), today))
        assertNotNull(validateBody("2026.09.06", "68", "29", "101", emptyMap(), today))
        assertNull(validateBody("2026.09.06", "68", "29", "18", emptyMap(), today))
    }

    @Test fun `optional body values are validated but empty is allowed`() {
        val date = LocalDate.of(2026, 9, 6)
        assertNull(validateBody("2026.09.06", "68", "29", "18", mapOf("BMI" to ""), date))
        assertNotNull(validateBody("2026.09.06", "68", "29", "18", mapOf("BMI" to "Infinity"), date))
    }

    @Test fun `run laps account for a crossing between ticks and multiple crossings`() {
        val run = RunningState()
        run.tick(300, 900)
        assertEquals(0, run.laps.size)
        run.tick(100, 300)
        assertEquals(333, run.laps.single().seconds)
        run.tick(600, 1800)
        assertEquals(3, run.laps.size)
        assertEquals(1000, run.laps.sumOf { it.seconds })
        assertEquals(3000, run.meters)
    }

    @Test fun `gps loss preserves elapsed time without fabricating laps`() {
        val run = RunningState()
        run.tick(30, 0)
        assertEquals(30, run.seconds)
        assertTrue(run.laps.isEmpty())
        assertEquals("—", runPace(run.seconds, run.meters))
    }

    @Test fun `device gps updates create laps from the actual tracked distance`() {
        val run = RunningState()
        run.updateFromDeviceTracking(300, 900)
        run.updateFromDeviceTracking(400, 1_200)
        assertEquals(400, run.seconds)
        assertEquals(1_200, run.meters)
        assertEquals(1, run.laps.size)
        assertEquals(333, run.laps.single().seconds)
    }

    @Test fun `request remains limited after first message is deleted`() {
        val social = SocialState(emptyList(), emptyList())
        val room = ChatRoom(1, "test", emptyList(), "요청 대기", incoming = false)
        room.draft = "hello"
        social.send(room)
        assertEquals(1, room.messages.size)
        room.messages = emptyList()
        room.draft = "again"
        social.send(room)
        assertTrue(room.messages.isEmpty())
    }

    @Test fun `blocked and rejected rooms cannot send`() {
        val social = SocialState(emptyList(), emptyList())
        val room = ChatRoom(1, "test", emptyList())
        room.draft = "hello"
        social.blocked = setOf("test")
        social.send(room)
        assertTrue(room.messages.isEmpty())
        social.blocked = emptySet()
        room.status = "거절"
        social.send(room)
        assertTrue(room.messages.isEmpty())
    }

    @Test fun `incoming request cannot send before acceptance`() {
        val social = SocialState(emptyList(), emptyList())
        val room = ChatRoom(1, "test", emptyList(), "요청 대기", incoming = true)
        room.draft = "hello"
        social.send(room)
        assertTrue(room.messages.isEmpty())
        room.status = "대화 가능"
        social.send(room)
        assertEquals("전송 중", room.messages.single().state)
    }

    @Test fun `only active mate workout becomes completed`() {
        val state = MateFeatureState()
        state.activeWorkoutId = 1
        state.completeWorkout()
        assertTrue(state.room(1).completed)
        assertFalse(state.room(2).completed)
        assertNull(state.activeWorkoutId)
    }

    @Test fun `host acceptance cannot exceed capacity even before UI recomposition`() {
        val post = MockFitMateRepository.matePosts().first()
        val room = MateRoom()
        room.applicants = listOf(MateApplicant(1, "a"), MateApplicant(2, "b"))
        room.respond(1, true, post)
        room.respond(2, true, post)
        assertEquals(1, room.applicants.count { it.status == "참가 확정" })
        assertEquals("신청 대기", room.applicants.last().status)
    }
}
