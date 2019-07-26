package com.github.ppaszkiewicz.tools.coroutines.loader

import android.util.Log
import com.github.ppaszkiewicz.tools.coroutines.CancellableJob
import kotlinx.coroutines.*

/**
 *  CoroutineLoader task. This is implementation of the loading, multiple queries can
 *  attach to a single running task and wait for it to finish.
 *
 *  This is not directly returned to the async call place.
 *
 *  @param Q type of query key (string, long, etc)
 *  @param R type of result
 */
abstract class CoroutineLoaderTask<Q : Any, R : Any> : CancellableJob<Unit>() {
    companion object {
        const val TAG = "CLoaderTask"
    }

    // job is injected when ASYNC task starts. Otherwise it is not initialized
    private lateinit var _loader: CoroutineLoader<Q, R>

    /**
     * Loader running this task.
     *
     * This is injected AFTER constructor, so it cannot be used to set up fields.
     **/
    val loader
        get() = _loader

    private lateinit var _key: Q

    /**
     * Query key of this task. If loader implements key mutation, this is mutated key.
     *
     * This is injected AFTER constructor, so it cannot be used to set up fields.
     * */
    val key: Q
        get() = _key

    private lateinit var _originalKey: Q

    /**
     * Query key of this task. If loader implements key mutation, this is the original key.
     *
     * This is injected AFTER constructor, so it cannot be used to set up fields.
     * */
    val originalKey: Q
        get() = _originalKey

    /**
     *  Array of queries awaiting this task to finish.
     *
     *  Modify this list only inside  [CoroutineLoader.sync] thread.
     *  */
    private val queries = ArrayList<CoroutineLoaderQuery<Q, R, *>>()

    /**
     * Timestamp of when ASYNC task started. -1 if this task has not used background multi thread.
     * */
    var startTimestamp = -1L
        private set
    /**
     * Timestamp of when ASYNC finished. -1 if this task has finished background multi thread.
     * */
    var endTimeStamp = -1L
        private set

    /** Coroutine coroutineContext where [doInBackground] runs. Valid only when it's active. */
    var backgroundScope: CoroutineScope? = null
        private set

    /**
     * Time taken to perform ASYNC in milliseconds. If [endTimeStamp] is not set this returns current runtime.
     *
     * If task has not started async this returns -1.
     */
    val asyncRuntime
        get() = when {
            endTimeStamp != -1L -> endTimeStamp - startTimestamp
            startTimestamp != -1L -> System.currentTimeMillis() - startTimestamp
            else -> -1
        }

    /** Same object that will be returned to progress callback. Only it's [CoroutineLoaderProgress.value] will update. */
    private lateinit var progressWrapper: CoroutineLoaderProgress<Q, R>

    /** Inject values. */
    internal fun lateInit(mutatedKey: Q, originalKey: Q, loader: CoroutineLoader<Q, R>) {
        _key = mutatedKey
        _originalKey = originalKey
        _loader = loader
        progressWrapper = CoroutineLoaderProgress(mutatedKey, originalKey, null)
    }

    /**
     * Insert new [query] into list of attached queries.
     *
     * - Context: [CoroutineLoader.sync].
     * */
    internal fun addQuery(query: CoroutineLoaderQuery<Q, R, *>) {
        queries.add(query)
    }

    /** Internal call to remove a query from this task. */
    internal fun cancelQuery(query: CoroutineLoaderQuery<Q, R, *>, reason: String?) {
        loader.scope.launch(loader.sync) {
            val wasRemoved = queries.remove(query)
            if (!wasRemoved)
                Log.e(TAG, "query is not in queries list: ${query.key}")
            else
                Log.i(TAG, "query cancelled.")
            if (queries.isEmpty()) { // so this never succeeds?
                performCancel(
                    CancellationException(
                        reason
                            ?: "All attached queries cancelled"
                    )
                )
            } else if (wasRemoved) {
                // trigger single query cancellation while task is still alive
                query.postCancellation(
                    CoroutineLoaderCancellation(key, originalKey, reason, CancellationType.QUERY)
                )
                query.release()
            }
        }
    }

