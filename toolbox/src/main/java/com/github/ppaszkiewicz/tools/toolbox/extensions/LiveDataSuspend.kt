package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Suspend coroutine up to [timeOut] until value fulfills [condition].
 *
 * @return first value that fulfills [condition]
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long, condition: (T?) -> Boolean): T? {
    value.let { if (condition(it)) return it } // fast exit case
    var obs: Observer<T>? = null
    val suspendBlock = suspend {
        suspendCancellableCoroutine<T?> { continuation ->
            val valueObserver = object : Observer<T> {
                override fun onChanged(it: T?) {
                    if (condition(it)) {
                        obs = null
                        removeObserver(this)
                        continuation.resume(value)
                    }
                }
            }
            obs = valueObserver
            mainHandler.post { observeForever(valueObserver) }
        }
    }
    return try {
        if (timeOut == Long.MIN_VALUE) suspendBlock()   // magic value that disables timeout
        else withTimeoutOrNull(timeOut) { suspendBlock() }
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
 * Suspend coroutine up to [timeOut] until value is non-null.
 *
 * @return first non-null value
 * @throws [TimeoutCancellationException] on timeout
 **/
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long) = awaitValue(timeOut, isNotNullCondition)!!

/**
 * Suspend coroutine up to [timeOut] until value fulfills [condition].
 *
 * @return first value that fulfills [condition] or null on timeout
 **/
suspend fun <T> LiveData<T>.awaitValueOrNull(timeOut: Long, condition: (T?) -> Boolean): T? {
    return try {
        awaitValue(timeOut, condition)
    } catch (tEx: TimeoutCancellationException) {
        null // consume timeout and return null
    }
}

/**
 * Suspend coroutine up to [timeOut] until value is non-null.
 *
 * @return first non-null value or null on timeout
 **/
suspend fun <T> LiveData<T>.awaitValueOrNull(timeOut: Long) =
    awaitValueOrNull(timeOut, isNotNullCondition)

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

// reusable helper objects
private val isNotNullCondition = { it: Any? -> it != null }
private val mainHandler = Handler(Looper.getMainLooper())