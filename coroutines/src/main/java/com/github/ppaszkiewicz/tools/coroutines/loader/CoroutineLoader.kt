package com.github.ppaszkiewicz.tools.coroutines.loader

import android.util.Log
import kotlinx.coroutines.*

/**
 * Revised CoroutineLoader.
 *
 * ***********************************************
 *
 * Loader elements:
 * - [CoroutineLoader] - core loader class, creating instances of others and managing the state and callbacks
 * - [CoroutineLoaderQuery] - query for loader, every call to [loadAsync] generates new instance of this object
 * - [CoroutineLoaderTask] - tasks that perform the execution in background. Multiple queries can be attached to single task.
 *
 * This loader operates on three Coroutine Contexts:
 *
 * - [ui] - UI context, it's assumed all [loadAsync] calls are performed here. Also result block is executed here.
 * - [sync] - single thread used to synchronize cache and ongoing tasks list.
 * - [background] - multi threaded context to perform long running background tasks.
 *
 *  ***********************************************
 *  Query flow:
 *
 *  1. User calls [loadAsync] with key and params. He gets new [CoroutineLoaderQuery] proxy instance every time.
 *  2. [createTask] generates new [CoroutineLoaderTask]
 *  3. Call [CoroutineLoaderTask.preExecute] - if it returns valid result return it immediately
 *  4. Otherwise switch to [sync] thread, call [CoroutineLoaderTask.queryCache] on task object.
 *  5. On cache miss, try to find ongoing task for this key.
 *  6. Attach to the ongoing task, or add job to the created one.
 *  7. If job was added, run [CoroutineLoaderTask.doInBackground] on [background] to generate result.
 *  5. When it finishes, switch to [sync], [CoroutineLoaderTask.cacheResult] is called to store the result.
 *  6. [CoroutineLoaderTask] forwards result to all attached [CoroutineLoaderQuery] on [ui].
 *  7. [CoroutineLoaderTask] is removed from "running" tasks on [sync].
 *
 *  @param Q type of query keys (string, long, etc)
 *  @param R type of result
 * */

