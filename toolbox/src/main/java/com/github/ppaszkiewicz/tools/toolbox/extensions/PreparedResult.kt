@file:Suppress("UNUSED", "UNCHECKED_CAST", "NOTHING_TO_INLINE")

package com.github.ppaszkiewicz.tools.toolbox.extensions

import kotlinx.coroutines.flow.*
import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Wrapped values that represent progress or final result.
 *
 * Provides utility extensions to handle it as value used in flows.
 *
 * @param P type of progress
 * @param R type of [Result]
 * */
// logic inspired by [Result] itself
@JvmInline
value class PreparedResult<out P, out R> @PublishedApi internal constructor(
    @PublishedApi
    internal val value: Any?
) : Serializable {
    val isProgress: Boolean get() = value !is Finished<*>
    val isFinished: Boolean get() = value is Finished<*>

    /** Get progress after checking [isProgress]. */
    val progress: P get() = value as P

    /** Get result after checking [isFinished]. */
    val result: Result<R> get() = (value as Finished<R>).result

    /** Get the progress value or null if this represents a [Result]. */
    inline fun progressOrNull(): P? =
        when {
            isProgress -> value as P
            else -> null
        }

    /** Get the [Result] or null if this represents progress. */
    inline fun resultOrNull(): Result<R>? =
        when (value) {
            isFinished -> result
            else -> null
        }

    override fun toString(): String =
        when (value) {
            is Finished<*> -> "Finished($value)"
            else -> "Progress($value)"
        }

    // value construction
    companion object {
        @JvmName("progress")
        inline fun <P, R> progress(progress: P): PreparedResult<P, R> = PreparedResult(progress)

        @JvmName("result")
        inline fun <P, R> result(value: Result<R>): PreparedResult<P, R> =
            PreparedResult(createFinished(value))

        @JvmName("success")
        inline fun <P, R> success(value: R): PreparedResult<P, R> =
            PreparedResult(createFinished(Result.success(value)))

        @JvmName("failure")
        inline fun <P, R> failure(error: Throwable): PreparedResult<P, R> =
            PreparedResult(createFinished<R>(Result.failure(error)))
    }

    /** Marker class to encapsulate non-progress result. */
    internal class Finished<R>(@JvmField val result: Result<R>) : Serializable {
        override fun equals(other: Any?): Boolean = other is Finished<*> && result == other.result
        override fun hashCode(): Int = result.hashCode()
        override fun toString(): String = "Finished($result)"
    }
}

/** Spawn internal marker class without exposing it. */
@PublishedApi
internal fun <R> createFinished(result: Result<R>): Any = PreparedResult.Finished(result)

/** Perform [action] if this represents progress. Returns self. */
@OptIn(ExperimentalContracts::class)
inline fun <P, R> PreparedResult<P, R>.onProgress(action: (progress: P) -> Unit): PreparedResult<P, R> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    progressOrNull()?.let { action(it) }
    return this
}

/** Perform [action] if this represents result. Returns self. */
@OptIn(ExperimentalContracts::class)
inline fun <P, R> PreparedResult<P, R>.onResult(action: (result: Result<R>) -> Unit): PreparedResult<P, R> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    resultOrNull()?.let { action(it) }
    return this
}

/**
 * Execute one of the actions depending on what this [PreparedResult] represents.
 *
 * This will rethrow any exceptions thrown by them.
 * */
@OptIn(ExperimentalContracts::class)
inline fun <P, R, T> PreparedResult<P, R>.fold(
    onProgress: (progress: P) -> T,
    onSuccess: (value: R) -> T,
    onFailure: (exception: Throwable) -> T
): T {
    contract {
        callsInPlace(onProgress, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (val result = resultOrNull()) {
        null -> onProgress(value as P)
        else -> result.fold(onSuccess, onFailure)
    }
}

/**
 * Execute one of the actions depending on what this [PreparedResult] represents.
 *
 * If result represents failure this will rethrow the exception.
 * */
@OptIn(ExperimentalContracts::class)
inline fun <P, R, T> PreparedResult<P, R>.fold(
    onProgress: (progress: P) -> T,
    onSuccess: (value: R) -> T
): T {
    contract {
        callsInPlace(onProgress, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    }
    return when (val result = resultOrNull()) {
        null -> onProgress(progress)
        else -> onSuccess(result.getOrThrow())
    }
}

/** Fold each [PreparedResult] into single value to flow down. */
fun <P, V, T> Flow<PreparedResult<P, V>>.foldPreparedResult(
    onProgress: (progress: P) -> T,
    onSuccess: (value: V) -> T,
    onFailure: (exception: Throwable) -> T
) = map { it.fold(onProgress, onSuccess, onFailure) }

/** Fold each [PreparedResult] into single value to flow down. */
inline fun <P, V, T> Flow<PreparedResult<P, V>>.foldPreparedResult(
    crossinline onProgress: (progress: P) -> T,
    crossinline onResult: (result: V) -> T
) = map { it.fold(onProgress, onResult) }

/** Flow of [PreparedResult] values. Types can be inferred by using [emitProgress] and [emitSuccess]. **/
fun <P, R> resultFlow(block: suspend FlowCollector<PreparedResult<P, R>>.() -> Unit): Flow<PreparedResult<P, R>> =
    flow(block)

/** [transform] incoming flow into flow of [PreparedResult]. Types can be inferred by using [emitProgress] and [emitSuccess]. */
fun <T, P, R> Flow<T>.toResultFlow(block: suspend FlowCollector<PreparedResult<P, R>>.(T) -> Unit): Flow<PreparedResult<P, R>> =
    transform(block)

/**
 * Collect flow of [PreparedResult] values by invoking [handleProgress] on all progress values
 * while flowing down all [Result] values.
 * */
fun <P, R> Flow<PreparedResult<P, R>>.filterProgress(handleProgress: suspend (P) -> Unit) =
    transform {
        when (val result = it.resultOrNull()) {
            null -> handleProgress(it.progress)
            else -> emit(result)
        }
    }

/**
 * Shorthand for [filterProgress] -> [first].
 *
 * Await and return first result in this flow while handling the progress emitted before it.
 * */
suspend fun <P, R> Flow<PreparedResult<P, R>>.filterProgressUntilResult(handleProgress: suspend (P) -> Unit) =
    filterProgress(handleProgress).first()

/**
 * Filter out progress values and only emits [Result] values.
 */
fun <P, R> Flow<PreparedResult<P, R>>.filterProgress() = filter { it.isFinished }

/** Shorthand for [filterProgress] -> [first]. */
suspend fun <P, R> Flow<PreparedResult<P, R>>.firstResult() = filterProgress().first()

/** Emit progress for flows emitting [PreparedResult]. */
suspend fun <P, R> FlowCollector<PreparedResult<P, R>>.emitProgress(progress: P) =
    emit(PreparedResult.progress(progress))

/** Emit a result for flows emitting [PreparedResult]. */
suspend fun <P, R> FlowCollector<PreparedResult<P, R>>.emitResult(result: Result<R>) =
    emit(PreparedResult.result(result))

/** Emit successful result for flows emitting [PreparedResult]. */
suspend fun <P, R> FlowCollector<PreparedResult<P, R>>.emitSuccess(result: R) =
    emit(PreparedResult.success(result))

/** Emit failed result for flows emitting [PreparedResult]. */
suspend fun <P, R> FlowCollector<PreparedResult<P, R>>.emitFailure(error: Throwable) =
    emit(PreparedResult.failure(error))