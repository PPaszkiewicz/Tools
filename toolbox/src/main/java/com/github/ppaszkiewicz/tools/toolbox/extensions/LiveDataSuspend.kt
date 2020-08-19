package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

private const val DEFAULT_TIMEOUT = 1000L

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until value fulfills [condition].
 *
 * @return first value that fulfills [condition]
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(
    timeOut: Long = DEFAULT_TIMEOUT,
    condition: (T) -> Boolean
): T {
    // fast exit case for non nullable value (don't check for null since it might mean livedata
    // is not initialized)
    value?.takeIf(condition)?.let { return it }

    var obs: Observer<T>? = null
    val suspendBlock = suspend {
        suspendCancellableCoroutine<T> { continuation ->
            val valueObserver = object : Observer<T> {
                override fun onChanged(it: T) {
                    if (condition(it)) {
                        obs = null
                        removeObserver(this)
                        continuation.resume(it)
                    }
                }
            }
            obs = valueObserver
            mainHandler.post { observeForever(valueObserver) }
        }
    }
    return try {
        if (timeOut == Long.MIN_VALUE) suspendBlock()   // magic value that disables timeout
        else withTimeout(timeOut) { suspendBlock() }
    } finally {
        obs?.let {
            mainHandler.post {
                obs?.let {
                    removeObserver(it)
                }
            }
        }
    }
}

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until value is non-null.
 *
 * @return first non-null value
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long = DEFAULT_TIMEOUT) =
    awaitValue(timeOut, isNotNullCondition)!!

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until value fulfills [condition].
 *
 * @return first value that fulfills [condition] or null on timeout
 **/
suspend fun <T> LiveData<T>.awaitValueOrNull(
    timeOut: Long = DEFAULT_TIMEOUT,
    condition: (T?) -> Boolean
): T? {
    return try {
        awaitValue(timeOut, condition)
    } catch (tEx: TimeoutCancellationException) {
        null // consume timeout and return null
    }
}

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until value is non-null.
 *
 * @return first non-null value or null on timeout
 **/
suspend fun <T> LiveData<T>.awaitValueOrNull(timeOut: Long = DEFAULT_TIMEOUT) =
    awaitValue(timeOut, isNotNullCondition)

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until value is `null`.
 *
 * @return `true` if value becomes `null`, `false` otherwise
 **/
suspend fun <T> LiveData<T>.awaitNull(timeOut: Long = DEFAULT_TIMEOUT): Boolean {
    return try {
        awaitValue(timeOut, isNullCondition)
        true
    } catch (tEx: TimeoutCancellationException) {
        false
    }
}

// "Forever" is used explicitly to denote that those methods are dangerous to use
// since they have no safety timeout and there's possibility that no value fulfilling condition
// is ever emitted

/**
 * Suspend coroutine **indefinitely** until value fulfills [condition].
 *
 * You must have a guarantee that value satisfying [condition] will be emitted or
 * coroutine will never continue.
 *
 * @return first value that fulfills [condition]
 **/
suspend fun <T> LiveData<T>.awaitValueForever(condition: (T?) -> Boolean) =
    awaitValue(Long.MIN_VALUE, condition)

/**
 * Suspend coroutine **indefinitely** until value is not null.
 *
 * You must have a guarantee that non-null value will be emitted or coroutine will never continue.
 *
 * @return first non-null value
 **/
suspend fun <T> LiveData<T>.awaitValueForever() = awaitValueForever(isNotNullCondition)!!

/**
 * Suspend coroutine **indefinitely** until value is `null`.
 *
 * You must have a guarantee that null value will be emitted or coroutine will never continue.
 *
 * @return `true` if value becomes `null`
 **/
suspend fun <T> LiveData<T>.awaitNullForever(): Boolean {
    awaitValue(Long.MIN_VALUE, isNullCondition)
    return true
}

// reusable helper objects
private val isNotNullCondition = { it: Any? -> it != null }
private val isNullCondition = { it: Any? -> it == null }
private val mainHandler = Handler(Looper.getMainLooper())