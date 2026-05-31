package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

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

    suspend fun indexDirectory(context: Context, treeUri: Uri, dao: CaptureDao): Int {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val total = indexDocumentFile(context, dir, dao)
        Log.d(TAG, "Indexed $total files from $treeUri")
        return total
    }

    private suspend fun indexDocumentFile(context: Context, file: DocumentFile, dao: CaptureDao): Int {
        if (file.isDirectory) {
            var count = 0
            val children = file.listFiles()
            Log.d(TAG, "Scanning directory '${file.name}' (${children.size} children)")
            for (child in children) {
                count += indexDocumentFile(context, child, dao)
            }
            return count
        }

        val uri = file.uri.toString()
        val fileName = file.name ?: uri.substringAfterLast("/")
        if (dao.countByMetadata(uri) > 0) {
            Log.d(TAG, "Skipping (already indexed): $fileName")
            return 0
        }

        val mimeType = resolveMimeType(file)
        if (mimeType == null) {
            Log.d(TAG, "Skipping (unsupported type): $fileName")
            return 0
        }

        val text = extractText(context, file, mimeType) ?: run {
            Log.d(TAG, "Skipping (no text extracted): $fileName")
            return 0
        }
        if (text.isBlank()) {
            Log.d(TAG, "Skipping (blank): $fileName")
            return 0
        }

        Log.d(TAG, "Indexing: $fileName ($mimeType, ${text.length} chars)")
        CaptureIngestor.ingest(
            text = text,
            appPackage = "local",
            appName = fileName,
            contentType = "page",
            category = mimeType,
            metadata = uri,
        )
        return 1
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

    private fun extractText(context: Context, file: DocumentFile, mimeType: String): String? = try {
        when {
            mimeType.startsWith("text/") -> extractPlainText(context, file.uri)
            mimeType == "application/pdf" -> extractPdfText(context, file.uri)
            mimeType.startsWith("image/") -> file.name
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun extractPlainText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }
    } catch (_: Exception) {
        null
    }

    private fun extractPdfText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }
    } catch (_: Exception) {
        null
    }
}
