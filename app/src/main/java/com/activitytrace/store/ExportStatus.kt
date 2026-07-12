package com.activitytrace.store

sealed class ExportStatus {
    data class Progress(val message: String) : ExportStatus()
    data class Success(val message: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
    data class Info(val message: String) : ExportStatus()
}
