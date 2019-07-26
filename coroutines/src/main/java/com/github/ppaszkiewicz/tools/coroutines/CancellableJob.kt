package com.github.ppaszkiewicz.tools.coroutines

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Wrapper for lateinit deferred object. Allows query of state interface methods (vals) before it's initialized.
 *
 * Check [isSet] to see if it was setup. Initialize using [setJob] otherwise.
 *
 * @param T type of result of async block, use [Unit] if no result is expected.
 */
open class CancellableJob<T : Any?> : InterruptibleWork(),
    ICancellableJob<T> {
    /**
     * Actual job: a [CompletableDeferred] that can be cancelled with a reason.
     */
    private lateinit var deferred: CompletableDeferred<T>

    /** Child job of [deferred]. Pushes result or exception as [deferred] result. */
    private lateinit var childJob: Job

    /** Backing for [getCancellationException]. */
    private var cancellationException : Throwable? = null

    /**
     *  Wrap an async block to act as a child of [deferred].
     * @param scope to run async in
     * @param context context that will be combined with [deferred]
     * @param block block that will run while [deferred] is active
     * */
    internal fun setJob(scope: CoroutineScope, context: CoroutineContext, block: suspend CoroutineScope.() -> T) {
        if (isSet)
            throw IllegalStateException("Can only set deferred once!")

        deferred = CompletableDeferred()
        childJob = scope.async(deferred + context, CoroutineStart.LAZY) {
            // complete deferred when childJob completes
            deferred.complete(block())
        }
        deferred.invokeOnCompletion(this::onTaskComplete)
        childJob.invokeOnCompletion(this::onChildCompletion)
        childJob.start()
    }

    /** Triggered when child job finishes: push exception to parent. */
    private fun onChildCompletion(t: Throwable?) {
        if (t != null && deferred.isActive) {
            // complete parent with an exception
            deferred.completeExceptionally(t)
        }
    }

    /**
     * Triggered when deferred completes. [t] is null on success, otherwise exception that stopped. Note that instances of
     * [CancellationException] should not be treated as errors.
     * */
    override fun onTaskComplete(t: Throwable?) {
        // nothing special happens - override for custom behavior
    }

    /**
     * True if deferred was set
     */
    var isSet = false
        get() = if (field) true else {
            field = ::deferred.isInitialized
            field
        }
        private set

    override val isActive: Boolean
        get() = isSet && deferred.isActive

    // todo: is completeExceptionally an issue?
    override val isCancelled: Boolean
        get() = isSet && cancellationException != null || deferred.isCancelled

    override val isCompleted: Boolean
        get() = isSet && deferred.isCompleted

    // cancel job including interruption of underlying thread
    override fun cancel(cause: Throwable?): Boolean {
        val c = internalCancelDeferred(cause)
        cancelWork()
        return c
    }

    // cancel ongoing deferred
    private fun internalCancelDeferred(cause: Throwable?): Boolean{
        if (isSet) {
            if (cause != null) {
                if(cancellationException == null)
                    cancellationException = cause
                return deferred.completeExceptionally(cause)
            }
            else if (isActive) {
                cancellationException = cause
                deferred.cancel()
                return true
            }
        }
        return false
    }

    override fun cancel() = cancel(null)
    override suspend fun await() = deferred.await()

    /**
     * Obtain exception that cancelled this job. (result not guaranteed?)
     * */
    final override fun getCancellationException() : Throwable?{
        cancellationException?.let { return it }
        // not possible yet
        if(isActive)
            return null
        return try{
            deferred.getCompletionExceptionOrNull()
        }catch (ise: IllegalStateException){
            null
        }
    }

    /** Immediately throws [CancellationException] to cancel this task if it's no longer active. */
    override suspend fun cancelIfInactive() =
            if (!isActive) {
                // try to propagate reason for cancellation if possible, fallback for raw exception
                throw getCancellationException() ?: CancellationException("Job cancelled")
            } else Unit

    override fun getWorkCancellationException(interruptedException: InterruptedException): Throwable {
        // obtain reason this coroutine was cancelled for, otherwise message from interrupted exception
        return getCancellationException() ?: super.getWorkCancellationException(interruptedException)
    }
}

/** Compatibility interface for job that can be cancelled with a custom throwable. */
interface ICancellableJob<T : Any?> {
    val isActive: Boolean
    val isCancelled: Boolean
    val isCompleted: Boolean
    fun onTaskComplete(t: Throwable?)
    fun cancel(cause: Throwable?): Boolean
    fun cancel(): Boolean
    fun getCancellationException() : Throwable?
    suspend fun cancelIfInactive()
    suspend fun await(): T
}