    /**
     * Internal method for cancel - also calls [preCancel] if this is still running.
     *
     * - Context: [CoroutineLoader.sync].
     */
    internal suspend fun performCancel(cause: Throwable) {
        val wasActive = isActive
        // Log.d(TAG, "cancelling $key -> ${cause.message}, was active: $wasActive")
        cancel(cause)
        if (wasActive) {
            Log.d(TAG, "attempting to kill $key thread: $workThreadName")
            // cancels the thread if there's work()
            Log.i(TAG, "preCancel: ${cancelWork()} - ${cause.message}")
            preCancel(cause)
        }
    }

    /** Internal method for [preExecute]. */
    internal fun performPreExecute(params: Any?): CoroutineLoaderResult<Q, R>? {
        preExecute(key, params)?.let {
            return CoroutineLoaderResult(
                key,
                originalKey,
                it,
                CoroutineLoaderResult.Info(true)
            )
        }
        return null
    }

    /** Internal method for [queryCache]. */
    internal suspend fun performQueryCache(params: Any?): CoroutineLoaderResult<Q, R>? {
        queryCache(key, params)?.let {
            return CoroutineLoaderResult(
                key,
                originalKey,
                it,
                CoroutineLoaderResult.Info(false, wasCached = true)
            )
        }
        return null
    }

    /**
     *  Internal call for [doInBackground].
     *
     * - Context: [CoroutineLoader.sync].
     *
     *  @param scope unique job context that runs on synchronized thread.
     * */
    internal suspend fun performDoInBackground(scope: CoroutineScope, params: Any?) {
        scope.apply {
            startTimestamp = System.currentTimeMillis()
            // this is the core switch - performs background loading and returns to this thread
            val async = async(loader.background) {
                backgroundScope = this
                val bgResult = doInBackground(key, params)
                backgroundScope = null
                bgResult
            }
            val result = async.await()
            //Log.d(TAG, "finish: queries: ${queries.size}, key: $key")
            cacheResult(key, params, result)
            // notify queries on UI thread and wait
            // this will execute even if this job was cancelled (no longer checks isActive)
            val notifier = launch(loader.ui) {
                for (q in queries) {
                    q.finish(
                        CoroutineLoaderResult(
                            key, originalKey, result,
                            CoroutineLoaderResult.Info(
                                false,
                                false,
                                q.isJoined
                            )
                        )
                    )
                }
            }
            notifier.join()
        }
    }

    /** This is called when task completes for any reason (listener method for coroutine execution). */
    final override fun onTaskComplete(t: Throwable?) {
        when (t) {
            null -> {
                // success
            }
            is AllCancellationException -> doCancel(t.message, true)
            is CancellationException -> doCancel(t.message, false)
            else -> doError(t)
        }
        onFinish()
        loader.scope.launch(loader.sync) {
            for (it in queries) {
                it.release()
            }
            loader.onTaskComplete(this@CoroutineLoaderTask)
            queries.clear()
        }
    }

    /** Internal - notify cancellation callback in queries. */
    private fun doCancel(reason: String?, isCancellingAll: Boolean) {
        if (onCancel(reason, isCancellingAll)) {
            val cancelObj = CoroutineLoaderCancellation<Q, R>(key, originalKey, reason, CancellationType.from(isCancellingAll))
            // iterate over copy to prevent concurrent modification exceptions
            queries.toTypedArray().forEach { it.postCancellation(cancelObj) }
        }
    }

    /** Internal - notify error callback in queries. */
    private fun doError(t: Throwable) {
        if (onError(t)) {
            val errorObj = CoroutineLoaderError<Q, R>(key, originalKey, t)
            // iterate over copy to prevent concurrent modification exceptions
            queries.toTypedArray().forEach { it.postError(errorObj) }
        }
    }

    // custom error text
    override fun getWorkInterruptedException(thread: Thread) = InterruptedException("$key + $thread was interrupted!")

    /** Run the [block] on UI thread. This is a helper method to use from [onError] and [onCancel]. */
    @Suppress("DeferredResultUnused")
    fun runOnUiThread(block: suspend CoroutineScope.() -> Unit) {
        // ignore deferred result - unlike launch async doesn't crash the app
        loader.scope.async(loader.ui, block = block)
    }

