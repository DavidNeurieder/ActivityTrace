package com.activitytrace.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturedItemTest {

    @Test
    fun `create with all fields`() {
        val item = CapturedItem(
            id = 1L,
            text = "Hello",
            appPackage = "com.example",
            contentType = "notification",
            timestamp = 1000L,
            metadata = "extra"
        )
        assertEquals(1L, item.id)
        assertEquals("Hello", item.text)
        assertEquals("com.example", item.appPackage)
        assertEquals("notification", item.contentType)
        assertEquals(1000L, item.timestamp)
        assertEquals("extra", item.metadata)
    }

    @Test
    fun `default id is zero`() {
        val item = CapturedItem(
            text = "test",
            appPackage = "com.test",
            contentType = "text",
            timestamp = 0L
        )
        assertEquals(0L, item.id)
    }

    @Test
    fun `metadata defaults to null`() {
        val item = CapturedItem(
            text = "test",
            appPackage = "com.test",
            contentType = "text",
            timestamp = 0L
        )
        assertNull(item.metadata)
    }
}
