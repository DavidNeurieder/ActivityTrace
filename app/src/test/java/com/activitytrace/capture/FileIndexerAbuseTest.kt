package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
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
class FileIndexerAbuseTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val dao = mockk<CaptureDao>(relaxed = true)

    private fun pdfFile(name: String, data: ByteArray): DocumentFile {
        val uri = Uri.parse("content://files/$name")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(data))
        val file = mockk<DocumentFile>()
        every { file.isDirectory } returns false
        every { file.name } returns name
        every { file.uri } returns uri
        every { file.type } returns "application/pdf"
        every { file.length() } returns data.size.toLong()
        return file
    }

    private fun dir(vararg children: DocumentFile): DocumentFile {
        val file = mockk<DocumentFile>()
        every { file.isDirectory } returns true
        every { file.name } returns "dir"
        every { file.listFiles() } returns arrayOf(*children)
        return file
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

    private fun encryptedPdfBytes(): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("secret")
                cs.endText()
            }
            val policy = StandardProtectionPolicy("ownerpass", "userpass", AccessPermission())
            policy.encryptionKeyLength = 128
            policy.setPreferAES(true)
            doc.protect(policy)
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    @Test
    fun `malformed pdf is rejected without indexing anything`() = runTest {
        val file = pdfFile("broken.pdf", "this is definitely not a pdf document".toByteArray())
        val budget = IndexBudget(IndexLimits())

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `truncated pdf is rejected without crashing`() = runTest {
        val full = pdfBytes(pageCount = 3)
        val truncated = full.copyOf(20)
        val file = pdfFile("truncated.pdf", truncated)
        val budget = IndexBudget(IndexLimits())

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `binary file renamed to pdf is rejected`() = runTest {
        val bytes = ByteArray(256) { (it * 31 + 7).toByte() }
        val file = pdfFile("fake.pdf", bytes)
        val budget = IndexBudget(IndexLimits())

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `empty pdf is not indexed`() = runTest {
        val file = pdfFile("empty.pdf", pdfBytes(pageCount = 0))
        val budget = IndexBudget(IndexLimits())

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `pdf page count above limit is rejected as too large`() = runTest {
        val file = pdfFile("many.pdf", pdfBytes(pageCount = 30))
        val budget = IndexBudget(IndexLimits(maxPdfPages = 5))

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(maxPdfPages = 5), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `pdf extracted characters are bounded by the budget`() = runTest {
        val pageText = "x".repeat(4096)
        val file = pdfFile("wordy.pdf", pdfBytes(pageCount = 3, text = pageText))
        val limits = IndexLimits(maxExtractedCharacters = 10_000)

        val result = FileIndexer.extractPdfText(context, file.uri, ExtractionBudget.from(limits))

        assertTrue(
            "expected success or truncation, was $result",
            result is ExtractionResult.Success || result is ExtractionResult.Truncated,
        )
        val text = (result as? ExtractionResult.Success)?.text
            ?: (result as ExtractionResult.Truncated).text
        assertTrue(text.length <= limits.maxExtractedCharacters)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `encrypted pdf is rejected without capturing its content`() = runTest {
        val file = pdfFile("locked.pdf", encryptedPdfBytes())
        val budget = IndexBudget(IndexLimits())

        val result = FileIndexer.extractPdfText(context, file.uri, ExtractionBudget.from(IndexLimits()))

        assertTrue(result == ExtractionResult.Unsupported || result == ExtractionResult.Invalid)

        FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), budget, 0)
        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `cancellation stops indexing cooperatively`() = runTest {
        val file = pdfFile("cancel.pdf", pdfBytes(pageCount = 3))
        val job = Job()
        job.cancel()

        val error = runCatching {
            withContext(job) {
                FileIndexer.indexDocumentFile(context, dir(file), dao, IndexLimits(), IndexBudget(IndexLimits()), 0)
            }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }
}