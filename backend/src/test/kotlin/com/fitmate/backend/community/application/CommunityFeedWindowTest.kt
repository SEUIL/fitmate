package com.fitmate.backend.community.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommunityFeedWindowTest {
    @Test fun `cursor is a row offset and lookahead does not multiply it`() {
        assertEquals(0L, communityFeedWindow(0, 20).offset)
        assertEquals(20L, communityFeedWindow(20, 20).offset)
        assertEquals(40L, communityFeedWindow(40, 20).offset)
        assertEquals(21, communityFeedWindow(20, 20).pageSize)
    }
}
