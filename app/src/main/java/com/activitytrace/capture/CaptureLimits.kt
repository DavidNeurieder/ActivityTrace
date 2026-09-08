package com.activitytrace.capture

import android.os.SystemClock

object CaptureLimits {
    const val DEBOUNCE_WINDOW_MS = 300L
    const val MAX_EVENTS_PER_SECOND = 10

    class EventDebouncer(
        private val windowMs: Long = DEBOUNCE_WINDOW_MS,
    ) {
        private val lastSeen = HashMap<String, Long>()

        fun allow(key: String, now: Long = SystemClock.elapsedRealtime()): Boolean {
            val last = lastSeen[key]
            if (last != null && now - last < windowMs) return false
            lastSeen[key] = now
            return true
        }

        fun clear() {
            lastSeen.clear()
        }
    }

    class EventRateLimiter(
        private val maxPerSecond: Int = MAX_EVENTS_PER_SECOND,
    ) {
        private var windowStart = Long.MIN_VALUE
        private var initialized = false
        private var count = 0

        fun tryAcquire(now: Long = SystemClock.elapsedRealtime()): Boolean {
            if (!initialized) {
                windowStart = now
                initialized = true
            }
            if (now - windowStart >= 1000L) {
                windowStart = now
                count = 0
            }
            if (count >= maxPerSecond) return false
            count++
            return true
        }

        fun reset() {
            windowStart = Long.MIN_VALUE
            initialized = false
            count = 0
        }
    }
}

data class IndexLimits(
    val maxFiles: Int = 10_000,
    val maxTotalBytes: Long = 500L * 1024 * 1024,
    val maxDepth: Int = 20,
    val maxFileBytes: Long = 10L * 1024 * 1024,
    val maxExtractedCharacters: Int = 1_000_000,
    val maxPdfPages: Int = 500,
)