package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

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

        val text = extractText(context, file, mimeType, limits.maxExtractedCharacters) ?: run {
            Log.d(TAG, "Skipping (no text extracted): $fileName")
            return
        }
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

    private fun extractText(context: Context, file: DocumentFile, mimeType: String, maxChars: Int): String? = try {
        when {
            mimeType.startsWith("text/") -> extractPlainText(context, file.uri, maxChars)
            mimeType == "application/pdf" -> extractPdfText(context, file.uri)
            mimeType.startsWith("image/") -> file.name
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    private fun extractPlainText(context: Context, uri: Uri, maxChars: Int): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { reader ->
                val sb = StringBuilder(minOf(maxChars, 8192))
                val buf = CharArray(4096)
                var total = 0
                var n: Int
                while (reader.read(buf).also { n = it } != -1 && total < maxChars) {
                    val toAppend = minOf(n, maxChars - total)
                    sb.append(buf, 0, toAppend)
                    total += toAppend
                }
                sb.toString()
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun extractPdfText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }
    } catch (_: Throwable) {
        null
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
