package com.fitmate.app.ui

internal enum class AdminReportStatus(val label: String) {
    RECEIVED("접수"), REVIEWING("검토 중"), RESOLVED("처리 완료"), DISMISSED("반려")
}

internal data class AdminReport(
    val id: String, val type: String, val title: String, val member: String,
    val reason: String, val time: String,
    val status: AdminReportStatus = AdminReportStatus.RECEIVED,
    val decision: String? = null
)

/** Local preview boundary. Never reads or mutates real accounts or server reports. */
internal interface AdminRepository {
    val reports: List<AdminReport>
    val history: List<String>
    val pendingCount: Int
    fun search(query: String, status: AdminReportStatus?): List<AdminReport>
    fun startReview(id: String): Boolean
    fun decide(id: String, action: String, reason: String, days: Int = 7, note: String = ""): Boolean
}

internal class FakeAdminRepository : AdminRepository {
    override var reports = listOf(
        AdminReport("R-1001", "커뮤니티", "반복적인 홍보 게시물", "러너01", "광고 / 스팸", "오늘 14:20"),
        AdminReport("R-1002", "메이트", "모집 내용과 다른 안내", "메이트02", "기타", "오늘 13:45"),
        AdminReport("R-1003", "회원", "프로필 내 부적절한 표현", "회원03", "괴롭힘", "오늘 12:10"),
        AdminReport("R-1004", "댓글", "공격적인 댓글", "회원04", "괴롭힘", "오늘 11:30"),
        AdminReport("R-1005", "DM", "원하지 않는 홍보 메시지", "회원05", "광고 / 스팸", "오늘 10:15")
    )
        private set
    override var history = emptyList<String>()
        private set
    override val pendingCount get() = reports.count { it.status in setOf(AdminReportStatus.RECEIVED, AdminReportStatus.REVIEWING) }
    override fun search(query: String, status: AdminReportStatus?) = reports.filter {
        (status == null || it.status == status) && listOf(it.id, it.title, it.member, it.type).any { value -> value.contains(query.trim(), true) }
    }
    override fun startReview(id: String): Boolean {
        val report = reports.find { it.id == id } ?: return false
        if (report.status != AdminReportStatus.RECEIVED) return false
        reports = reports.map { if (it.id == id) it.copy(status = AdminReportStatus.REVIEWING) else it }
        return true
    }
    override fun decide(id: String, action: String, reason: String, days: Int, note: String): Boolean {
        val report = reports.find { it.id == id } ?: return false
        if (report.status != AdminReportStatus.REVIEWING || reason.isBlank() || reason.length > 500 || note.length > 500) return false
        if (action !in listOf("반려", "경고", "기간 정지")) return false
        if (action == "기간 정지" && days !in listOf(1, 3, 7, 30)) return false
        val summary = "$action${if (action == "기간 정지") " ${days}일" else ""} · ${reason.trim()}"
        reports = reports.map { if (it.id == id) it.copy(status = if (action == "반려") AdminReportStatus.DISMISSED else AdminReportStatus.RESOLVED, decision = summary) else it }
        history = listOf("${report.id} · ${report.member} · $summary" + if (note.isBlank()) "" else "\n내부 메모: ${note.trim()}") + history
        return true
    }
}