    /**
     * Called before loader switches any threads. This should not perform any long running tasks.
     *
     * If this method returns non-null value query is considered complete, result block is executed
     * immediately and task is discarded.
     *
     * If this returns null it will receive [queryCache] call next.
     *
     * - Context: [CoroutineLoader.ui].
     *
     * @return non-null to finish query in-line
     */
    open fun preExecute(key: Q, params: Any?): R? {
        return null
    }

    /**
     * This is called just after this task gets cancelled (background thread will still be working).
     *
     * This will only be called when cancel request comes from [CoroutineLoader.cancelAndReleaseAsyncResult], [CoroutineLoader.release]
     * or last attached [CoroutineLoaderQuery.cancel].
     *
     * *Internal calls to [Job.cancel] do not trigger this callback so cancellation cannot rely entirely on this method.*
     *
     * If [doInBackground] is performing a blocking call that can be interrupted from outside (aside from [work] blocks which receives [Thread.interrupt]),
     * this is the only place to do it.
     *
     * - Context: [CoroutineLoader.sync].
     * */
    open suspend fun preCancel(t: Throwable) {}

    /**
     * Triggered after task finishes (for any reason) and disconnects from the loader.
     *
     * This can be used as a final clean up method.
     *
     * - Context: unspecified
     */
    open fun onFinish() {}

    /**
     * Pre [doInBackground], query the cache.
     *
     * - Context: [CoroutineLoader.sync].
     * @return null to proceed to [doInBackground], or value to return
     */
    abstract suspend fun queryCache(key: Q, params: Any?): R?

    /**
     * Triggered when task is cancelled, before attached queries receive callback.
     *
     * To run some code on UI thread, use [runOnUiThread].
     *
     * - Context: unspecified
     *
     * @return true (default) to send callback to attached queries, false to consume it
     */
    open fun onCancel(reason: String?, isCancellingAll: Boolean) = true

    /**
     * Triggered when task crashes, before attached queries receive callback.
     *
     * To run some code on UI thread, use [runOnUiThread].
     *
     * - Context: unspecified
     *
     * @return true (default) to send callback to attached queries, false to consume it
     */
    open fun onError(t: Throwable?) = true

    /**
     * Triggered when task is about to send emit progress, before attached queries receive callback.
     *
     * - Context: [CoroutineLoader.ui]
     *
     * @return [progress] value to send to queries, or null to prevent callback
     */
    open fun onProgress(progress: Any): Any? = progress

    /**
     * This performs the actual background task.
     *
     * - Call [cancelIfInactive] periodically to interrupt properly.
     * - Wrap long blocking calls with [work] if they can be interrupted by [Thread.interrupt].
     * - Refer to [backgroundScope] to launch nested coroutines.
     * - Use [postProgress] to send progress to all queries on the UI thread.
     * - It should throw all exceptions that happen within to properly get [onError] and [onCancel] callbacks.
     * -----
     * - Context: [CoroutineLoader.background].
     *
     * @return value to return
     */
    @Throws(Exception::class)
    abstract suspend fun doInBackground(key: Q, params: Any?): R

    /**
     * Only call from within [doInBackground].
     *
     * Will send progress to all attached queries using ui context.
     */
    suspend fun postProgress(progress: Any) {
        // copy queries reference to prevent concurrent modification error
        val queries = this.queries.toTypedArray()
        loader.scope.launch(loader.ui) {
            val p = onProgress(progress) ?: return@launch
            progressWrapper.value = p
            for (query in queries) {
                try {
                    query.postProgress(progressWrapper)
                } catch (ce: CancellationException) {
                    // ignore cancellation exception here - keep proceeding through items.
                }
            }
        }
    }

    /**
     * Post [doInBackground], save the result in  the cache.
     *
     * - Context: [CoroutineLoader.sync].
     * @param result passed from [doInBackground].
     */
    abstract suspend fun cacheResult(key: Q, params: Any?, result: R)

    /**
     *  Clear result from cache. This is called when [CoroutineLoader.cancelAndReleaseAsyncResult] is called.
     *
     *  If there was no ongoing task for this key, one is created to call this method explicitly.
     *
     * - Context: [CoroutineLoader.sync].
     * */
    abstract suspend fun clearResultFromCache(key: Q, params: Any?)
}