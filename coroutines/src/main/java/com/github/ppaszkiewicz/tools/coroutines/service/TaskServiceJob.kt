package com.github.ppaszkiewicz.tools.coroutines.service

import android.content.Intent
import android.util.Log
import com.github.ppaszkiewicz.tools.coroutines.CancellableJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wrapper for async job (holding intent that started it), and definition of background task.
 *
 * Possible to override all async callbacks, by default they delegate calls back to service.
 *
 * Extends [CancellableJob].
 * */
abstract class TaskServiceJob<R> : CancellableJob<R>() {
    /** Service that created this job. */
    lateinit var context: QueuedTaskService<R>
        internal set
    /** Intent of this job. Should only be used internally as read only. */
    lateinit var intent: Intent
        internal set
    /** Scope used in [doInBackground] of this job. For advanced use only. */
    lateinit var coroutineScope: CoroutineScope
        internal set

    /**
     * Implementation of async loading. Check [CancellableJob.cancelIfInactive] to cancel periodically.
     *
     * Catch [CancellationException] and rethrow it to clean up any temporary resources.
     *
     * @throws CancellationException if cancelled, triggers [QueuedTaskService.onTaskCancelled].
     * @throws Exception any other exception is allowed to be thrown, will be caught and forwarded to [QueuedTaskService.onTaskError].
     * */
    @Throws(CancellationException::class, Exception::class)
    abstract suspend fun doInBackground(intent: Intent): R

    /**
     * Called when async is finished (success, cancel or error).
     *
     * To handle cancel out of [doInBackground] only, override [QueuedTaskService.onTaskCancelled] instead.
     * */
    override fun onTaskComplete(t: Throwable?) {
        context.apply {
            launch(uiContext) {
                when (t) {
                    null -> {
                        // success
                    }
                    is CancellingAllException -> this@TaskServiceJob.onTaskCancelled(intent, t.message, true)
                    is CancellationException -> this@TaskServiceJob.onTaskCancelled(intent, t.message,false)
                    else -> this@TaskServiceJob.onTaskError(intent, t)
                }
                launch(lock) {
                    // task complete so remove it from  jobs:
                    if (jobs.remove(this@TaskServiceJob)) {
                        // notify that jobs size changed
                        if (t !is CancellingAllException)
                            launch(uiContext) {
                                onTotalSizeChanged(getTotalSize())
                            }
                    } else
                        Log.e("TaskServiceJob", "Failed ro remove a task from jobs!")

                    if (t is CancellingAllException) {
                        // if this is last job being cancelled (within cancelAll), trigger callback
                        if (isEmpty())
                            allTasksFinished()
                    } else
                        startJobFromQueue(this)
                }
            }
        }
    }

    /** Run the [block] on UI thread. */
    @Suppress("unused")
    fun runOnUiThread(block: suspend CoroutineScope.() -> Unit) = context.runOnUiThread(block)

    /** Success callback, default implementation delegates to service. */
    open fun onTaskFinished(intent: Intent, result: R) = context.onTaskFinished(intent, result)

    /** Task start callback, returning *false* discards the task immediately. Default implementation delegates to service. */
    open fun onTaskStarted(intent: Intent) = context.onTaskStarted(intent)

    /**
     * Cancellation callback, default implementation delegates to service. [cancellingAll] means all tasks are being cleared.
     * */
    open fun onTaskCancelled(intent: Intent, message : String?, cancellingAll: Boolean) = context.onTaskCancelled(intent, message, cancellingAll)

    /** Error callback, default implementation delegates to service. */
    open fun onTaskError(intent: Intent, cause: Throwable) = context.onTaskError(intent, cause)

    // custom exception to throw when blocking call is interrupted
    override fun getWorkInterruptedException(thread: Thread) =
        InterruptedException("${intent.dataString} was interrupted!")
}