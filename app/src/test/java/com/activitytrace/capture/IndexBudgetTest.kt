package com.activitytrace.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexBudgetTest {

    @Test
    fun `max files enforced`() {
        val budget = IndexBudget(IndexLimits(maxFiles = 2))
        assertTrue(budget.canAccept(1L, IndexLimits(maxFiles = 2)))
        budget.accept(1L)
        assertTrue(budget.canAccept(1L, IndexLimits(maxFiles = 2)))
        budget.accept(1L)
        assertFalse(budget.canAccept(1L, IndexLimits(maxFiles = 2)))
        assertTrue(budget.exhausted)
        assertEquals(2, budget.filesIndexed)
    }

    @Test
    fun `max total bytes enforced across files`() {
        val limits = IndexLimits(maxTotalBytes = 100)
        val budget = IndexBudget(limits)
        assertTrue(budget.canAccept(60L, limits))
        budget.accept(60L)
        assertFalse(budget.canAccept(60L, limits))
        assertTrue(budget.canAccept(40L, limits))
        budget.accept(40L)
        assertTrue(budget.exhausted)
        assertFalse(budget.canAccept(1L, limits))
        assertEquals(100L, budget.bytesIndexed)
    }

    @Test
    fun `per file byte limit rejected without consuming budget`() {
        val limits = IndexLimits(maxFileBytes = 100, maxTotalBytes = 10_000)
        val budget = IndexBudget(limits)
        assertFalse(budget.canAccept(101L, limits))
        assertTrue(budget.canAccept(50L, limits))
        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `zero length files count without consuming bytes`() {
        val limits = IndexLimits()
        val budget = IndexBudget(limits)
        assertTrue(budget.canAccept(0L, limits))
        budget.accept(0L)
        assertEquals(1, budget.filesIndexed)
        assertEquals(0L, budget.bytesIndexed)
    }
}