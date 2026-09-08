package com.activitytrace.capture

import com.activitytrace.capture.CaptureLimits.EventDebouncer
import com.activitytrace.capture.CaptureLimits.EventRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLimitsTest {

    @Test
    fun `first event for a key is allowed`() {
        val debouncer = EventDebouncer(windowMs = 300L)
        assertTrue(debouncer.allow("com.a|A", now = 1000L))
    }

    @Test
    fun `event within the debounce window is dropped`() {
        val debouncer = EventDebouncer(windowMs = 300L)
        debouncer.allow("com.a|A", now = 1000L)
        assertFalse(debouncer.allow("com.a|A", now = 1299L))
    }

    @Test
    fun `event after the debounce window is allowed again`() {
        val debouncer = EventDebouncer(windowMs = 300L)
        debouncer.allow("com.a|A", now = 1000L)
        assertTrue(debouncer.allow("com.a|A", now = 1300L))
        assertFalse(debouncer.allow("com.a|A", now = 1599L))
    }

    @Test
    fun `different window keys debounce independently`() {
        val debouncer = EventDebouncer(windowMs = 300L)
        assertTrue(debouncer.allow("com.a|A", now = 1000L))
        assertTrue(debouncer.allow("com.b|B", now = 1000L))
    }

    @Test
    fun `clear resets all windows`() {
        val debouncer = EventDebouncer(windowMs = 300L)
        debouncer.allow("com.a|A", now = 1000L)
        debouncer.clear()
        assertTrue(debouncer.allow("com.a|A", now = 1001L))
    }

    @Test
    fun `allows up to the per second limit`() {
        val limiter = EventRateLimiter(maxPerSecond = 10)
        repeat(10) {
            assertTrue("event ${it + 1} should be allowed", limiter.tryAcquire(now = 1000L))
        }
        assertFalse(limiter.tryAcquire(now = 1000L))
        assertFalse(limiter.tryAcquire(now = 1999L))
    }

    @Test
    fun `window rolls over after one second`() {
        val limiter = EventRateLimiter(maxPerSecond = 3)
        assertTrue(limiter.tryAcquire(now = 1000L))
        assertTrue(limiter.tryAcquire(now = 1000L))
        assertTrue(limiter.tryAcquire(now = 1000L))
        assertFalse(limiter.tryAcquire(now = 1000L))
        assertTrue(limiter.tryAcquire(now = 2000L))
    }

    @Test
    fun `event flood is throttled`() {
        val limiter = EventRateLimiter(maxPerSecond = 10)
        val accepted = (1..50).count { limiter.tryAcquire(now = 5000L) }
        assertEquals(10, accepted)
    }
}