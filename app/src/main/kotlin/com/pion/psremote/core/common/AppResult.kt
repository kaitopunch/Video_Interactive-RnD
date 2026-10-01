package com.pion.psremote.core.common

/**
 * The result of an operation that is allowed to fail in a way the user can be told about.
 *
 * Every repository returns this rather than throwing, so `launchSafely`'s catch block is a floor
 * for the unexpected — not the normal error path (MVI doc §1).
 */
sealed interface AppResult<out T> {

    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}
