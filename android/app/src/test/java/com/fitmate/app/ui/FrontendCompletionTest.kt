package com.fitmate.app.ui

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class FrontendCompletionTest {
    @Test fun `social snapshot retains drafts privacy and interrupted delivery without id collisions`() {
        val state = SocialState(emptyList(), emptyList())
        state.composerText.value = "saved draft"
        state.blocked = setOf("blocked")
        state.following = setOf("민준")
        val room = state.chat("민준"); room.draft = "hello"; state.send(room)
        room.draft = "next draft"
        val originalId = room.messages.single().id
        val gson = Gson()
        val saved = gson.fromJson(gson.toJson(state.snapshot()), SocialSnapshot::class.java)
        val restored = SocialState(emptyList(), emptyList()); saved.restore(restored)
        assertEquals("saved draft", restored.composerText.value)
        assertTrue("blocked" in restored.blocked); assertTrue("민준" in restored.mutual)
        val restoredRoom = restored.chat("민준")
        assertEquals("전송 실패", restoredRoom.messages.single().state)
        assertEquals("next draft", restoredRoom.draft)
        assertTrue(restored.nextId() > originalId)
    }
    @Test fun `empty social and body records do not restore seed data`() {
        val state = SocialState(emptyList(), emptyList()); state.chats.clear()
        val gson = Gson()
        val saved = FrontendSnapshot(social = state.snapshot(), body = emptyList())
        val decoded = gson.fromJson(gson.toJson(saved), FrontendSnapshot::class.java)
        val restored = SocialState(emptyList(), emptyList()); decoded.social!!.restore(restored)
        assertTrue(restored.chats.isEmpty()); assertEquals(emptyList<BodySnapshot>(), decoded.body)
        val old = gson.fromJson("{\"version\":1}", FrontendSnapshot::class.java)
        assertNull(old.social); assertNull(old.body)
    }
    @Test fun `body date storage uses portable ISO date and preserves identity`() {
        val entry = BodyEntry(date = java.time.LocalDate.of(2026, 9, 6), weight = 70.0, muscle = 30.0, fat = 20.0)
        val gson = Gson()
        assertEquals(entry, gson.fromJson(gson.toJson(entry.snapshot()), BodySnapshot::class.java).restore())
    }
    @Test fun `timeline interleaves sports newest first and preserves undated records`() {
        val workouts = listOf(PreviewWorkouts.first().copy(completedAt = 100L), PreviewWorkouts.last())
        val rows = recordTimeline(workouts, listOf(PreviewRun.copy(completedAt = 200L)))
        assertEquals(listOf("러닝", "헬스", "헬스"), rows.map { it.sport })
        assertNull(rows.last().time); assertEquals("날짜 없음", recordMonth(null))
        val legacy = Gson().fromJson("{\"durationMinutes\":10,\"sets\":[],\"usedSamsungHealth\":false,\"targetSetCount\":0}", com.fitmate.app.data.model.WorkoutSummary::class.java)
        assertNull(legacy.completedAt)
    }
    @Test fun `mate snapshot keeps participant decision and active workout link`() {
        val state = MateFeatureState(); state.activeWorkoutId = 42
        state.room(42).applicants = listOf(MateApplicant(1, "tester", "참가 확정"))
        state.room(42).closed = true
        val gson = Gson()
        val restored = MateFeatureState()
        gson.fromJson(gson.toJson(state.snapshot(emptyList())), MateSnapshot::class.java).restore(restored)
        assertEquals(42L, restored.activeWorkoutId); assertTrue(restored.room(42).closed)
        assertEquals("참가 확정", restored.room(42).applicants.single().status)
    }
    @Test fun `running snapshot restores laps history and pauses tracking`() {
        val running = RunningState()
        running.phase = "진행"; running.demo = true; running.tick(360, 1000)
        val gson = Gson()
        val decoded = gson.fromJson(gson.toJson(running.snapshot()), RunningSnapshot::class.java).restore()
        assertEquals(1000, decoded.meters); assertEquals(360, decoded.seconds)
        assertEquals(running.laps, decoded.laps); assertTrue(decoded.paused)
    }
    @Test fun `email and mock password inputs reject invalid values`() {
        assertFalse(validDemoEmail("wrong")); assertFalse(validDemoEmail("a b@example.com"))
        assertTrue(validDemoEmail("tester@example.com"))
        assertFalse(validDemoPassword("short1")); assertFalse(validDemoPassword("12345678"))
        assertTrue(validDemoPassword("DemoOnly123"))
    }
    @Test fun `pounds convert to canonical kilograms without reinterpreting history`() {
        val session = WorkoutSession("스쿼트", false)
        session.weightText = "100"; assertTrue(session.recordSet())
        session.changeUnit("lb")
        assertEquals(220.46, session.weightText.toDouble(), .01)
        assertTrue(session.recordSet())
        assertEquals(100.0, session.recordedSets.last().weightKg, .01)
        session.changeUnit("kg")
        assertEquals(100.0, session.weightText.toDouble(), .01)
        assertEquals(100.0, session.recordedSets.first().weightKg, .001)
    }
    @Test fun `session snapshot is independent and resumes paused`() {
        val session = WorkoutSession("스쿼트", true)
        session.sessionExercises.add("랫풀다운"); session.recordSet(); session.elapsedSeconds = 123
        session.remainingRestSeconds = 42; session.exerciseDraft = "new"; session.unit = "lb"
        val copy = session.snapshot()
        session.recordedSets.clear(); session.sessionExercises.clear()
        val restored = copy.restore()
        assertEquals(2, restored.sessionExercises.size); assertEquals(1, restored.recordedSets.size)
        assertEquals(123, restored.elapsedSeconds); assertEquals(42, restored.remainingRestSeconds)
        assertEquals("new", restored.exerciseDraft); assertEquals("lb", restored.unit)
        assertFalse(restored.isTimerRunning); assertFalse(restored.isRestTimerRunning)
    }
    @Test fun `persistent snapshot round trips session routines and history`() {
        val session = WorkoutSession("스쿼트", false).also { it.recordSet() }
        val value = FrontendSnapshot(workout = session.snapshot(), routines = ExampleRoutines)
        val gson = Gson()
        val decoded = gson.fromJson(gson.toJson(value), FrontendSnapshot::class.java)
        assertEquals(value, decoded)
        assertEquals("스쿼트", decoded.workout!!.restore().recordedSets.single().exerciseName)
    }
    @Test fun `private profile and DM remain gated until follow accepted`() {
        val social = SocialState(emptyList(), emptyList()); val a = social.accounts; val name = "비공개 메이트"
        assertFalse(a.visibleProfile(social, name)); assertFalse(a.canDm(social, name))
        a.follow(social, name)
        assertEquals("PENDING", a.outgoing[name]); assertFalse(a.canDm(social, name))
        a.resolveOutgoing(social, name, true)
        assertTrue(a.visibleProfile(social, name)); assertTrue(a.canDm(social, name))
        assertFalse(name in social.mutual)
        a.followers = a.followers + name; a.reconcile(social)
        assertTrue(name in social.mutual)
        a.follow(social, name)
        assertFalse(name in social.mutual); assertFalse(a.visibleProfile(social, name))
    }
    @Test fun `canceled or blocked requests cannot be accepted later`() {
        val social = SocialState(emptyList(), emptyList()); val a = social.accounts; val name = "비공개 메이트"
        a.follow(social, name); a.follow(social, name); a.resolveOutgoing(social, name, true)
        assertFalse(name in social.following)
        a.follow(social, name); social.blocked = setOf(name); a.resolveOutgoing(social, name, true)
        assertFalse(a.canDm(social, name)); assertFalse(name in social.following)
    }
    @Test fun `incoming rejection does not grant follower access`() {
        val social = SocialState(emptyList(), emptyList()); val a = social.accounts
        a.respond(social, "요청 메이트", false)
        assertEquals("REJECTED", a.incoming["요청 메이트"])
        assertFalse("요청 메이트" in a.followers)
    }
    @Test fun `new outbound room is not treated as incoming request`() {
        val social = SocialState(emptyList(), emptyList())
        val room = social.chat("비공개 메이트"); room.status = "요청 대기"; room.draft = "hello"
        social.send(room)
        assertEquals(1, room.messages.size); assertTrue(room.requestSent)
    }
}
