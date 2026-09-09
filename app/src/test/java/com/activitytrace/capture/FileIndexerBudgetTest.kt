package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class FileIndexerBudgetTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val dao = mockk<CaptureDao>(relaxed = true)

    private fun budget(
        maxInputBytes: Long = 1024,
        maxOutputChars: Int = 1_000_000,
        maxPages: Int = 100,
        deadlineNanos: Long = Long.MAX_VALUE,
        nowNanos: () -> Long = { 0L },
    ) = ExtractionBudget(
        maxInputBytes = maxInputBytes,
        maxOutputChars = maxOutputChars,
        maxPages = maxPages,
        maxEntries = 10,
        maxExpandedBytes = 4096,
        deadlineNanos = deadlineNanos,
        nowNanos = nowNanos,
    )

    private fun registerText(content: String): Uri {
        val uri = Uri.parse("content://budget/$content.length.txt")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(content.toByteArray()))
        return uri
    }

    private fun pdfBytes(pageCount: Int, text: String = "Hello world "): ByteArray {
        PDDocument().use { doc ->
            repeat(pageCount) {
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText(text)
                    cs.endText()
                }
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun registerTextSource(name: String, data: ByteArray): Uri {
        val uri = Uri.parse("content://budget/$name")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(data))
        return uri
    }

    // --- ExtractionBudget unit behaviour ------------------------------------

    @Test
    fun `budget rejects oversized input before any parsing`() {
        assertTrue(budget(maxInputBytes = 10).exceedsInput(11))
        assertTrue(!budget(maxInputBytes = 10).exceedsInput(10))
    }

    @Test
    fun `budget rejects too many pages`() {
        assertTrue(budget(maxPages = 5).exceedsPages(6))
        assertTrue(!budget(maxPages = 5).exceedsPages(5))
    }

    @Test
    fun `budget reports past deadline only once the clock passes it`() {
        var now = 0L
        val b = budget(deadlineNanos = 100) { now }
        assertTrue(!b.isPastDeadline())
        now = 100
        assertTrue(b.isPastDeadline())
    }

    @Test
    fun `budget maps the IndexLimits configuration`() {
        val limits = IndexLimits(maxFileBytes = 2048, maxExtractedCharacters = 123, maxPdfPages = 7)
        val fromLimits = ExtractionBudget.from(limits, startNanos = System.nanoTime())

        assertEquals(2048L, fromLimits.maxInputBytes)
        assertEquals(123, fromLimits.maxOutputChars)
        assertEquals(7, fromLimits.maxPages)
    }

    // --- LimitedInputStream input bound -------------------------------------

    @Test
    fun `input that ends exactly at the limit is allowed`() {
        val data = "exact".toByteArray()
        val limited = LimitedInputStream(ByteArrayInputStream(data), data.size.toLong())
        val out = limited.readBytes()
        assertEquals("exact", out.toString(Charsets.UTF_8))
    }

    @Test
    fun `input beyond the limit throws and is not fully consumed`() {
        val data = ByteArray(10) { 'a'.code.toByte() }
        val limited = LimitedInputStream(ByteArrayInputStream(data), 4)

        val error = runCatching { limited.readBytes() }.exceptionOrNull()

        assertTrue("expected an input-limit error, got $error", error is ExtractionInputLimitException)
    }

    // --- Plain text extraction -------------------------------------------------

    @Test
    fun `plain text is truncated instead of unbounded at the output budget`() = runTest {
        val uri = registerText("x".repeat(1000))

        val result = FileIndexer.extractPlainText(
            context,
            uri,
            budget(maxOutputChars = 10),
            knownSize = 1000L,
        )

        assertTrue("expected truncation, was $result", result is ExtractionResult.Truncated)
        assertEquals(10, (result as ExtractionResult.Truncated).text.length)
    }

    @Test
    fun `plain text that ends exactly at the output budget is a success`() = runTest {
        val uri = registerText("0123456789")

        val result = FileIndexer.extractPlainText(
            context,
            uri,
            budget(maxOutputChars = 10),
            knownSize = 10L,
        )

        assertTrue("expected success, was $result", result is ExtractionResult.Success)
        assertEquals(10, (result as ExtractionResult.Success).text.length)
    }

    @Test
    fun `oversized plain text is rejected as too large even when its reported length is hidden`() = runTest {
        val uri = registerText("y".repeat(50_000))

        val result = FileIndexer.extractPlainText(context, uri, budget(maxInputBytes = 100))

        assertEquals(ExtractionResult.TooLarge, result)
    }

    @Test
    fun `plain text stops when the deadline passes`() = runTest {
        val uri = registerText("z".repeat(500))

        val result = FileIndexer.extractPlainText(
            context,
            uri,
            budget(deadlineNanos = 0) { 1L },
        )

        assertEquals(ExtractionResult.DeadlineExceeded, result)
    }

    @Test
    fun `plain text extraction cooperates with cancellation`() = runTest {
        val uri = registerText("w".repeat(500))
        val job = Job().also { it.cancel() }

        val outcome = runCatching {
            withContext(job) { FileIndexer.extractPlainText(context, uri, budget()) }
        }

        if (outcome.isSuccess) {
            assertEquals(ExtractionResult.Cancelled, outcome.getOrNull())
        } else {
            assertTrue("expected cancellation, was $outcome", outcome.exceptionOrNull() is CancellationException)
        }
    }

    // --- PDF extraction ---------------------------------------------------------

    @Test
    fun `oversized pdf is rejected as too large`() = runTest {
        val data = pdfBytes(pageCount = 1)
        val uri = registerTextSource("big.pdf", data)

        val result = FileIndexer.extractPdfText(context, uri, budget(maxInputBytes = 100))

        assertEquals(ExtractionResult.TooLarge, result)
    }

    @Test
    fun `pdf with more pages than the budget is rejected as too large`() = runTest {
        val uri = registerTextSource("pages.pdf", pdfBytes(pageCount = 3))

        val result = FileIndexer.extractPdfText(context, uri, budget(maxPages = 2))

        assertEquals(ExtractionResult.TooLarge, result)
    }

    @Test
    fun `pdf extraction stops when the deadline passes`() = runTest {
        val uri = registerTextSource("timed.pdf", pdfBytes(pageCount = 2, text = "page text "))

        val result = FileIndexer.extractPdfText(
            context,
            uri,
            budget(maxInputBytes = 1_000_000, deadlineNanos = 0) { 1L },
        )

        assertEquals(ExtractionResult.DeadlineExceeded, result)
    }

    @Test
    fun `pdf extraction truncates at the output budget`() = runTest {
        val uri = registerTextSource("wordy.pdf", pdfBytes(pageCount = 2, text = "x".repeat(2048)))

        val result = FileIndexer.extractPdfText(
            context,
            uri,
            budget(maxInputBytes = 1_000_000, maxOutputChars = 10),
        )

        assertTrue("expected truncation, was $result", result is ExtractionResult.Truncated)
        assertEquals(10, (result as ExtractionResult.Truncated).text.length)
    }

    // --- indexDocumentFile end to end ------------------------------------------

    @Test
    fun `the indexer skips oversized files through the stream budget without indexing`() = runTest {
        val uri = Uri.parse("content://budget/oversize.txt")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(ByteArray(10_000) { 'a'.code.toByte() }))
        val file = mockk<androidx.documentfile.provider.DocumentFile>()
        every { file.isDirectory } returns false
        every { file.name } returns "oversize.txt"
        every { file.uri } returns uri
        every { file.type } returns "text/plain"
        every { file.length() } returns -1L
        val dir = mockk<androidx.documentfile.provider.DocumentFile>()
        every { dir.isDirectory } returns true
        every { dir.name } returns "dir"
        every { dir.listFiles() } returns arrayOf(file)
        coEvery { dao.countByMetadata(any()) } returns 0
        val crawlBudget = IndexBudget(IndexLimits(maxFileBytes = 512))

        FileIndexer.indexDocumentFile(
            context,
            dir,
            dao,
            IndexLimits(maxFileBytes = 512),
            crawlBudget,
            0,
        )

        assertEquals(0, crawlBudget.filesIndexed)
    }

    @Test
    fun `the indexer indexes truncated text and counts the file`() = runTest {
        val uri = Uri.parse("content://budget/trunc.txt")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(("k".repeat(5000)).toByteArray()))
        val file = mockk<androidx.documentfile.provider.DocumentFile>()
        every { file.isDirectory } returns false
        every { file.name } returns "trunc.txt"
        every { file.uri } returns uri
        every { file.type } returns "text/plain"
        every { file.length() } returns 5000L
        val dir = mockk<androidx.documentfile.provider.DocumentFile>()
        every { dir.isDirectory } returns true
        every { dir.name } returns "dir"
        every { dir.listFiles() } returns arrayOf(file)
        coEvery { dao.countByMetadata(any()) } returns 0
        val crawlBudget = IndexBudget(IndexLimits(maxExtractedCharacters = 100))

        FileIndexer.indexDocumentFile(
            context,
            dir,
            dao,
            IndexLimits(maxExtractedCharacters = 100),
            crawlBudget,
            0,
        )

        assertEquals(1, crawlBudget.filesIndexed)
    }
}