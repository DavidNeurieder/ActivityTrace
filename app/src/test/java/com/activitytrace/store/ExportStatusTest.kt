package com.activitytrace.store

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportStatusTest {

    @Test
    fun `Success message is preserved`() {
        assertEquals("ok", ExportStatus.Success("ok").message)
    }

    @Test
    fun `Error message is preserved`() {
        assertEquals("fail", ExportStatus.Error("fail").message)
    }

    @Test
    fun `Progress message is preserved`() {
        assertEquals("working", ExportStatus.Progress("working").message)
    }

    @Test
    fun `Info message is preserved`() {
        assertEquals("info", ExportStatus.Info("info").message)
    }

    @Test
    fun `when expression matches all sealed variants`() {
        val variants: List<ExportStatus> = listOf(
            ExportStatus.Success("a"),
            ExportStatus.Error("b"),
            ExportStatus.Progress("c"),
            ExportStatus.Info("d"),
        )
        val messages = variants.map { status ->
            when (status) {
                is ExportStatus.Success -> "s:${status.message}"
                is ExportStatus.Error -> "e:${status.message}"
                is ExportStatus.Progress -> "p:${status.message}"
                is ExportStatus.Info -> "i:${status.message}"
            }
        }
        assertEquals(listOf("s:a", "e:b", "p:c", "i:d"), messages)
    }
}
