@file:Suppress("UNUSED", "UNCHECKED_CAST", "NOTHING_TO_INLINE")

package com.github.ppaszkiewicz.tools.toolbox.extensions

import kotlinx.coroutines.flow.*
import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents either a value or progress towards preparing it.
 *
 * @param P type of progress
 * @param V type of value
 * */
// logic inspired by [Result]
@JvmInline
value class PreparedValue<out P, out V> @PublishedApi internal constructor(
    @PublishedApi
    internal val preparedValueField: Any?
) : Serializable {
    val isProgress: Boolean get() = preparedValueField !is Finished<*>
    val isValue: Boolean get() = preparedValueField is Finished<*>

    @PublishedApi
    internal val progress: P
        get() = preparedValueField as P

    @PublishedApi
    internal val value: V
        get() = (preparedValueField as Finished<V>).value

    /** Get progress after checking [isProgress]. */
    fun progressOrThrow(): P {
        check(isProgress)
        return preparedValueField as P
    }

    /** Get value after checking [isValue]. */
    fun valueOrThrow(): V {
        check(isValue)
        return (preparedValueField as Finished<V>).value
    }

    /** Get the progress or null if this represents value.. */
    inline fun progressOrNull(): P? =
        when {
            isProgress -> progress
            else -> null
        }

    /** Get the value or null if this represents progress. */
    inline fun valueOrNull(): V? =
        when {
            isValue -> value
            else -> null
        }

    override fun toString(): String =
        when (preparedValueField) {
            is Finished<*> -> "Value($preparedValueField)"
            else -> "Progress($preparedValueField)"
        }

    // value construction
    companion object {
        @JvmName("progress")
        inline fun <P, V> progress(progress: P): PreparedValue<P, V> = PreparedValue(progress)

        @JvmName("value")
        inline fun <P, V> value(value: V): PreparedValue<P, V> =
            PreparedValue(createFinished(value))
    }

    /** Marker class to encapsulate non-progress values. */
    internal class Finished<R>(@JvmField val value: R) : Serializable {
        override fun equals(other: Any?): Boolean = other is Finished<*> && value == other.value
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Finished($value)"
    }
}

/** Spawn internal marker class without exposing it. */
@PublishedApi
internal fun <V> createFinished(value: V): Any = PreparedValue.Finished(value)

/** Perform [action] if this represents progress. Returns self. */
@OptIn(ExperimentalContracts::class)
inline fun <P, V> PreparedValue<P, V>.onProgress(action: (progress: P) -> Unit): PreparedValue<P, V> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    progressOrNull()?.let { action(it) }
    return this
}

/** Perform [action] if this represents value. Returns self. */
@OptIn(ExperimentalContracts::class)
inline fun <P, V> PreparedValue<P, V>.onValue(action: (value: V) -> Unit): PreparedValue<P, V> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }
    valueOrNull()?.let { action(it) }
    return this
}

/**
 * Execute one of the actions depending on what this [PreparedValue] represents.
 *
 * This will rethrow any exceptions thrown by them.
 * */
@OptIn(ExperimentalContracts::class)
inline fun <P, V, T> PreparedValue<P, V>.fold(
    onProgress: (progress: P) -> T,
    onValue: (value: V) -> T
): T {
    contract {
        callsInPlace(onProgress, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onValue, InvocationKind.AT_MOST_ONCE)
    }
    return when (val value = valueOrNull()) {
        null -> onProgress(preparedValueField as P)
        else -> onValue(value)
    }
}

/** Fold each [PreparedValue] into single value to flow down. */
inline fun <P, V, R> Flow<PreparedValue<P, V>>.foldPreparedValue(
    crossinline onProgress: (progress: P) -> R,
    crossinline onValue: (value: V) -> R
) = map { it.fold(onProgress, onValue) }

/** Flow of [PreparedValue] values. Types can be inferred by using [emitProgress] and [emitValue]. **/
fun <P, V> progressAndValueFlow(block: suspend FlowCollector<PreparedValue<P, V>>.() -> Unit): Flow<PreparedValue<P, V>> =
    flow(block)

/** [transform] incoming flow into flow of [PreparedValue]. Types can be inferred by using [emitProgress] and [emitValue]. */
fun <T, P, V> Flow<T>.toProgressAndValueFlow(block: suspend FlowCollector<PreparedValue<P, V>>.(T) -> Unit): Flow<PreparedValue<P, V>> =
    transform(block)

/**
 * Ignore progress values and only emits values.
 */
fun <P, V> Flow<PreparedValue<P, V>>.filterProgress() = mapNotNull { it.valueOrNull() }

/**
 * Collect progress values in this flow while flowing down all non-progress values.
 * */
fun <P, V> Flow<PreparedValue<P, V>>.filterProgress(
    handleProgress: suspend (P) -> Unit
) = transform {
    when (val value = it.valueOrNull()) {
        null -> handleProgress(it.preparedValueField as P)
        else -> emit(value)
    }
}

/**
 * Collect progress values in this flow while waiting for first non-progress value.
 * */
suspend fun <P, V> Flow<PreparedValue<P, V>>.filterProgressUntilValue(
    handleProgress: suspend (P) -> Unit
) = filterProgress(handleProgress).first()

/** Emit progress for flows emitting [PreparedValue]. */
suspend fun <P, V> FlowCollector<PreparedValue<P, V>>.emitProgress(progress: P) =
    emit(PreparedValue.progress(progress))

/** Emit value for flows emitting [PreparedValue]. */
suspend fun <P, V> FlowCollector<PreparedValue<P, V>>.emitValue(value: V) =
    emit(PreparedValue.value(value))