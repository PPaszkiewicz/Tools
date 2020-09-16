package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

// default timeout is rather short but enough for most livedatas backed by local providers like
// sensors or database to return
private const val DEFAULT_TIMEOUT = 1000L
private const val DISABLE_TIMEOUT = Long.MIN_VALUE

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until livedata value fulfills [condition].
 *
 * @return first value that fulfills [condition]
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(
    timeOut: Long = DEFAULT_TIMEOUT,
    condition: (T) -> Boolean
): T {
    // fast exit case for non nullable value
    // - don't check null because it might mean livedata is not initialized
    // - possible issue: if livedata is NOT active and condition is satisfied here outdated value
    // might be returned if livedata implementation relies on invalidation during onActive()
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
            mainHandler.post {
                if (continuation.isActive) observeForever(valueObserver)
                else obs = null
            }
        }
    }

    return try {
        if (timeOut == DISABLE_TIMEOUT) suspendBlock()
        else withTimeout(timeOut) { suspendBlock() }
    } finally {
        if (obs != null) mainHandler.post {
            obs?.let { removeObserver(it) } // double check because obs might change between threads
        }
    }
}

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until livedata value is non-null.
 *
 * @return first non-null value
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long = DEFAULT_TIMEOUT) =
    awaitValue(timeOut, isNotNullCondition)!!

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until livedata value fulfills [condition].
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
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until livedata value is non-null.
 *
 * @return first non-null value or null on timeout
 **/
suspend fun <T> LiveData<T>.awaitValueOrNull(timeOut: Long = DEFAULT_TIMEOUT) =
    awaitValue(timeOut, isNotNullCondition)

/**
 * Suspend coroutine up to [timeOut] _(default: 1000ms)_ until livedata value is `null`.
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
 * Suspend coroutine **indefinitely** until livedata value fulfills [condition].
 *
 * You must have a guarantee that value satisfying [condition] will be emitted or
 * coroutine will never continue.
 *
 * @return first value that fulfills [condition]
 **/
suspend fun <T> LiveData<T>.awaitValueForever(condition: (T?) -> Boolean) =
    awaitValue(DISABLE_TIMEOUT, condition)

/**
 * Suspend coroutine **indefinitely** until livedata value is not null.
 *
 * You must have a guarantee that non-null value will be emitted or coroutine will never continue.
 *
 * @return first non-null value
 **/
suspend fun <T> LiveData<T>.awaitValueForever() = awaitValueForever(isNotNullCondition)!!

/**
 * Suspend coroutine **indefinitely** until livedata value is `null`.
 *
 * You must have a guarantee that null value will be emitted or coroutine will never continue.
 *
 * @return `true` if value becomes `null`
 **/
suspend fun <T> LiveData<T>.awaitNullForever(): Boolean {
    awaitValueForever(isNullCondition)
    return true
}

// reusable helper objects
private val isNotNullCondition = { it: Any? -> it != null }
private val isNullCondition = { it: Any? -> it == null }
private val mainHandler = Handler(Looper.getMainLooper())