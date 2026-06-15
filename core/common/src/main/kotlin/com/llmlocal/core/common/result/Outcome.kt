package com.llmlocal.core.common.result

/**
 * Lightweight [Result]-like wrapper, but with stable equality (no stack trace
 * captured by [Throwable]) and a Kotlin-native sealed hierarchy so it can be
 * pattern-matched.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: Throwable) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun exceptionOrNull(): Throwable? = (this as? Failure)?.error

    fun isSuccess(): Boolean = this is Success

    fun isFailure(): Boolean = this is Failure
}

inline fun <T, R> Outcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R,
): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}

inline fun <T> Outcome<T>.getOrElse(default: (Throwable) -> T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> default(error)
}

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(t)
}
