package com.llmlocal.core.common.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Wraps each emission of the source flow in a [Result], capturing any
 * exception as a failure. The flow does *not* terminate on a single failure
 * unless the upstream flow terminates; use [kotlinx.coroutines.flow.Flow.catch]
 * with a `throw` to re-throw.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> = map { Result.success(it) }
    .catch { emit(Result.failure(it)) }
