package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.activitytrace.store.CaptureDao
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object FileIndexer {

    suspend fun indexDirectory(context: Context, treeUri: Uri, dao: CaptureDao): Int {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        return indexDocumentFile(context, dir, dao)
    }

    private suspend fun indexDocumentFile(context: Context, file: DocumentFile, dao: CaptureDao): Int {
        if (file.isDirectory) {
            var count = 0
            for (child in file.listFiles()) {
                count += indexDocumentFile(context, child, dao)
            }
            return count
        }

        val uri = file.uri.toString()
        if (dao.countByMetadata(uri) > 0) return 0

        val mimeType = file.type ?: return 0
        if (!mimeType.startsWith("text/") && mimeType != "application/pdf") return 0

        val text = extractText(context, file) ?: return 0
        if (text.isBlank() || text.length < 20) return 0

        CaptureIngestor.ingest(text, "local", "page", metadata = uri)
        return 1
    }

    private fun extractText(context: Context, file: DocumentFile): String? = try {
        val uri = file.uri
        when {
            file.type?.startsWith("text/") == true -> extractPlainText(context, uri)
            file.type == "application/pdf" -> extractPdfText(context, uri)
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
