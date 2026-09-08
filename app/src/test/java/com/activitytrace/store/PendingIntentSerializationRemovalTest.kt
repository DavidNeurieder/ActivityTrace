package com.activitytrace.store

import com.activitytrace.model.CapturedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingIntentSerializationRemovalTest {

    @Test
    fun `notification capture works without PendingIntent metadata`() {
        val item = CapturedItem(
            text = "Test notification",
            appPackage = "com.example",
            contentType = "notification",
            timestamp = 1000L,
            contentHash = ContentHasher.hash("com.example", "notification", "Test notification"),
        )

        assertNull(item.metadata)
        assertEquals("com.example", item.appPackage)
        assertEquals("notification", item.contentType)
    }

    @Test
    fun `old records with arbitrary metadata strings are still displayable`() {
        val oldRecord = CapturedItem(
            text = "Old notification",
            appPackage = "com.old",
            contentType = "notification",
            timestamp = 2000L,
            metadata = "some old parcel blob that we no longer understand",
            contentHash = ContentHasher.hash("com.old", "notification", "Old notification"),
        )

        assertEquals("some old parcel blob that we no longer understand", oldRecord.metadata)
    }
}