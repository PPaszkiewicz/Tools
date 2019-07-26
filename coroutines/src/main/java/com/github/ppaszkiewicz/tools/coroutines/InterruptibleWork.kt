package com.github.ppaszkiewicz.tools.coroutines

import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ExecutionException

/**
 * Host of interruptible thread that support cancellation of blocking call by performing
 * [Thread.interrupt], catching exceptions and rethrowing them in coroutine friendly way.
 *
 * Core method of this object is [work].
 * */
open class InterruptibleWork {
    companion object {
        const val TAG = "InterruptibleWork"
    }

    // internal backing fields, accessible inline due to PublishedApi annotation
    @PublishedApi
    internal var _workThread: Thread? = null
    @PublishedApi
    internal var _workThreadStartTimestamp = -1L
    @PublishedApi
    internal var _workThreadRuntime = -1L

    /**
     * Thread of a blocking call, backing field of [work] method.
     * */
    val workThread: Thread?
        get() = _workThread

    /**
     * Start timestamp of last [work] block.
     * */
    val workThreadStartTimestamp
        get() = _workThreadStartTimestamp


    /**
     * Runtime of last [work] block.
     **/
    val workThreadRuntime
        get() = _workThreadRuntime

    /** Name of workThread - for debug printing. */
    val workThreadName
        get() = workThread?.toString()

    /**
     * Perform blocking [block] inside current thread with [InterruptedException] handling.
     *
     * This should be invoked to perform long running tasks that can be cancelled by [Thread.interrupt]
     * within coroutines.
     *
     * Do not start any coroutines and suspensions within [block].
     *
     * Stores time it took to perform [block] in [workThreadRuntime].
     *
     * @throws ThreadChangedException if thread switched while executing [block]
     * @throws CancellationException if thread was interrupted
     * */
    inline fun <T> work(crossinline block: () -> T): T {
        _workThread = Thread.currentThread()
        _workThreadStartTimestamp = System.currentTimeMillis()
        val r = try {
            val v = block()
            // work thread should never be null here
            if (workThread != Thread.currentThread()) {
                onWorkThreadChanged(workThread!!, Thread.currentThread())
                throw ThreadChangedException("Thread switching is not allowed within work block!")
            }
            if (workThread!!.isInterrupted) {
                // consume Interrupted flag from the thread
                throw getWorkInterruptedException(workThread!!)
            }
            v
        } catch (ie: InterruptedException) {
            Log.w(TAG, "cancelled: ${ie.message}")  // message might be null
            throw getWorkCancellationException(ie)
        }
        _workThread = null
        _workThreadRuntime = System.currentTimeMillis() - workThreadStartTimestamp
        return r
    }

    /** Interrupt any work. Returns *true* if [work] is executing and not interrupted, *false* otherwise.*/
    fun cancelWork(): Boolean {
        workThread?.let {
            if (it.isInterrupted)
                return false
            it.interrupt()
            return true
        }
        return false
    }

    /** Triggered before [ThreadChangedException] gets thrown. */
    open fun onWorkThreadChanged(threadOld: Thread, threadNew: Thread) {
        Log.e(TAG, "Thread changed during work block execution: $threadNew != $threadOld")
    }

    /** Customize exception thrown when interrupt is called - message will be propagated to [CancellationException] if needed. */
    open fun getWorkInterruptedException(thread: Thread): InterruptedException =
        InterruptedException("$thread -> was interrupted!")

    /**
     * Throwable that will be thrown when [interruptedException] is caught within [work] block.
     *
     * Recommended to return an instance of [CancellationException] to consider host coroutine as cancelled without
     * further exceptions.
     * */
    open fun getWorkCancellationException(interruptedException: InterruptedException): Throwable {
        return CancellationException(interruptedException.message)
    }

    /** Thrown if thread changes during [work] block. */
    class ThreadChangedException(s: String?) : ExecutionException(s)
}