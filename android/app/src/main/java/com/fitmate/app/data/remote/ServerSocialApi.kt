package com.fitmate.app.data.remote

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject

/** P2 소셜 API의 UUID 기반 계약을 UI mock 모델과 분리한다. */
internal class ServerSocialApi(private val api: FitMateApiClient) {
    fun imageBytes(path: String): ByteArray = api.downloadImage(path)
    fun feedPage(followingOnly: Boolean = false, cursor: String? = null): JsonObject =
        api.request("GET", "community/posts?followingOnly=$followingOnly&limit=20" + cursor?.let { "&cursor=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()).asJsonObject
    fun feed(followingOnly: Boolean = false): List<JsonObject> = feedPage(followingOnly).rows("items")

    fun post(postId: String): JsonObject = api.request("GET", "community/posts/$postId").asJsonObject

    fun communityMediaId(context: Context, source: String): String {
        if (source.startsWith("content:") || source.startsWith("file:"))
            return uploadImage(context, source, "COMMUNITY_POST")
        val match = Regex("^/api/v1/community/posts/[^/]+/media/([0-9a-fA-F-]{36})$").matchEntire(source)
            ?: error("첨부 사진 정보를 확인할 수 없습니다. 게시물을 다시 열어 주세요.")
        return java.util.UUID.fromString(match.groupValues[1]).toString()
    }

    fun uploadImage(context: Context, uri: String, purpose: String): String {
        val parsed = Uri.parse(uri)
        val bytes = context.contentResolver.openInputStream(parsed)?.use { it.readBytes() } ?: error("사진을 읽을 수 없습니다.")
        val type = context.contentResolver.getType(parsed)?.lowercase() ?: "image/jpeg"
        val name = parsed.lastPathSegment?.substringAfterLast('/') ?: "fitmate-image.jpg"
        return api.uploadImage(purpose, name, type, bytes).get("mediaId")?.asString ?: error("사진 식별자를 받지 못했습니다.")
    }

    fun createPost(content: String, mutualOnly: Boolean, mediaIds: List<String> = emptyList()) = api.request("POST", "community/posts", mapOf(
        "content" to content, "visibility" to if (mutualOnly) "MUTUAL" else "PUBLIC", "mediaIds" to mediaIds,
    )).asJsonObject
    fun updatePost(postId: String, content: String, mutualOnly: Boolean, version: Long, mediaIds: List<String>? = null) = api.request("PUT", "community/posts/$postId", buildMap {
        put("content", content); put("visibility", if (mutualOnly) "MUTUAL" else "PUBLIC"); put("version", version)
        mediaIds?.let { put("mediaIds", it) }
    }).asJsonObject

    fun like(postId: String) = api.request("POST", "community/posts/$postId/likes").asJsonObject
    fun unlike(postId: String) = api.request("DELETE", "community/posts/$postId/likes").asJsonObject
    fun comments(postId: String): List<JsonObject> = api.items("community/posts/$postId/comments")
    fun addComment(postId: String, content: String) = api.request("POST", "community/posts/$postId/comments", mapOf("content" to content)).asJsonObject
    fun deleteComment(postId: String, commentId: String) = api.request("DELETE", "community/posts/$postId/comments/$commentId")
    fun deletePost(postId: String) = api.request("DELETE", "community/posts/$postId")

    fun follow(userId: String) = api.request("POST", "users/$userId/follow").asJsonObject
    fun unfollow(userId: String) = api.request("DELETE", "users/$userId/follow").asJsonObject
    fun profile(userId: String) = api.request("GET", "users/$userId/profile").asJsonObject
    fun followers(userId: String): List<JsonObject> = api.items("users/$userId/followers")
    fun following(userId: String): List<JsonObject> = api.items("users/$userId/following")
    fun followRequests(): List<JsonObject> = api.items("users/me/follow-requests")
    fun acceptFollowRequest(userId: String) = api.request("POST", "users/me/follow-requests/$userId/accept").asJsonObject
    fun rejectFollowRequest(userId: String) = api.request("POST", "users/me/follow-requests/$userId/reject").asJsonObject
    fun removeFollower(userId: String) = api.request("DELETE", "users/me/followers/$userId").asJsonObject
    fun block(userId: String) = api.request("POST", "users/$userId/block").asJsonObject
    fun unblock(userId: String) = api.request("DELETE", "users/$userId/block")
    fun blocks(): List<JsonObject> = api.items("users/me/blocks")

    fun startConversation(userId: String, content: String) = api.request("POST", "dm/conversations", mapOf("recipientUserId" to userId, "content" to content)).asJsonObject
    fun conversations(): List<JsonObject> = api.items("dm/conversations")
    fun messageRequests(): List<JsonObject> = api.items("dm/requests")
    fun messages(conversationId: String): List<JsonObject> = api.items("dm/conversations/$conversationId/messages")
    fun sendMessage(conversationId: String, content: String, mediaId: String? = null) = api.request("POST", "dm/conversations/$conversationId/messages", buildMap {
        put("content", content); mediaId?.let { put("mediaId", it) }
    }).asJsonObject
    fun respondToMessageRequest(conversationId: String, accepted: Boolean) = api.request("POST", "dm/conversations/$conversationId/${if (accepted) "accept" else "reject"}").asJsonObject

    fun notifications(): List<JsonObject> = api.items("notifications")
    fun readNotification(notificationId: String) = api.request("PATCH", "notifications/$notificationId/read").asJsonObject
    fun readAllNotifications() = api.request("PATCH", "notifications/read-all")
    fun deleteAllNotifications() = api.request("DELETE", "notifications")

    fun report(targetType: String, targetId: String, reason: String, detail: String?) = api.request("POST", "reports", mapOf(
        "targetType" to targetType, "targetId" to targetId, "reason" to reason, "detail" to detail,
    )).asJsonObject
}

private fun JsonObject.rows(key: String): List<JsonObject> = getAsJsonArray(key)?.map { it.asJsonObject }.orEmpty()
