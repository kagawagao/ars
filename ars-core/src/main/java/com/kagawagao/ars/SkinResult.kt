package com.kagawagao.ars

/**
 * Result wrapper for all skin operations.
 *
 * Every public API returns a [SkinResult] — the framework never throws
 * from expected runtime conditions. Use `when` for exhaustive handling.
 *
 * @param T The success value type.
 */
sealed class SkinResult<out T> {
    /**
     * Operation completed successfully.
     *
     * @property value The result value.
     */
    data class Success<T>(val value: T) : SkinResult<T>()

    /**
     * Operation failed.
     *
     * @property error The [SkinError] describing the failure.
     */
    data class Error(val error: SkinError) : SkinResult<Nothing>()

    /**
     * Returns `true` if this is a [Success].
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns `true` if this is an [Error].
     */
    val isError: Boolean get() = this is Error

    /**
     * Returns the success value, or `null` if this is an [Error].
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Error -> null
    }

    /**
     * Returns the success value, or throws an exception if this is an [Error].
     *
     * @throws IllegalStateException if this is an [Error].
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Error -> throw IllegalStateException("SkinResult is an error: ${error.message}", error.cause)
    }
}
