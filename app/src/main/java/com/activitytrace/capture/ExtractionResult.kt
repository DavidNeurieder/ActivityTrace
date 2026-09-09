package com.activitytrace.capture

sealed interface ExtractionResult {
    data class Success(val text: String) : ExtractionResult
    data class Truncated(val text: String) : ExtractionResult
    data object Unsupported : ExtractionResult
    data object TooLarge : ExtractionResult
    data object Invalid : ExtractionResult
    data object Cancelled : ExtractionResult
    data object DeadlineExceeded : ExtractionResult
    data class Failed(val cause: Exception) : ExtractionResult
}