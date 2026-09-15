package com.fitmate.app.ui

internal enum class AdminReportStatus(val label: String) {
    RECEIVED("접수"), REVIEWING("검토 중"), RESOLVED("처리 완료"), DISMISSED("반려")
}

internal data class AdminReport(
    val id: String, val type: String, val title: String, val member: String,
    val reason: String, val time: String,
    val status: AdminReportStatus = AdminReportStatus.RECEIVED,
    val decision: String? = null,
    val version: Long = 0,
    val evidence: String? = null, val reporterStatement: String? = null,
    val history: List<String> = emptyList(), val allowedActions: List<AdminAction> = AdminAction.entries,
    val remote: Boolean = false
)

/** Local preview boundary. Never reads or mutates real accounts or server reports. */
internal class FakeAdminRepository(private val latencyMs: Long = 250) : AdminRepository {
    var reports = listOf(
        AdminReport("R-1001", "커뮤니티", "반복적인 홍보 게시물", "러너01", "광고 / 스팸", "오늘 14:20"),
        AdminReport("R-1002", "메이트", "모집 내용과 다른 안내", "메이트02", "기타", "오늘 13:45"),
        AdminReport("R-1003", "회원", "프로필 내 부적절한 표현", "회원03", "괴롭힘", "오늘 12:10"),
        AdminReport("R-1004", "댓글", "공격적인 댓글", "회원04", "괴롭힘", "오늘 11:30"),
        AdminReport("R-1005", "DM", "원하지 않는 홍보 메시지", "회원05", "광고 / 스팸", "오늘 10:15")
    )
        private set
    var history = emptyList<String>()
        private set
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val decisions = mutableMapOf<String, Pair<AdminDecisionCommand, AdminSnapshot>>()
    private fun snapshot() = AdminSnapshot(reports, history)

    override suspend fun fetch(): AdminSnapshot {
        kotlinx.coroutines.delay(latencyMs)
        return snapshot()
    }

    override suspend fun claim(id: String, version: Long): AdminSnapshot = locked {
        val report = reports.find { it.id == id } ?: throw AdminFailure(AdminError.NOT_FOUND)
        if (report.version != version) throw AdminFailure(AdminError.VERSION_CONFLICT)
        if (report.status != AdminReportStatus.RECEIVED) throw AdminFailure(AdminError.ALREADY_PROCESSED)
        reports = reports.map { if (it.id == id) it.copy(status = AdminReportStatus.REVIEWING, version = it.version + 1) else it }
        snapshot()
    }

    override suspend fun submit(command: AdminDecisionCommand): AdminSnapshot = locked {
        if (!command.isValid()) throw AdminFailure(AdminError.VALIDATION)
        decisions[command.idempotencyKey]?.let { previous ->
            if (previous.first != command) throw AdminFailure(AdminError.IDEMPOTENCY_CONFLICT)
            return@locked snapshot()
        }
        val report = reports.find { it.id == command.reportId } ?: throw AdminFailure(AdminError.NOT_FOUND)
        if (report.version != command.expectedVersion) throw AdminFailure(AdminError.VERSION_CONFLICT)
        if (report.status != AdminReportStatus.REVIEWING) throw AdminFailure(AdminError.ALREADY_PROCESSED)
        val summary = "${command.action.label}${command.durationDays?.let { " ${it}일" }.orEmpty()} · ${command.publicReason.trim()}"
        reports = reports.map { if (it.id == report.id) it.copy(status = command.resolution, decision = summary, version = it.version + 1) else it }
        history = listOf("${report.id} · ${report.member} · $summary" + command.internalNote?.takeIf { it.isNotBlank() }?.let { "\n내부 메모: ${it.trim()}" }.orEmpty()) + history
        snapshot().also { decisions[command.idempotencyKey] = command to it }
    }

    private suspend fun <T> locked(action: () -> T): T {
        kotlinx.coroutines.delay(latencyMs)
        mutex.lock()
        try { return action() } finally { mutex.unlock() }
    }
}
