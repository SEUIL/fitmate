package com.fitmate.app.ui

import org.junit.Assert.*
import org.junit.Test

class PostcodeRegionTest {
    @Test fun `dong code stays at dong and rural village resolves to town`() {
        assertEquals("KR-LAW-1168010300", postcodeRegionCode("1168010300"))
        assertEquals("KR-LAW-4113511000", postcodeRegionCode("4113511000"))
        assertEquals("KR-LAW-4161025300", postcodeRegionCode("4161025321"))
    }

    @Test fun `address text and malformed codes never become stored region`() {
        listOf("", "1234", "서울 강남구 개포로 1", "1168010300&address=1").forEach {
            assertThrows(IllegalArgumentException::class.java) { postcodeRegionCode(it) }
        }
    }
}
