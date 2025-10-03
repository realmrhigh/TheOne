package com.high.theone.domain

/**
 * A generic Result type for handling success and failure cases in domain operations.
 * This provides a type-safe way to handle operations that can fail.
 */
sealed class Result<out T, out E> {
    /**
     * Represents a successful operation with a value of type T.
     */
    data class Success<out T>(val value: T) : Result<T, Nothing>()
    
    /**
     * Represents a failed operation with an error of type E.
     */
    data class Failure<out E>(val error: E) : Result<Nothing, E>()
    
    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Returns true if this is a Failure result.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Returns the value if this is a Success, or null if this is a Failure.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    /**
     * Returns the error if this is a Failure, or null if this is a Success.
     */
    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }
    
    /**
     * Maps the success value using the provided transform function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    
    /**
     * Maps the error using the provided transform function.
     */
    inline fun <F> mapError(transform: (E) -> F): Result<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }
    
    /**
     * Flat maps the success value using the provided transform function.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
}