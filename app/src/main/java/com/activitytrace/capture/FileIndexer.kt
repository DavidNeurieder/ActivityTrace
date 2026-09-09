package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.IOException

object FileIndexer {
    private const val TAG = "FileIndexer"

    private val extensionToMime = mapOf(
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "text" to "text/plain",
        "md" to "text/plain",
        "csv" to "text/plain",
        "json" to "text/plain",
        "xml" to "text/plain",
        "log" to "text/plain",
        "ini" to "text/plain",
        "cfg" to "text/plain",
        "yaml" to "text/plain",
        "yml" to "text/plain",
        "toml" to "text/plain",
        "conf" to "text/plain",
        "properties" to "text/plain",
        "sh" to "text/plain",
        "bat" to "text/plain",
        "sql" to "text/plain",
        "html" to "text/plain",
        "htm" to "text/plain",
        "css" to "text/plain",
        "js" to "text/plain",
        "py" to "text/plain",
        "rb" to "text/plain",
        "java" to "text/plain",
        "kt" to "text/plain",
        "kts" to "text/plain",
        "swift" to "text/plain",
        "c" to "text/plain",
        "cpp" to "text/plain",
        "h" to "text/plain",
        "hpp" to "text/plain",
        "rs" to "text/plain",
        "go" to "text/plain",
        "ts" to "text/plain",
        "tsx" to "text/plain",
        "jsx" to "text/plain",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
    )

    suspend fun indexDirectory(
        context: Context,
        treeUri: Uri,
        dao: CaptureDao,
        limits: IndexLimits = IndexLimits(),
    ): Int {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val budget = IndexBudget(limits)
        indexDocumentFile(context, dir, dao, limits, budget, 0)
        Log.d(TAG, "Indexed ${budget.filesIndexed} files from $treeUri")
        return budget.filesIndexed
    }

    internal suspend fun indexDocumentFile(
        context: Context,
        file: DocumentFile,
        dao: CaptureDao,
        limits: IndexLimits,
        budget: IndexBudget,
        depth: Int,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > limits.maxDepth) return
        if (file.isDirectory) {
            if (budget.exhausted) return
            val children = file.listFiles()
            Log.d(TAG, "Scanning directory '${file.name}' (${children.size} children)")
            for (child in children) {
                if (budget.exhausted) break
                indexDocumentFile(context, child, dao, limits, budget, depth + 1)
            }
            return
        }

        val uri = file.uri.toString()
        val fileName = file.name ?: uri.substringAfterLast("/")
        if (dao.countByMetadata(uri) > 0) {
            Log.d(TAG, "Skipping (already indexed): $fileName")
            return
        }

        val mimeType = resolveMimeType(file)
        if (mimeType == null) {
            Log.d(TAG, "Skipping (unsupported type): $fileName")
            return
        }

        val fileSize = file.length()
        if (!budget.canAccept(fileSize, limits)) return

