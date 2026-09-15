package com.fitmate.app.ui

import com.fitmate.app.data.remote.ApiFailure
import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/** All requests use the signed-in session; server failures never fall back to fixtures. */
internal class ServerAdminRepository(private val api: FitMateApiClient) : AdminRepository {
    private var currentQuery = ""
    private var currentStatus: AdminReportStatus? = null
    override suspend fun fetch(): AdminSnapshot = search(currentQuery, currentStatus)

    override suspend fun search(query: String, status: AdminReportStatus?, cursor: String?): AdminSnapshot = io {
        currentQuery = query; currentStatus = status
        val access = get("admin/access")
        if (!access["eligible"].asBoolean) throw AdminFailure(AdminError.FORBIDDEN)
        val dashboard = get("admin/dashboard")
        val params = mutableListOf("size=20", "query=${encode(query)}")
        status?.let { params += "status=${it.name}" }
        cursor?.let { params += "cursor=${encode(it)}" }
        val page = get("admin/reports?${params.joinToString("&")}")
        AdminSnapshot(
            reports = page.getAsJsonArray("items").map { summary(it.asJsonObject) },
            history = dashboard.getAsJsonArray("recentHistory").map { it.asJsonObject.text("summary").orEmpty() },
            metrics = listOf("pendingReports", "newUsers", "completedWorkouts", "failedAnalyses").map { dashboard.getAsJsonObject(it)["value"].asLong },
            nextCursor = page.text("nextCursor"),
            members = get("admin/members").getAsJsonArray("items").map { it.asJsonObject.text("nickname") ?: "탈퇴 또는 프로필 없음" },
            contents = get("admin/contents").getAsJsonArray("items").map { item ->
                val value = item.asJsonObject
                AdminReport(value["reportId"].asString, type(value["targetType"].asString), value["title"].asString,
                    "", "", time(value["createdAt"].asString), AdminReportStatus.valueOf(value["status"].asString), remote = true)
            },
            recentReports = dashboard.getAsJsonArray("recentReports").map { summary(it.asJsonObject) }, remote = true, role = access.text("role"))
    }

