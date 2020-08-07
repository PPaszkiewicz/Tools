package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Suspend coroutine up to [timeOut] until value fulfills [condition].
 *
 * @return first value that fulfills [condition]
 * @throws [TimeoutCancellationException] on timeout
 *  */
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long, condition: (T?) -> Boolean): T? {
    value.let { if (condition(it)) return it } // fast exit case
    var obs: Observer<T>? = null
    return try {
        withTimeoutOrNull(timeOut) {
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
 *  */
suspend fun <T> LiveData<T>.awaitValue(timeOut: Long) =
    awaitValue(timeOut, isNotNullCondition)!!

/**
 * Suspend coroutine up to [timeOut] until value fulfills [condition].
 *
 * @return first value that fulfills [condition] or null on timeout
 *  */
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
 *  */
suspend fun <T> LiveData<T>.awaitValueOrNull(timeOut: Long) =
    awaitValueOrNull(timeOut, isNotNullCondition)

private val isNotNullCondition = { it: Any? -> it != null }
private val mainHandler = Handler(Looper.getMainLooper())