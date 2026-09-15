package com.fitmate.app.ui

/** Operator API display and command models. */
internal enum class AdminAction(val label: String) { NONE("반려"), WARNING("경고"), SUSPEND("기간 정지") }
internal enum class AdminDecisionReason(val label: String) {
    POLICY_VIOLATION("운영 정책 위반"), SPAM("광고 / 스팸"), HARASSMENT("괴롭힘"),
    NO_VIOLATION("위반 없음"), INSUFFICIENT_EVIDENCE("근거 부족")
}

internal data class AdminDecisionCommand(
    val reportId: String,
    val expectedVersion: Long,
    val action: AdminAction,
    val reasonCode: AdminDecisionReason,
    val publicReason: String,
    val durationDays: Int?,
    val internalNote: String?,
    val idempotencyKey: String
) {
    val resolution get() = if (action == AdminAction.NONE) AdminReportStatus.DISMISSED else AdminReportStatus.RESOLVED
    fun isValid(): Boolean = expectedVersion >= 0 && idempotencyKey.isNotBlank() &&
        publicReason.isNotBlank() && publicReason.length <= 500 && (internalNote?.length ?: 0) <= 500 &&
        (if (action == AdminAction.SUSPEND) durationDays in listOf(1, 3, 7, 30) else durationDays == null) &&
        (if (action == AdminAction.NONE) reasonCode in listOf(AdminDecisionReason.NO_VIOLATION, AdminDecisionReason.INSUFFICIENT_EVIDENCE)
        else reasonCode in listOf(AdminDecisionReason.POLICY_VIOLATION, AdminDecisionReason.SPAM, AdminDecisionReason.HARASSMENT))
}

internal data class AdminSnapshot(val reports: List<AdminReport>, val history: List<String>,
    val metrics: List<Long>? = null, val nextCursor: String? = null,
    val members: List<String> = emptyList(), val contents: List<AdminReport> = emptyList(),
    val recentReports: List<AdminReport> = reports.take(3), val remote: Boolean = false,
    val role: String? = null) {
    val pendingCount get() = metrics?.firstOrNull()?.toInt() ?: reports.count { it.status == AdminReportStatus.RECEIVED || it.status == AdminReportStatus.REVIEWING }
    fun search(query: String, status: AdminReportStatus?) = reports.filter { report ->
        (status == null || report.status == status) && listOf(report.id, report.title, report.member, report.type).any { it.contains(query.trim(), true) }
    }
}

internal enum class AdminError(val message: String) {
    NETWORK("요청 결과를 확인하지 못했습니다. 최신 상태를 다시 조회해 주세요."),
    VERSION_CONFLICT("다른 작업으로 상태가 변경되었습니다. 최신 내용을 확인해 주세요."),
    IDEMPOTENCY_CONFLICT("이미 사용한 요청과 내용이 다릅니다. 처리 결과를 먼저 확인해 주세요."),
    ALREADY_PROCESSED("이미 처리된 신고입니다. 최신 상태를 확인해 주세요."),
    VALIDATION("조치 종류, 사유와 기간을 확인해 주세요."),
    NOT_FOUND("대상을 찾을 수 없습니다. 목록을 다시 조회해 주세요."),
    SESSION_EXPIRED("로그인이 만료되었습니다. 센터를 종료한 뒤 다시 로그인해 주세요."),
    FORBIDDEN("이 작업에 필요한 운영자 권한이 없습니다."),
    UNKNOWN("처리 결과를 확인하지 못했습니다. 다시 조회해 주세요.")
}
internal class AdminFailure(val code: AdminError) : Exception(code.name)

internal interface AdminRepository {
    suspend fun fetch(): AdminSnapshot
    suspend fun search(query: String, status: AdminReportStatus?, cursor: String? = null): AdminSnapshot = fetch().let { it.copy(reports = it.search(query, status)) }
    suspend fun detail(id: String): AdminReport = fetch().reports.first { it.id == id }
    suspend fun claim(id: String, version: Long): AdminSnapshot
    suspend fun submit(command: AdminDecisionCommand): AdminSnapshot
}
