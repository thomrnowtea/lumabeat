package com.lumabeat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun `compact dashboard requires a wide and sufficiently tall landscape viewport`() {
        assertTrue(shouldUseCompactDashboard(widthDp = 866f, heightDp = 387f, fontScale = 1f))
        assertFalse(shouldUseCompactDashboard(widthDp = 640f, heightDp = 360f, fontScale = 1f))
        assertFalse(shouldUseCompactDashboard(widthDp = 800f, heightDp = 350f, fontScale = 1f))
        assertFalse(shouldUseCompactDashboard(widthDp = 411f, heightDp = 842f, fontScale = 1f))
    }

    @Test
    fun `large text falls back to the scrollable dashboard`() {
        assertTrue(shouldUseCompactDashboard(widthDp = 866f, heightDp = 387f, fontScale = 1.15f))
        assertFalse(shouldUseCompactDashboard(widthDp = 866f, heightDp = 387f, fontScale = 1.3f))
        assertEquals(184, blackoutButtonWidthDp(compact = false, fontScale = 1.3f))
        assertEquals(148, blackoutButtonWidthDp(compact = false, fontScale = 1f))
        assertEquals(136, blackoutButtonWidthDp(compact = true, fontScale = 1.3f))
    }
}
