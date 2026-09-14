package com.fitmate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDisplayCountsTest {
    private val demoCounts = listOf("3", "2", "1")

    @Test fun pendingServerCountsNeverExposeDemoNumbers() {
        assertEquals(listOf("—", "—", "—"), profileDisplayCounts(true, null, demoCounts))
    }

    @Test fun serverZerosAreDisplayedAsRealValues() {
        assertEquals(listOf("0", "0", "0"), profileDisplayCounts(true, listOf("0", "0", "0"), demoCounts))
    }

    @Test fun populatedServerCountsTakePriority() {
        assertEquals(listOf("12", "7", "4"), profileDisplayCounts(true, listOf("12", "7", "4"), demoCounts))
    }

    @Test fun offlinePreviewsCanStillUseLocalCounts() {
        assertEquals(demoCounts, profileDisplayCounts(false, null, demoCounts))
    }
}