        when (val result = extractText(context, file, mimeType, ExtractionBudget.from(limits))) {
            is ExtractionResult.Success -> {
                val text = result.text
                if (text.isBlank()) {
                    Log.d(TAG, "Skipping (blank): $fileName")
                    return
                }
                Log.d(TAG, "Indexing: $fileName ($mimeType, ${text.length} chars)")
                budget.accept(fileSize)
                CaptureIngestor.ingest(
                    text = text,
                    appPackage = "local",
                    appName = fileName,
                    contentType = "page",
                    category = mimeType,
                    metadata = uri,
                )
            }
            is ExtractionResult.Truncated -> {
                val text = result.text
                if (text.isBlank()) {
                    Log.d(TAG, "Skipping (blank): $fileName")
                    return
                }
                Log.d(TAG, "Indexing (truncated): $fileName ($mimeType, ${text.length} chars)")
                budget.accept(fileSize)
                CaptureIngestor.ingest(
                    text = text,
                    appPackage = "local",
                    appName = fileName,
                    contentType = "page",
                    category = mimeType,
                    metadata = uri,
                )
            }
            ExtractionResult.Unsupported -> Log.d(TAG, "Skipping (unsupported): $fileName")
            ExtractionResult.TooLarge -> Log.d(TAG, "Skipping (extraction too large): $fileName")
            ExtractionResult.Invalid -> Log.d(TAG, "Skipping (invalid content): $fileName")
            ExtractionResult.Cancelled -> return
            ExtractionResult.DeadlineExceeded -> Log.d(TAG, "Skipping (extraction deadline): $fileName")
            is ExtractionResult.Failed -> Log.w(TAG, "Skipping (extraction failed): $fileName", result.cause)
        }
    }

    private fun resolveMimeType(file: DocumentFile): String? {
        val providerType = file.type
        if (providerType != null && providerType != "application/octet-stream") {
            if (providerType.startsWith("text/") || providerType == "application/pdf" || providerType.startsWith("image/")) {
                return providerType
            }
        }
        val name = file.name ?: return null
        val ext = name.substringAfterLast('.', "").lowercase()
        return extensionToMime[ext]
    }

    private suspend fun extractText(
        context: Context,
        file: DocumentFile,
        mimeType: String,
        budget: ExtractionBudget,
    ): ExtractionResult {
        if (!currentCoroutineContext().isActive) return ExtractionResult.Cancelled
        val knownSize = file.length().takeIf { it > 0 }
        if (knownSize != null && budget.exceedsInput(knownSize)) return ExtractionResult.TooLarge
        return try {
            when {
                mimeType.startsWith("text/") -> extractPlainText(context, file.uri, budget, knownSize)
                mimeType == "application/pdf" -> extractPdfText(context, file.uri, budget)
                mimeType.startsWith("image/") -> ExtractionResult.Success(file.name ?: "")
                else -> ExtractionResult.Unsupported
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtractionInputLimitException) {
            ExtractionResult.TooLarge
        } catch (e: IOException) {
            ExtractionResult.Invalid
        } catch (e: IllegalArgumentException) {
            ExtractionResult.Invalid
        } catch (e: Exception) {
            ExtractionResult.Failed(e)
        }
    }

    internal suspend fun extractPlainText(
        context: Context,
        uri: Uri,
        budget: ExtractionBudget,
        knownSize: Long? = null,
    ): ExtractionResult {
        if (knownSize != null && budget.exceedsInput(knownSize)) {
            return ExtractionResult.TooLarge
        }
        return try {
            val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            ExtractionResult.Invalid
        } else {
            stream.use { raw ->
                LimitedInputStream(raw, budget.maxInputBytes).bufferedReader().use { reader ->
                    val sb = StringBuilder(minOf(budget.maxOutputChars, 8192))
                    val buf = CharArray(4096)
                    var total = 0
                    while (true) {
                        if (!currentCoroutineContext().isActive) return ExtractionResult.Cancelled
                        if (budget.isPastDeadline()) return ExtractionResult.DeadlineExceeded
                        val n = reader.read(buf)
                        if (n == -1) break
                        val room = budget.maxOutputChars - total
                        if (n >= room) {
                            sb.append(buf, 0, room)
                            total += room
                            val exhausted = knownSize != null && knownSize <= total
                            return if (exhausted) {
                                ExtractionResult.Success(sb.toString())
                            } else {
                                ExtractionResult.Truncated(sb.toString())
                            }
                        }
                        sb.append(buf, 0, n)
                        total += n
                    }
                    ExtractionResult.Success(sb.toString())
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ExtractionInputLimitException) {
        ExtractionResult.TooLarge
    } catch (e: IOException) {
        ExtractionResult.Invalid
    } catch (e: IllegalArgumentException) {
        ExtractionResult.Invalid
    }
    }

    internal suspend fun extractPdfText(
        context: Context,
        uri: Uri,
        budget: ExtractionBudget,
    ): ExtractionResult {
        if (!currentCoroutineContext().isActive) return ExtractionResult.Cancelled
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return ExtractionResult.Invalid
            stream.use { raw ->
                val input = LimitedInputStream(raw, budget.maxInputBytes)
                PDDocument.load(input).use { doc ->
                    if (!doc.isEncrypted) {
                        extractPdfPages(doc, budget)
                    } else {
                        ExtractionResult.Unsupported
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtractionInputLimitException) {
            ExtractionResult.TooLarge
        } catch (e: IOException) {
            ExtractionResult.Invalid
        } catch (e: IllegalArgumentException) {
            ExtractionResult.Invalid
        }
    }

    private suspend fun extractPdfPages(doc: PDDocument, budget: ExtractionBudget): ExtractionResult {
        val pageCount = doc.numberOfPages
        if (pageCount > budget.maxPages) return ExtractionResult.TooLarge
        if (pageCount <= 0) return ExtractionResult.Success("")

        val stripper = PDFTextStripper()
        val sb = StringBuilder(minOf(budget.maxOutputChars, 8192))
        var total = 0
        for (page in 1..pageCount) {
            if (!currentCoroutineContext().isActive) return ExtractionResult.Cancelled
            if (budget.isPastDeadline()) return ExtractionResult.DeadlineExceeded
            stripper.setStartPage(page)
            stripper.setEndPage(page)
            val text = stripper.getText(doc)
            val remaining = budget.maxOutputChars - total
            if (text.length >= remaining) {
                sb.append(text, 0, remaining)
                total += remaining
                return if (page == pageCount && text.length == remaining) {
                    ExtractionResult.Success(sb.toString())
                } else {
                    ExtractionResult.Truncated(sb.toString())
                }
            }
            sb.append(text)
            total += text.length
        }
        return ExtractionResult.Success(sb.toString())
    }
}

class IndexBudget(private val limits: IndexLimits) {
    var filesIndexed: Int = 0
        private set
    var bytesIndexed: Long = 0L
        private set

    val exhausted: Boolean
        get() = filesIndexed >= limits.maxFiles || bytesIndexed >= limits.maxTotalBytes

    fun canAccept(fileSize: Long, limits: IndexLimits): Boolean {
        if (exhausted) return false
        if (fileSize > 0 && fileSize > limits.maxFileBytes) return false
        return bytesIndexed + fileSize <= limits.maxTotalBytes
    }

    fun accept(fileSize: Long) {
        filesIndexed++
        bytesIndexed += fileSize
    }
}