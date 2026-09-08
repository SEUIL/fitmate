package com.fitmate.app.data.remote

import android.content.Context
import com.google.gson.JsonObject

/** 인바디 사진 분석과 운동 기록 API의 UI 전용 어댑터. */
internal class ServerInbodyApi(private val api: FitMateApiClient) {
    private val media = ServerSocialApi(api)

    fun upload(context: Context, uri: String): String {
        // Refresh an expired access token before starting the multipart request.
        api.request("GET", "users/me")
        return media.uploadImage(context, uri, "INBODY")
    }
    fun createAnalysis(mediaId: String): JsonObject = api.request("POST", "inbody-analyses", mapOf("mediaId" to mediaId)).asJsonObject
    fun analysis(id: String): JsonObject = api.request("GET", "inbody-analyses/$id").asJsonObject
    fun retry(id: String): JsonObject = api.request("POST", "inbody-analyses/$id/retry").asJsonObject
    fun save(body: Map<String, Any?>): JsonObject = api.request("POST", "inbody-records", body).asJsonObject
    fun records(): List<JsonObject> = api.items("inbody-records")
    fun delete(recordId: String) = api.request("DELETE", "inbody-records/$recordId")
}
