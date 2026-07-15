package com.activitytrace.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedAppDefaultsTest {

    @Test
    fun `DEFAULT_BLOCKED contains all 7 expected system packages`() {
        assertEquals(7, DEFAULT_BLOCKED.size)
        assertTrue(DEFAULT_BLOCKED.contains("com.android.systemui"))
        assertTrue(DEFAULT_BLOCKED.contains("com.android.settings"))
        assertTrue(DEFAULT_BLOCKED.contains("com.android.launcher3"))
        assertTrue(DEFAULT_BLOCKED.contains("com.google.android.apps.nexuslauncher"))
        assertTrue(DEFAULT_BLOCKED.contains("com.android.launcher"))
        assertTrue(DEFAULT_BLOCKED.contains("com.google.android.inputmethod.latin"))
        assertTrue(DEFAULT_BLOCKED.contains("com.android.inputmethod.latin"))
    }
}
