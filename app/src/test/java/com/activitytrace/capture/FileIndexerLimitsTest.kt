package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class FileIndexerLimitsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val dao = mockk<CaptureDao>(relaxed = true)

    private fun textFile(name: String, size: Long, content: String): DocumentFile {
        val uri = Uri.parse("content://files/$name")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(content.toByteArray()))
        val file = mockk<DocumentFile>()
        every { file.isDirectory } returns false
        every { file.name } returns name
        every { file.uri } returns uri
        every { file.type } returns "text/plain"
        every { file.length() } returns size
        return file
    }

    private fun dir(vararg children: DocumentFile): DocumentFile {
        val file = mockk<DocumentFile>()
        every { file.isDirectory } returns true
        every { file.name } returns "dir"
        every { file.listFiles() } returns arrayOf(*children)
        return file
    }

    @Test
    fun `max files enforced during traversal`() = runTest {
        val root = dir(textFile("a.txt", 10, "content a"), textFile("b.txt", 10, "content b"))
        val budget = IndexBudget(IndexLimits(maxFiles = 1))

        FileIndexer.indexDocumentFile(context, root, dao, IndexLimits(maxFiles = 1), budget, 0)

        assertEquals(1, budget.filesIndexed)
    }

    @Test
    fun `max total bytes enforced during traversal`() = runTest {
        val root = dir(textFile("a.txt", 60, "aaaa"), textFile("b.txt", 60, "bbbb"))
        val budget = IndexBudget(IndexLimits(maxTotalBytes = 100))

        FileIndexer.indexDocumentFile(context, root, dao, IndexLimits(maxTotalBytes = 100), budget, 0)

        assertEquals(1, budget.filesIndexed)
    }

    @Test
    fun `max depth enforced stops descending into nested directories`() = runTest {
        val deep = dir(dir(textFile("deep.txt", 10, "deep content")))
        val budget = IndexBudget(IndexLimits(maxDepth = 1))

        FileIndexer.indexDocumentFile(context, deep, dao, IndexLimits(maxDepth = 1), budget, 0)

        assertEquals(0, budget.filesIndexed)
    }

    @Test
    fun `already indexed files are skipped and not recorded`() = runTest {
        val uri = Uri.parse("content://files/known.txt")
        val known = mockk<DocumentFile>()
        every { known.isDirectory } returns false
        every { known.name } returns "known.txt"
        every { known.uri } returns uri
        every { known.type } returns "text/plain"
        every { known.length() } returns 10L

        coEvery { dao.countByMetadata(uri.toString()) } returns 1
        val root = dir(known, textFile("new.txt", 10, "new content"))
        val budget = IndexBudget(IndexLimits())

        FileIndexer.indexDocumentFile(context, root, dao, IndexLimits(), budget, 0)

        assertEquals(1, budget.filesIndexed)
    }

    @Test
    fun `restart resumes after a partially completed run`() = runTest {
        val aUri = Uri.parse("content://files/a.txt")
        val bUri = Uri.parse("content://files/b.txt")
        shadowOf(context.contentResolver)
            .registerInputStream(aUri, ByteArrayInputStream("content a".toByteArray()))
        val aFile = mockk<DocumentFile>()
        every { aFile.isDirectory } returns false
        every { aFile.name } returns "a.txt"
        every { aFile.uri } returns aUri
        every { aFile.type } returns "text/plain"
        every { aFile.length() } returns 10L
        val bFile = textFile("b.txt", 10, "content b")
        val root = dir(aFile, bFile)

        coEvery { dao.countByMetadata(any()) } returns 0
        val first = IndexBudget(IndexLimits(maxFiles = 1))
        FileIndexer.indexDocumentFile(context, root, dao, IndexLimits(maxFiles = 1), first, 0)
        assertEquals(1, first.filesIndexed)

        coEvery { dao.countByMetadata(aUri.toString()) } returns 1
        val second = IndexBudget(IndexLimits(maxFiles = 2))
        FileIndexer.indexDocumentFile(context, root, dao, IndexLimits(maxFiles = 2), second, 0)
        assertEquals(1, second.filesIndexed)
    }
}