    override suspend fun detail(id: String): AdminReport = io { parseDetail(get("admin/reports/$id")) }
    suspend fun notices(managing: Boolean, cursor: String? = null): NoticePageData = io {
        val path = if (managing) "admin/notices" else "notices"
        val value = get(path + "?size=20" + (cursor?.let { "&cursor=${encode(it)}" } ?: ""))
        NoticePageData(value.getAsJsonArray("items").map { notice(it.asJsonObject) }, value.text("nextCursor"))
    }
    suspend fun noticeDetail(managing: Boolean, id: String): NoticeData = io {
        notice(get((if (managing) "admin/notices/" else "notices/") + id))
    }
    suspend fun saveNotice(id: String?, title: String, body: String, version: Long, key: String): NoticeData = io {
        notice(api.request(if (id == null) "POST" else "PUT", "admin/notices" + (id?.let { "/$it" } ?: ""),
            mapOf("title" to title, "body" to body, "expectedVersion" to version), headers = mapOf("Idempotency-Key" to key)).asJsonObject)
    }
    suspend fun transitionNotice(id: String, action: String, version: Long, key: String): NoticeData = io {
        require(action in listOf("publish", "end"))
        notice(api.request("POST", "admin/notices/$id/$action", mapOf("expectedVersion" to version), headers = mapOf("Idempotency-Key" to key)).asJsonObject)
    }
    suspend fun categories(): CategoryCatalogData = io { catalog(get("admin/categories")) }
    suspend fun reorderCategories(ids: List<String>, version: Long, key: String): CategoryCatalogData = io {
        catalog(api.request("PUT", "admin/categories/order", mapOf("ids" to ids, "expectedVersion" to version), headers = mapOf("Idempotency-Key" to key)).asJsonObject)
    }
    private fun notice(value: JsonObject) = NoticeData(value["id"].asString, value["title"].asString, value.text("body").orEmpty(),
        value["status"].asString, value["version"].asLong, value.text("publishedAt")?.let(::time))
    private fun catalog(value: JsonObject) = CategoryCatalogData(value["version"].asLong,
        value.getAsJsonArray("items").map { row -> row.asJsonObject.let { CategoryData(it["id"].asString, it["code"].asString, it["name"].asString, it["active"].asBoolean) } },
        value.getAsJsonArray("otherSports").map { row -> row.asJsonObject.let { it["name"].asString to it["memberCount"].asLong } })
    suspend fun systemSummary(days: Int): AdminSystemData = io {
        require(days in listOf(1, 7, 30))
        // Let the server choose the end timestamp; the device clock cannot produce a future 'to'.
        val baseline = get("admin/system/summary")
        val value = if (days == 1) baseline else {
            val end = java.time.Instant.parse(baseline["to"].asString)
            get("admin/system/summary?from=${encode(end.minusSeconds(days * 86400L).toString())}&to=${encode(end.toString())}")
        }
        fun counts(key: String) = value.getAsJsonArray(key).map { row ->
            row.asJsonObject.let { it["code"].asString to it["count"].asLong }
        }
        AdminSystemData(time(value["from"].asString), time(value["to"].asString), time(value["asOf"].asString),
            counts("statuses"), counts("failures"), value.get("failureRate")?.takeUnless { it.isJsonNull }?.asDouble)
    }
    override suspend fun claim(id: String, version: Long): AdminSnapshot {
        io { api.request("POST", "admin/reports/$id/claim", mapOf("expectedVersion" to version)) }
        return fetch()
    }
    override suspend fun submit(command: AdminDecisionCommand): AdminSnapshot {
        if (!command.isValid()) throw AdminFailure(AdminError.VALIDATION)
        io { api.request("POST", "admin/reports/${command.reportId}/decisions", mapOf(
            "expectedVersion" to command.expectedVersion, "resolution" to command.resolution.name,
            "action" to command.action.name, "reasonCode" to command.reasonCode.name,
            "publicReason" to command.publicReason, "internalNote" to command.internalNote,
            "durationDays" to command.durationDays), headers = mapOf("Idempotency-Key" to command.idempotencyKey)) }
        return fetch()
    }
    private fun get(path: String) = api.request("GET", path).asJsonObject
    private fun summary(value: JsonObject): AdminReport = AdminReport(
        value["id"].asString, type(value["targetType"].asString), value["title"].asString,
        value.get("subjectUser")?.takeUnless { it.isJsonNull }?.asJsonObject?.text("nickname") ?: "대상 회원 정보 없음",
        reason(value["reporterReason"].asString), time(value["createdAt"].asString),
        AdminReportStatus.valueOf(value["status"].asString), version = value["version"].asLong,
        allowedActions = emptyList(), remote = true)
    private fun parseDetail(value: JsonObject): AdminReport {
        val evidence = value.getAsJsonObject("evidence")
        val decision = value.get("decision")?.takeUnless { it.isJsonNull }?.asJsonObject
        return summary(value.getAsJsonObject("report")).copy(
            evidence = evidence.text("text"), reporterStatement = evidence.text("reporterStatement"),
            history = value.getAsJsonArray("history").map { "${time(it.asJsonObject.text("occurredAt").orEmpty())} · ${it.asJsonObject.text("summary")}" },
            allowedActions = value.getAsJsonArray("allowedActions").map { AdminAction.valueOf(it.asString) },
            decision = decision?.let { d -> listOfNotNull(d.text("action")?.let { AdminAction.valueOf(it).label },
                d.text("reasonCode")?.let { AdminDecisionReason.valueOf(it).label }, d.text("publicReason"),
                d.text("decidedAt")?.let { "처리 시각: ${time(it)}" },
                d.text("sanctionEndsAt")?.let { "정지 종료: ${time(it)}" }, d.text("internalNote")?.let { "내부 메모: $it" }).joinToString("\n") })
    }
    private fun time(value: String): String = java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    private fun reason(value: String) = when (value) {
        "SPAM" -> "광고 / 스팸"; "HARASSMENT" -> "괴롭힘"; "HATE" -> "혐오"; "SEXUAL" -> "성적 콘텐츠"
        "VIOLENCE" -> "폭력"; "SELF_HARM" -> "자해"; "OTHER" -> "기타"; else -> value
    }
    private fun type(value: String) = when (value) { "USER" -> "회원"; "POST" -> "커뮤니티"; "COMMENT" -> "댓글"; "DM_MESSAGE" -> "DM"; "MATE_POST" -> "메이트"; else -> value }
    private fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (error: ApiFailure) {
            throw AdminFailure(when (error.code) {
                "VERSION_CONFLICT" -> AdminError.VERSION_CONFLICT
                "IDEMPOTENCY_KEY_CONFLICT" -> AdminError.IDEMPOTENCY_CONFLICT
                "INVALID_STATE_TRANSITION" -> AdminError.ALREADY_PROCESSED
                else -> when (error.status) { 400 -> AdminError.VALIDATION; 401 -> AdminError.SESSION_EXPIRED; 403 -> AdminError.FORBIDDEN; 404 -> AdminError.NOT_FOUND; else -> AdminError.UNKNOWN }
            })
        } catch (_: IOException) { throw AdminFailure(AdminError.NETWORK) }
    }
}