abstract class CoroutineLoader<Q : Any, R : Any> @JvmOverloads constructor(
    val coroutineLoaderDispatcherProvider: CoroutineLoaderDispatcherProvider = DefaultCoroutineLoaderDispatcherProvider(
        FIXED_COROUTINE_CONTEXT_THREAD_COUNT
    )
) {
    companion object {
        const val TAG = "CoroutineLoader"

        /** Default reason used for [release]. */
        const val REASON_DESTROYING_COROUTINES = "$TAG.REASON_DESTROYING_COROUTINES"

        /** Default work thread count used by loaders. */
        val FIXED_COROUTINE_CONTEXT_THREAD_COUNT by lazy {
            Math.max(1, Runtime.getRuntime().availableProcessors() - 2)
        }
    }

    /** Flag - raised when any async query comes - optimizes cleanup if no calls happened. */
    private var wasAsyncCalled = false

    /** Raised after loader is released and can no longer operate. */
    var wasReleased = false
        private set

    /** Default coroutineContext to run coroutines on. */
    val scope = coroutineLoaderDispatcherProvider.scope

    /** Multithread context for async calls. This context is used by all doInBackground calls. */
    val background = coroutineLoaderDispatcherProvider.background

    /** Context that runs everything on UI thread. This is used for result report exclusively. */
    val ui = coroutineLoaderDispatcherProvider.ui

    /** This is a single thread that allows synchronization of job listing and cache. */
    val sync by lazy {
        wasAsyncCalled = true   // this is initialized first so raise async flag here
        coroutineLoaderDispatcherProvider.sync
    }

    /**
     * Begin async loading. This is the core method for starting new tasks and background loading. This is short call for
     * success only without support for weak references, so be sure to clearly close async loader.
     *
     * To handle cancellation and errors use [loadAsyncWithCallbacks].
     *
     * @param key used to determine tasks
     * @param params optional extra params
     * @param finishBlock code to invoke on success.
     */
    fun loadAsync(
        key: Q,
        params: Any? = null,
        finishBlock: (CoroutineLoaderResult<Q, R>) -> Unit
    ): CoroutineLoaderQuery<Q, R, Unit> {
        val query = CoroutineLoaderQuery.NoRefBuilder<Q, R>(key)
            .result(finishBlock)
            .build()
        loadAsync(params, query)
        return query
    }

    /**
     * Begin async loading. This is the core method for starting new tasks and background loading. This is short call for
     * success only.
     *
     * To handle cancellation and errors use [loadAsyncWithCallbacks].
     *
     * @param key used to determine tasks
     * @param resultReceiver object that receives [finishBlock]. Stores weak reference to this object, if it's lost task is cancelled.
     * @param params optional extra params
     * @param finishBlock code to invoke on success.
     */
    fun <T : Any> loadAsyncWithWeakRef(
        key: Q,
        resultReceiver: T,
        params: Any? = null,
        finishBlock: T.(CoroutineLoaderResult<Q, R>) -> Unit
    ): CoroutineLoaderQuery<Q, R, T> {
        val query = CoroutineLoaderQuery.Builder<Q, R, T>(
            key,
            resultReceiver
        )
            .result(finishBlock)
            .build()
        loadAsync(params, query)
        return query
    }

    /**
     * Begin async loading. This is the core method for starting new tasks and background loading.
     *
     * @param key used to determine tasks
     * @param resultReceiver object that receives all callbacks.
     * @param params optional extra params
     * @param setupBlock block for preparing query callbacks
     */
    fun <T : Any> loadAsyncWithCallbacks(
        key: Q,
        resultReceiver: T,
        params: Any? = null,
        setupBlock: CoroutineLoaderQuery.Builder<Q, R, T>.() -> Unit
    ): CoroutineLoaderQuery<Q, R, T> {
        val query = CoroutineLoaderQuery.Builder<Q, R, T>(
            key,
            resultReceiver
        )
            .apply(setupBlock)
            .build()
        loadAsync(params, query)
        return query
    }

    // internal callback after building a query
    private fun <T : Any> loadAsync(params: Any?, query: CoroutineLoaderQuery<Q, R, T>) {
        if (wasReleased) throw IllegalStateException("This coroutine loader was already released")

        // create the query and base of the task
        val key = query.key
        val mutatedKey = mutateKey(query.key, params)
        val task = createTask(key, params)
        task.lateInit(mutatedKey, key, this)
        // see if preExecute returns
        task.performPreExecute(params)?.let {
            query.preResult = true
            query.postResult(it)
            query.release()
            return
        }
        // create sync thread execution block, did not return in-line.
        // loader itself implements CoroutineScope - it's ran on it
        scope.launch(sync) {
            if (query.isCancelled) {
                Log.w(TAG, "$mutatedKey was cancelled before starting (start)")
                return@launch
            }
            // see if there's a cache hit:
            val cacheHit = task.performQueryCache(params)
            if (cacheHit != null) {
                // cache hit, async finishes.
                query.wasCacheHit = true
                launch(ui) {
                    query.finish(cacheHit)
                }
            } else {
                // try to attach this query to ongoing task for this key:
                val ongoingTask = getTaskForKey(mutatedKey)
                if (query.isCancelled) {
                    Log.w(TAG, "$mutatedKey was cancelled before starting (after task search)")
                    return@launch
                }
                if (!isActive) {
                    // this might trip in case release() was called?
                    Log.w(TAG, "$mutatedKey no longer active before starting task - release() was called?")
                    return@launch
                }
                if (ongoingTask != null) {
                    // task found - attach
                    query.isJoined = true
                    query.attachToTask(ongoingTask)
                } else {
                    // only now begin new async task
                    query.attachToTask(task)

                    // create new job in the dispatcher
                    task.setJob(this, sync) {
                        task.performDoInBackground(this, params)
                    }

                    // keep track of this task
                    addTaskToOngoingList(task.key, task)
                }
            }
        }
    }

    /**
     * Tasks report when their async complete for any reason. Used exclusively to remove them from ongoing list.
     *
     * - Context: [sync]
     * */
    internal suspend fun onTaskComplete(task: CoroutineLoaderTask<Q, R>) {
        removeTaskFromOngoingList(task.key, task)
    }

    /**
     * See if there's result that can be obtained using [CoroutineLoaderTask.preExecute]. Does not start any
     * async loading.
     *
     * @param key key of the task
     * @param params optional params that might be needed for task
     * */
    fun getPreExecuteResult(key: Q, params: Any?): R? {
        val mutatedKey = mutateKey(key, params)
        val task = createTask(key, params)
        task.lateInit(mutatedKey, key, this)
        return task.preExecute(mutatedKey, params)
    }

    /**
     * Cancel an ongoing task.
     *
     * @param key to release
     * @param params optional params that might be needed to identify task
     * @param reason cancelation message
     * */
    fun cancelAsyncTask(key: Q, params: Any? = null, reason: String?) {
        cancelAndReleaseAsyncResult(key, params, clearResultFromCache = false, cancelOngoing = true, reason = reason)
    }

    /**
     * Master method for manipulating ongoing tasks.
     *
     * @param key to release
     * @param params optional params that might be needed to identify task
     * @param clearResultFromCache true to clear result from cache
     * @param cancelOngoing *true* to force cancel ongoing task for this key if possible
     * @param reason cancellation reason, [cancelOngoing] must be true
     */
    fun cancelAndReleaseAsyncResult(
        key: Q,
        params: Any? = null,
        clearResultFromCache: Boolean = true,
        cancelOngoing: Boolean = false,
        reason: String? = "cancelAndReleaseAsyncResult with cancelOngoing"
    ) {
        if (!wasAsyncCalled) return
        scope.launch(sync) {
            val mutatedKey = mutateKey(key, params)
            // see if this task is ongoing
            var task = getTaskForKey(mutatedKey)
            if (task == null) {
                task = createTask(key, params)
            } else if (cancelOngoing) {
                //fixme: logic here is odd and conflicts with query.cancel()
                val case = CancellationException(reason)
                task.performCancel(case)
            }
            if (clearResultFromCache)
                task.clearResultFromCache(mutatedKey, params)
        }
    }

    /**
     * Release any unprocessed requests and destroy the loader.
     *
     * @param reason reason string passed to throwable. default: [REASON_DESTROYING_COROUTINES]
     */
    fun release(reason: String? = REASON_DESTROYING_COROUTINES) {
        if (!wasAsyncCalled) return
        if (wasReleased) throw IllegalStateException("This coroutine loader was already released")
        wasReleased = true
        // run blocking to ensure everything closes before dispatcher providers die
        runBlocking(sync) {
            //            scope.launch(sync) {
            val tasks = getAllOngoingTasksForCancel()
            val exc = AllCancellationException(reason)
            for (task in tasks) {
                task.cancel(exc)
            }
            // now kill everything
            coroutineContext.cancelChildren()
//            }
        }
        coroutineLoaderDispatcherProvider.release()
    }

    /**
     * Optional method to mutate key based on [params].
     *
     * Mutated keys are used to differentiate Tasks based on their [params] as well as the [key].
     *
     * By default there's no mutation ([key] is returned) which means queries with different [params]
     * are considered the same task.
     * */
    open fun mutateKey(key: Q, params: Any?): Q = key

    /**
     * Task factory. Tasks are created in two cases:
     *
     * 1. [loadAsync] - new tasks will always receive [CoroutineLoaderTask.preExecute] call. Then they might or not
     * (if task with this key is already running) begin async task.
     * 2. [cancelAndReleaseAsyncResult] - if there was no ongoing task for this key. In that this object receives
     * [CoroutineLoaderTask.clearResultFromCache] call only.
     *
     * - Context: [ui] for [loadAsync], [sync] for [cancelAndReleaseAsyncResult].
     * */
    protected abstract fun createTask(key: Q, params: Any?): CoroutineLoaderTask<Q, R>

    /**
     * Implement ongoing task finding.
     *
     * - Context: [sync].
     * */
    protected abstract suspend fun getTaskForKey(mutatedKey: Q): CoroutineLoaderTask<Q, R>?

    /**
     * Implement storing ongoing tasks. This is called implicitly when [task] will begin async loading.
     *
     * - Context: [sync].
     * */
    protected abstract suspend fun addTaskToOngoingList(mutatedKey: Q, task: CoroutineLoaderTask<Q, R>)

    /**
     * Implement removing task from ongoing list. This is called when [task] completes or crashes.
     *
     * - Context: [sync].
     * */
    protected abstract suspend fun removeTaskFromOngoingList(mutatedKey: Q, task: CoroutineLoaderTask<Q, R>)

    /**
     * Called during [release].
     *
     * This will cancel all tasks, and each of them will
     * implicitly call [removeTaskFromOngoingList] that should clean up the list afterwards.
     *
     * - Context: [sync].
     * @return all ongoing tasks
     * */
    protected abstract suspend fun getAllOngoingTasksForCancel(): List<CoroutineLoaderTask<Q, R>>
}

/** Used when all jobs are cancelled at once. */
internal class AllCancellationException(s: String?) : CancellationException(s)