package com.activitytrace.capture

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream

/**
 * The single per-file resource budget every extractor must run under.
 *
 * Input, output and page counts are enforced by the current parsers
 * (plain text, PDF). [maxEntries]/[maxExpandedBytes] bound entry count and
 * expanded output for archive-style parsers once one exists; they are part of
 * the budget so no parser spins up its own ad-hoc limits.
 *
 * [deadlineNanos] is an absolute elapsed-realtime deadline; checking
 * [isPastDeadline] between expensive steps prevents any parser (even one fed
 * a small file with pathological structure) from consuming CPU indefinitely.
 */
class ExtractionBudget(
    val maxInputBytes: Long,
    val maxOutputChars: Int,
    val maxPages: Int,
    val maxEntries: Int,
    val maxExpandedBytes: Long,
    val deadlineNanos: Long,
    private val nowNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) {
    fun isPastDeadline(): Boolean = nowNanos() >= deadlineNanos

    fun exceedsInput(bytes: Long): Boolean = bytes > maxInputBytes

    fun exceedsPages(count: Int): Boolean = count > maxPages

    fun outputExceeded(total: Int): Boolean = total >= maxOutputChars

    companion object {
        fun from(limits: IndexLimits, startNanos: Long = SystemClock.elapsedRealtimeNanos()): ExtractionBudget =
            ExtractionBudget(
                maxInputBytes = limits.maxFileBytes,
                maxOutputChars = limits.maxExtractedCharacters,
                maxPages = limits.maxPdfPages,
                maxEntries = limits.maxArchiveEntries,
                maxExpandedBytes = limits.maxArchiveExpandedBytes,
                deadlineNanos = startNanos + limits.maxExtractionMillis * 1_000_000L,
            )
    }
}

/**
 * Raised by [LimitedInputStream] once an input exceeds its byte budget,
 * *before* the full input has been consumed.
 */
class ExtractionInputLimitException(message: String) : IOException(message)

/**
 * An [InputStream] that only ever pulls at most [maxBytes] from [delegate];
 * any further byte aborts with [ExtractionInputLimitException]. This keeps
 * extraction bounded even when the size reported by the storage provider is
 * `-1` (unknown) or lies.
 */
class LimitedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {
    private var readCount: Long = 0

    override fun read(): Int {
        if (readCount >= maxBytes) {
            val b = delegate.read()
            if (b >= 0) {
                readCount++
                throw ExtractionInputLimitException("input exceeded limit of $maxBytes bytes")
            }
            return -1
        }
        val b = delegate.read()
        if (b >= 0) readCount++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val remaining = maxBytes - readCount
        if (remaining <= 0) return read()
        val allowed = minOf(len.toLong(), remaining).toInt()
        val n = delegate.read(b, off, allowed)
        if (n > 0) readCount += n
        return n
    }

    override fun close() = delegate.close()
}