package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test

class AdminDemoRepositoryTest {
    @Test fun reviewMustStartBeforeDecision() {
        val repo = FakeAdminRepository()
        assertFalse(repo.decide("R-1001", "반려", "위반 없음"))
        assertTrue(repo.startReview("R-1001"))
        assertTrue(repo.decide("R-1001", "반려", "위반 없음"))
        assertEquals(AdminReportStatus.DISMISSED, repo.reports.first().status)
        assertFalse(repo.decide("R-1001", "경고", "다시 처리"))
    }
    @Test fun invalidSanctionsCannotBeSaved() {
        val repo = FakeAdminRepository()
        repo.startReview("R-1001")
        assertFalse(repo.decide("R-1001", "기간 정지", "사유", 2))
        assertFalse(repo.decide("R-1001", "경고", "  "))
        assertFalse(repo.decide("R-1001", "영구 정지", "사유"))
        assertTrue(repo.decide("R-1001", "기간 정지", "반복적인 광고 게시", 7))
        assertEquals(1, repo.history.size)
        assertEquals(AdminReportStatus.RESOLVED, repo.reports.first().status)
    }
    @Test fun filtersAndCountsReflectDecisions() {
        val repo = FakeAdminRepository()
        assertEquals(5, repo.pendingCount)
        assertEquals(1, repo.search("러너01", null).size)
        repo.startReview("R-1001")
        repo.decide("R-1001", "반려", "위반 없음")
        assertEquals(4, repo.pendingCount)
        assertEquals(1, repo.search("", AdminReportStatus.DISMISSED).size)
    }
}
