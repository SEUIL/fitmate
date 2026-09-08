package com.fitmate.backend.mate.api

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import com.fitmate.backend.ops.repository.IdempotencyKeyRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.datasource.url=\${FITMATE_TEST_DB_URL}",
    "spring.datasource.username=\${FITMATE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=\${FITMATE_TEST_DB_PASSWORD:}",
])
class MatePostApiIntegrationTest {
    @Autowired private lateinit var http: TestRestTemplate
    @Autowired private lateinit var idempotencyKeys: IdempotencyKeyRepository

    companion object {
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("fitmate.security.jwt.secret") { UUID.randomUUID().toString().repeat(2) }
        }
    }

    @Test
    fun `host creates post and applicant can apply then host accepts`() {
        val host = signup()
        val applicant = signup()
        val stranger = signup()
        val createRequest = mapOf(
            "title" to "주말 운동", "body" to "함께 운동해요", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001",
            "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(), "locationSummary" to "여의도",
            "capacity" to 2, "difficulty" to "BEGINNER",
        )
        val key = UUID.randomUUID()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, createRequest, key)
        assertEquals(HttpStatus.CREATED, post.statusCode, post.body.toString())
        val postId = post.body!!["data"]["id"].asText()
        assertEquals(1, post.body!!["data"]["acceptedCount"].asInt())
        val replay = command("/api/v1/mate-posts", HttpMethod.POST, host, createRequest, key)
        assertEquals(HttpStatus.CREATED, replay.statusCode, replay.body.toString())
        assertEquals(postId, replay.body!!["data"]["id"].asText())

        val room = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, applicant, null)
        assertEquals(HttpStatus.CREATED, room.statusCode, room.body.toString())
        val roomId = room.body!!["data"]["id"].asText()
        val applicantDetail = command("/api/v1/mate-posts/$postId", HttpMethod.GET, applicant, null)
        assertEquals(HttpStatus.OK, applicantDetail.statusCode, applicantDetail.body.toString())
        assertEquals(roomId, applicantDetail.body!!["data"]["myInquiryRoomId"].asText())
        assertEquals(true, applicantDetail.body!!["data"]["allowedActions"].isArray)
        val forbiddenRoom = command("/api/v1/mate-chat-rooms/$roomId", HttpMethod.GET, stranger, null)
        assertEquals(HttpStatus.NOT_FOUND, forbiddenRoom.statusCode, forbiddenRoom.body.toString())
        val message = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, applicant, mapOf(
            "clientMessageId" to UUID.randomUUID().toString(), "body" to "문의드립니다.",
        ))
        assertEquals(HttpStatus.CREATED, message.statusCode, message.body.toString())
        assertEquals(1, message.body!!["data"]["sequenceNo"].asInt())
        val read = command("/api/v1/mate-chat-rooms/$roomId/read", HttpMethod.PUT, applicant, mapOf("lastReadMessageId" to message.body!!["data"]["id"].asText()))
        assertEquals(HttpStatus.OK, read.statusCode, read.body.toString())
        assertEquals(1, read.body!!["data"]["myLastReadSequence"].asInt())

        val applied = command("/api/v1/mate-posts/$postId/applications", HttpMethod.POST, applicant, null)
        assertEquals(HttpStatus.CREATED, applied.statusCode, applied.body.toString())
        val applicationId = applied.body!!["data"]["id"].asText()
        val accepted = command("/api/v1/mate-posts/$postId/applications/$applicationId/accept", HttpMethod.POST, host, mapOf("version" to 0))
        assertEquals(HttpStatus.OK, accepted.statusCode, accepted.body.toString())
        assertEquals("ACCEPTED", accepted.body!!["data"]["status"].asText())
        assertEquals("FULL", get("/api/v1/mate-posts?status=FULL", host)["items"][0]["status"].asText())
        val detail = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null)
        assertEquals(HttpStatus.OK, detail.statusCode, detail.body.toString())
        val closed = command("/api/v1/mate-posts/$postId/close", HttpMethod.POST, host, mapOf("version" to detail.body!!["data"]["version"].asLong()))
        assertEquals(HttpStatus.OK, closed.statusCode, closed.body.toString())
        assertEquals("CLOSED", closed.body!!["data"]["status"].asText())
        val messageAfterClose = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, applicant, mapOf(
            "clientMessageId" to UUID.randomUUID().toString(), "body" to "마감 후에도 대화합니다.",
        ))
        assertEquals(HttpStatus.CREATED, messageAfterClose.statusCode, messageAfterClose.body.toString())
        val notifications = command("/api/v1/notifications?unreadOnly=true", HttpMethod.GET, host, null)
        assertEquals(HttpStatus.OK, notifications.statusCode, notifications.body.toString())
        assertEquals(true, notifications.body!!["data"]["items"].isArray)
        val notificationId = notifications.body!!["data"]["items"][0]["id"].asText()
        val foreignNotification = command("/api/v1/notifications/$notificationId/read", HttpMethod.PATCH, stranger, null)
        assertEquals(HttpStatus.NOT_FOUND, foreignNotification.statusCode, foreignNotification.body.toString())
        val readNotification = command("/api/v1/notifications/$notificationId/read", HttpMethod.PATCH, host, null)
        assertEquals(HttpStatus.OK, readNotification.statusCode, readNotification.body.toString())
        val closedDetail = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null)
        val canceled = command("/api/v1/mate-posts/$postId/cancel", HttpMethod.POST, host, mapOf("version" to closedDetail.body!!["data"]["version"].asLong()))
        assertEquals(HttpStatus.OK, canceled.statusCode, canceled.body.toString())
        val messageAfterCancel = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, applicant, mapOf(
            "clientMessageId" to UUID.randomUUID().toString(), "body" to "취소 뒤에는 전송 불가",
        ))
        assertEquals(HttpStatus.CONFLICT, messageAfterCancel.statusCode, messageAfterCancel.body.toString())
        assertEquals("MATE_CHAT_READ_ONLY", messageAfterCancel.body!!["error"]["code"].asText())
    }

    @Test
    fun `mate notifications respect recipient setting without rejecting the application`() {
        val host = signup()
        val applicant = signup()
        val settings = command("/api/v1/users/me/notification-settings", HttpMethod.GET, host, null)
        assertEquals(HttpStatus.OK, settings.statusCode, settings.body.toString())
        val disabled = command("/api/v1/users/me/notification-settings", HttpMethod.PATCH, host, mapOf(
            "inAppEnabled" to true, "dmEnabled" to true, "mateEnabled" to false,
            "communityEnabled" to true, "version" to settings.body!!["data"]["version"].asLong(),
        ))
        assertEquals(HttpStatus.OK, disabled.statusCode, disabled.body.toString())
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "알림 설정 검증", "body" to "신청은 가능해야 합니다", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 2, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        assertEquals(HttpStatus.CREATED, post.statusCode, post.body.toString())
        val applied = command("/api/v1/mate-posts/${post.body!!["data"]["id"].asText()}/applications", HttpMethod.POST, applicant, null)
        assertEquals(HttpStatus.CREATED, applied.statusCode, applied.body.toString())
        val unread = command("/api/v1/notifications/unread-count", HttpMethod.GET, host, null)
        assertEquals(HttpStatus.OK, unread.statusCode, unread.body.toString())
        assertEquals(0, unread.body!!["data"]["unreadCount"].asInt())
    }

    @Test
    fun `simultaneous create requests with one idempotency key replay one post`() {
        val host = signup()
        val request = mapOf(
            "title" to "동시 생성", "body" to "한 건만 생성되어야 합니다", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 2, "difficulty" to "BEGINNER",
        )
        val key = UUID.randomUUID()
        val first = CompletableFuture.supplyAsync { command("/api/v1/mate-posts", HttpMethod.POST, host, request, key) }
        val second = CompletableFuture.supplyAsync { command("/api/v1/mate-posts", HttpMethod.POST, host, request, key) }
        val responses = listOf(first.join(), second.join())
        responses.forEach { assertEquals(HttpStatus.CREATED, it.statusCode, it.body.toString()) }
        assertEquals(responses[0].body!!["data"]["id"].asText(), responses[1].body!!["data"]["id"].asText())
    }

    @Test
    fun `expired idempotency key creates a new post while an active key replays its original response`() {
        val host = signup()
        val request = mapOf(
            "title" to "멱등성 만료", "body" to "최초 응답 재생", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 3, "difficulty" to "BEGINNER",
        )
        val key = UUID.randomUUID()
        val first = command("/api/v1/mate-posts", HttpMethod.POST, host, request, key)
        assertEquals(HttpStatus.CREATED, first.statusCode, first.body.toString())
        val firstId = first.body!!["data"]["id"].asText()
        val update = command("/api/v1/mate-posts/$firstId", HttpMethod.PATCH, host, mapOf("version" to first.body!!["data"]["version"].asLong(), "title" to "변경된 제목"))
        assertEquals(HttpStatus.OK, update.statusCode, update.body.toString())
        val replay = command("/api/v1/mate-posts", HttpMethod.POST, host, request, key)
        assertEquals(HttpStatus.CREATED, replay.statusCode, replay.body.toString())
        assertEquals(first.body!!["data"], replay.body!!["data"])

        val storedKey = idempotencyKeys.findByIdempotencyKey(key)!!
        storedKey.expiresAt = Instant.EPOCH
        idempotencyKeys.saveAndFlush(storedKey)
        val renewed = command("/api/v1/mate-posts", HttpMethod.POST, host, request, key)
        assertEquals(HttpStatus.CREATED, renewed.statusCode, renewed.body.toString())
        assertTrue(firstId != renewed.body!!["data"]["id"].asText())
    }

    @Test
    fun `member cannot mark another accessible room message as read`() {
        val host = signup()
        val first = signup()
        val second = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "방 경계", "body" to "두 문의방", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 3, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        val postId = post.body!!["data"]["id"].asText()
        val firstRoom = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, first, null).body!!["data"]["id"].asText()
        val secondRoom = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, second, null).body!!["data"]["id"].asText()
        val secondMessage = command("/api/v1/mate-chat-rooms/$secondRoom/messages", HttpMethod.POST, second, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "두번째 방"))
        assertEquals(HttpStatus.CREATED, secondMessage.statusCode, secondMessage.body.toString())
        val invalidRead = command("/api/v1/mate-chat-rooms/$firstRoom/read", HttpMethod.PUT, host, mapOf("lastReadMessageId" to secondMessage.body!!["data"]["id"].asText()))
        assertEquals(HttpStatus.NOT_FOUND, invalidRead.statusCode, invalidRead.body.toString())
    }

    @Test
    fun `concurrent reads from one user never move room sequence backwards`() {
        val host = signup()
        val applicant = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "읽음 경쟁", "body" to "두 기기", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 2, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        val roomId = command("/api/v1/mate-posts/${post.body!!["data"]["id"].asText()}/inquiry-room", HttpMethod.POST, applicant, null).body!!["data"]["id"].asText()
        val first = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, host, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "첫 메시지"))
        val second = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, host, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "둘 메시지"))
        val firstId = first.body!!["data"]["id"].asText()
        val secondId = second.body!!["data"]["id"].asText()
        val low = CompletableFuture.supplyAsync { command("/api/v1/mate-chat-rooms/$roomId/read", HttpMethod.PUT, applicant, mapOf("lastReadMessageId" to firstId)) }
        val high = CompletableFuture.supplyAsync { command("/api/v1/mate-chat-rooms/$roomId/read", HttpMethod.PUT, applicant, mapOf("lastReadMessageId" to secondId)) }
        listOf(low.join(), high.join()).forEach { assertEquals(HttpStatus.OK, it.statusCode, it.body.toString()) }
        val room = command("/api/v1/mate-chat-rooms/$roomId", HttpMethod.GET, applicant, null)
        assertEquals(2, room.body!!["data"]["myLastReadSequence"].asInt())
    }

    @Test
    fun `simultaneous duplicate applications create exactly one participation`() {
        val host = signup()
        val applicant = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "중복 신청", "body" to "한 번만 신청", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 2, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        val path = "/api/v1/mate-posts/${post.body!!["data"]["id"].asText()}/applications"
        val first = CompletableFuture.supplyAsync { command(path, HttpMethod.POST, applicant, null) }
        val second = CompletableFuture.supplyAsync { command(path, HttpMethod.POST, applicant, null) }
        val statuses = listOf(first.join().statusCode, second.join().statusCode)
        assertEquals(1, statuses.count { it == HttpStatus.CREATED })
        assertEquals(1, statuses.count { it == HttpStatus.CONFLICT })
    }

    @Test
    fun `last remaining slot accepts exactly one concurrent applicant`() {
        val host = signup(); val first = signup(); val second = signup()
        val postId = createPost(host, "마지막 한 자리", capacity = 2)
        val firstApplication = apply(postId, first)
        val secondApplication = apply(postId, second)
        val firstAccept = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$postId/applications/$firstApplication/accept", HttpMethod.POST, host, mapOf("version" to 0)) }
        val secondAccept = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$postId/applications/$secondApplication/accept", HttpMethod.POST, host, mapOf("version" to 0)) }
        val responses = listOf(firstAccept.join(), secondAccept.join())
        assertEquals(1, responses.count { it.statusCode == HttpStatus.OK })
        assertEquals(1, responses.count { it.statusCode == HttpStatus.CONFLICT })
        val detail = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null).body!!["data"]
        assertEquals("FULL", detail["status"].asText())
        assertEquals(2, detail["acceptedCount"].asInt())
    }

    @Test
    fun `inquiry-only user receives one close notification and message replay is stable`() {
        val host = signup(); val inquirer = signup()
        val postId = createPost(host, "문의 종료 알림")
        val roomId = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, inquirer, null).body!!["data"]["id"].asText()
        val clientMessageId = UUID.randomUUID().toString()
        val sent = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, inquirer, mapOf("clientMessageId" to clientMessageId, "body" to "첫 문의"))
        assertEquals(HttpStatus.CREATED, sent.statusCode, sent.body.toString())
        assertEquals("SENT", sent.body!!["data"]["status"].asText())
        val replay = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, inquirer, mapOf("clientMessageId" to clientMessageId, "body" to "첫 문의"))
        assertEquals(HttpStatus.OK, replay.statusCode, replay.body.toString())
        assertEquals(sent.body!!["data"]["id"].asText(), replay.body!!["data"]["id"].asText())
        val conflict = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, inquirer, mapOf("clientMessageId" to clientMessageId, "body" to "다른 본문"))
        assertEquals(HttpStatus.CONFLICT, conflict.statusCode, conflict.body.toString())

        val detail = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null)
        val close = command("/api/v1/mate-posts/$postId/close", HttpMethod.POST, host, mapOf("version" to detail.body!!["data"]["version"].asLong()))
        assertEquals(HttpStatus.OK, close.statusCode, close.body.toString())
        val notifications = command("/api/v1/notifications?unreadOnly=true", HttpMethod.GET, inquirer, null)
        assertEquals(HttpStatus.OK, notifications.statusCode, notifications.body.toString())
        assertEquals(1, notifications.body!!["data"]["items"].count { it["type"].asText() == "MATE_POST_CLOSED" })
        assertEquals("MATE_POST", notifications.body!!["data"]["items"].first { it["type"].asText() == "MATE_POST_CLOSED" }["target"]["type"].asText())
    }

    @Test
    fun `message sequence queries honor database page limits`() {
        val host = signup(); val inquirer = signup()
        val postId = createPost(host, "메시지 페이지")
        val roomId = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, inquirer, null).body!!["data"]["id"].asText()
        (1..3).forEach { sequence ->
            val sent = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, host, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "메시지 $sequence"))
            assertEquals(HttpStatus.CREATED, sent.statusCode, sent.body.toString())
        }
        val after = command("/api/v1/mate-chat-rooms/$roomId/messages?afterSequence=1&size=1", HttpMethod.GET, inquirer, null)
        assertEquals(HttpStatus.OK, after.statusCode, after.body.toString())
        assertEquals(1, after.body!!["data"]["items"].size())
        assertEquals(2, after.body!!["data"]["items"][0]["sequenceNo"].asInt())
        val before = command("/api/v1/mate-chat-rooms/$roomId/messages?beforeSequence=3&size=1", HttpMethod.GET, inquirer, null)
        assertEquals(HttpStatus.OK, before.statusCode, before.body.toString())
        assertEquals(1, before.body!!["data"]["items"].size())
        assertEquals(2, before.body!!["data"]["items"][0]["sequenceNo"].asInt())
    }

    @Test
    fun `inquiry-only user receives one cancellation notification`() {
        val host = signup(); val inquirer = signup()
        val postId = createPost(host, "문의 취소 알림")
        command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, inquirer, null)
        val version = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null).body!!["data"]["version"].asLong()
        val canceled = command("/api/v1/mate-posts/$postId/cancel", HttpMethod.POST, host, mapOf("version" to version))
        assertEquals(HttpStatus.OK, canceled.statusCode, canceled.body.toString())
        val notifications = command("/api/v1/notifications?unreadOnly=true", HttpMethod.GET, inquirer, null)
        assertEquals(1, notifications.body!!["data"]["items"].count { it["type"].asText() == "MATE_POST_CANCELED" })
    }

    @Test
    fun `message send racing with cancellation leaves room read only`() {
        val host = signup(); val inquirer = signup()
        val postId = createPost(host, "종료 전송 경쟁")
        val roomId = command("/api/v1/mate-posts/$postId/inquiry-room", HttpMethod.POST, inquirer, null).body!!["data"]["id"].asText()
        val version = command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null).body!!["data"]["version"].asLong()
        val send = CompletableFuture.supplyAsync { command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, inquirer, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "경쟁 메시지")) }
        val cancel = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$postId/cancel", HttpMethod.POST, host, mapOf("version" to version)) }
        assertEquals(HttpStatus.OK, cancel.join().statusCode)
        assertTrue(send.join().statusCode in setOf(HttpStatus.CREATED, HttpStatus.CONFLICT))
        val afterCancel = command("/api/v1/mate-chat-rooms/$roomId/messages", HttpMethod.POST, inquirer, mapOf("clientMessageId" to UUID.randomUUID().toString(), "body" to "종료 뒤 메시지"))
        assertEquals(HttpStatus.CONFLICT, afterCancel.statusCode, afterCancel.body.toString())
        assertEquals("READ_ONLY", command("/api/v1/mate-chat-rooms/$roomId", HttpMethod.GET, inquirer, null).body!!["data"]["status"].asText())
    }

    @Test
    fun `accept and close race leaves a valid post state`() {
        val host = signup(); val applicant = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "수락 마감 경쟁", "body" to "상태 일관성", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 3, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        val id = post.body!!["data"]["id"].asText()
        val application = command("/api/v1/mate-posts/$id/applications", HttpMethod.POST, applicant, null)
        val applicationId = application.body!!["data"]["id"].asText()
        val accept = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$id/applications/$applicationId/accept", HttpMethod.POST, host, mapOf("version" to 0)) }
        val close = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$id/close", HttpMethod.POST, host, mapOf("version" to 0)) }
        listOf(accept.join(), close.join()).forEach { assertTrue(it.statusCode in setOf(HttpStatus.OK, HttpStatus.CONFLICT), it.body.toString()) }
        val detail = command("/api/v1/mate-posts/$id", HttpMethod.GET, host, null).body!!["data"]
        assertTrue(detail["status"].asText() in setOf("RECRUITING", "CLOSED"))
        assertTrue(detail["acceptedCount"].asInt() in 1..3)
    }

    @Test
    fun `accept and cancel race leaves a valid post state`() {
        val host = signup(); val applicant = signup()
        val post = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "수락 취소 경쟁", "body" to "상태 일관성", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "locationSummary" to "여의도", "capacity" to 3, "difficulty" to "BEGINNER",
        ), UUID.randomUUID())
        val id = post.body!!["data"]["id"].asText()
        val applicationId = apply(id, applicant)
        val accept = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$id/applications/$applicationId/accept", HttpMethod.POST, host, mapOf("version" to 0)) }
        val cancel = CompletableFuture.supplyAsync { command("/api/v1/mate-posts/$id/cancel", HttpMethod.POST, host, mapOf("version" to 0)) }
        listOf(accept.join(), cancel.join()).forEach { assertTrue(it.statusCode in setOf(HttpStatus.OK, HttpStatus.CONFLICT), it.body.toString()) }
        val detail = command("/api/v1/mate-posts/$id", HttpMethod.GET, host, null).body!!["data"]
        assertTrue(detail["status"].asText() in setOf("RECRUITING", "CANCELED"))
        assertTrue(detail["acceptedCount"].asInt() in 1..3)
    }

    @Test
    fun `an applicant cannot reapply after rejection cancellation or withdrawal`() {
        val host = signup(); val applicant = signup()

        val rejectedPost = createPost(host, "거절 후 재신청")
        val rejectedApplication = apply(rejectedPost, applicant)
        val rejected = command("/api/v1/mate-posts/$rejectedPost/applications/$rejectedApplication/reject", HttpMethod.POST, host, mapOf("version" to 0))
        assertEquals(HttpStatus.OK, rejected.statusCode, rejected.body.toString())
        assertReapplyRejected(rejectedPost, applicant)

        val canceledPost = createPost(host, "취소 후 재신청")
        val canceledApplication = apply(canceledPost, applicant)
        val canceled = command("/api/v1/mate-posts/$canceledPost/applications/$canceledApplication/cancel", HttpMethod.POST, applicant, mapOf("version" to 0))
        assertEquals(HttpStatus.OK, canceled.statusCode, canceled.body.toString())
        assertReapplyRejected(canceledPost, applicant)

        val withdrawnPost = createPost(host, "철회 후 재신청")
        val withdrawnApplication = apply(withdrawnPost, applicant)
        val accepted = command("/api/v1/mate-posts/$withdrawnPost/applications/$withdrawnApplication/accept", HttpMethod.POST, host, mapOf("version" to 0))
        assertEquals(HttpStatus.OK, accepted.statusCode, accepted.body.toString())
        val acceptedVersion = command("/api/v1/mate-posts/$withdrawnPost/applications/me", HttpMethod.GET, applicant, null).body!!["data"]["version"].asLong()
        val withdrawn = command("/api/v1/mate-posts/$withdrawnPost/applications/$withdrawnApplication/cancel", HttpMethod.POST, applicant, mapOf("version" to acceptedVersion))
        assertEquals(HttpStatus.OK, withdrawn.statusCode, withdrawn.body.toString())
        assertReapplyRejected(withdrawnPost, applicant)
    }

    @Test
    fun `post details can change before an application but protected details cannot change after it`() {
        val host = signup(); val applicant = signup()
        val created = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
            "title" to "수정 정책", "body" to "첫 신청 전 변경", "sportCode" to "HEALTH",
            "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
            "durationSeconds" to 1800, "locationSummary" to "여의도", "capacity" to 3, "difficulty" to "BEGINNER", "estimatedCostKrw" to 0,
        ), UUID.randomUUID())
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body.toString())
        val postId = created.body!!["data"]["id"].asText()
        val changed = command("/api/v1/mate-posts/$postId", HttpMethod.PATCH, host, mapOf(
            "version" to created.body!!["data"]["version"].asLong(), "scheduledStartAt" to Instant.now().plusSeconds(7200).toString(),
            "durationSeconds" to 3600, "locationSummary" to "잠실", "difficulty" to "ADVANCED", "estimatedCostKrw" to 12000,
        ))
        assertEquals(HttpStatus.OK, changed.statusCode, changed.body.toString())
        assertEquals("잠실", changed.body!!["data"]["locationSummary"].asText())
        assertEquals(3600, changed.body!!["data"]["durationSeconds"].asInt())
        assertEquals("ADVANCED", changed.body!!["data"]["difficulty"].asText())
        assertEquals(12000, changed.body!!["data"]["estimatedCostKrw"].asInt())
        apply(postId, applicant)
        val rejected = command("/api/v1/mate-posts/$postId", HttpMethod.PATCH, host, mapOf(
            "version" to command("/api/v1/mate-posts/$postId", HttpMethod.GET, host, null).body!!["data"]["version"].asLong(), "locationSummary" to "강남",
        ))
        assertEquals(HttpStatus.CONFLICT, rejected.statusCode, rejected.body.toString())
        assertEquals("INVALID_STATE_TRANSITION", rejected.body!!["error"]["code"].asText())
    }

    private fun signup(): String {
        val response = http.postForEntity("/api/v1/auth/signup", mapOf(
            "email" to "mate-${UUID.randomUUID()}@example.com", "password" to "test-password-123",
            "nickname" to "mate-${UUID.randomUUID().toString().take(16)}",
            "sports" to listOf(mapOf("sportCode" to "HEALTH", "skillLevel" to "BEGINNER")),
            "goals" to listOf(mapOf("goalCode" to "FITNESS", "primary" to true)),
            "consentDocumentIds" to listOf("20000000-0000-0000-0000-000000000001", "20000000-0000-0000-0000-000000000002"),
        ), JsonNode::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode, response.body.toString())
        return response.body!!["data"]["tokens"]["accessToken"].asText()
    }

    private fun createPost(host: String, title: String, capacity: Int = 3): String = command("/api/v1/mate-posts", HttpMethod.POST, host, mapOf(
        "title" to title, "body" to "신청 이력 보존", "sportCode" to "HEALTH",
        "regionId" to "10000000-0000-0000-0000-000000000001", "scheduledStartAt" to Instant.now().plusSeconds(3600).toString(),
        "locationSummary" to "여의도", "capacity" to capacity, "difficulty" to "BEGINNER",
    ), UUID.randomUUID()).also { assertEquals(HttpStatus.CREATED, it.statusCode, it.body.toString()) }.body!!["data"]["id"].asText()

    private fun apply(postId: String, applicant: String): String = command("/api/v1/mate-posts/$postId/applications", HttpMethod.POST, applicant, null)
        .also { assertEquals(HttpStatus.CREATED, it.statusCode, it.body.toString()) }.body!!["data"]["id"].asText()

    private fun assertReapplyRejected(postId: String, applicant: String) {
        val replay = command("/api/v1/mate-posts/$postId/applications", HttpMethod.POST, applicant, null)
        assertEquals(HttpStatus.CONFLICT, replay.statusCode, replay.body.toString())
        assertEquals("MATE_APPLICATION_ALREADY_EXISTS", replay.body!!["error"]["code"].asText())
    }

    private fun get(path: String, token: String): JsonNode = command(path, HttpMethod.GET, token, null).also { assertEquals(HttpStatus.OK, it.statusCode, it.body.toString()) }.body!!["data"]
    private fun command(path: String, method: HttpMethod, token: String, body: Any?, key: UUID? = null) = http.exchange(path, method, HttpEntity(body, HttpHeaders().also { it.setBearerAuth(token); key?.let { value -> it["Idempotency-Key"] = value.toString() } }), JsonNode::class.java)
}
