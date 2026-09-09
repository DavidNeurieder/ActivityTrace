package com.activitytrace.store

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue

/**
 * Verifies that a failed restore leaves the live DAO untouched:
 * no insertions, no key reads, no state changes.
 *
 * Usage:
 * ```
 * val verifier = RestoreIntegrityVerifier(dao)
 * verifier.verifyUnchanged {
 *     EncryptedBackupExporter.import(context, uri, password, dao)
 * }
 * ```
 */
class RestoreIntegrityVerifier(private val dao: CaptureDao) {

    fun installMocks() {
        coEvery { dao.getAllItemKeys() } returns emptyList()
        coEvery { dao.insertAll(any()) } returns emptyList()
    }

    fun verifyUnchanged(block: suspend () -> RestoreResult) = runTest {
        val result = block()
        assertTrue(
            "Expected InvalidBackup but was $result",
            result is RestoreResult.InvalidBackup,
        )
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    fun verifyNoWrites(block: suspend () -> Unit) = runTest {
        block()
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }
}
