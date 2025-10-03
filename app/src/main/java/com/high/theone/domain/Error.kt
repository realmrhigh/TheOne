package com.high.theone.domain

/**
 * Generic error class for domain operations.
 * Provides a consistent error handling mechanism across the application.
 */
data class Error(
    val message: String,
    val code: String? = null,
    val cause: Throwable? = null
) {
    companion object {
        fun fromThrowable(throwable: Throwable): Error {
            return Error(
                message = throwable.message ?: "Unknown error",
                cause = throwable
            )
        }
        
        fun notFound(resource: String): Error {
            return Error(
                message = "$resource not found",
                code = "NOT_FOUND"
            )
        }
        
        fun invalidInput(message: String): Error {
            return Error(
                message = message,
                code = "INVALID_INPUT"
            )
        }
        
        fun fileSystem(message: String): Error {
            return Error(
                message = message,
                code = "FILE_SYSTEM_ERROR"
            )
        }
        
        fun permission(message: String): Error {
            return Error(
                message = message,
                code = "PERMISSION_DENIED"
            )
        }
    }
}