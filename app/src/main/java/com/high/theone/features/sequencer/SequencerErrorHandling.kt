package com.high.theone.features.sequencer

/**
 * Utility functions for creating common sequencer errors
 */
object SequencerErrors {

    fun patternValidationError(
        message: String,
        patternId: String? = null,
        validationErrors: List<String> = emptyList()
    ): SequencerError {
        return SequencerError(
            type = ErrorType.PATTERN_LOADING_ERROR,
            severity = ErrorSeverity.MEDIUM,
            message = message,
            context = mapOf(
                "patternId" to (patternId ?: "unknown"),
                "validationErrors" to validationErrors
            ),
            timestamp = System.currentTimeMillis()
        )
    }
}