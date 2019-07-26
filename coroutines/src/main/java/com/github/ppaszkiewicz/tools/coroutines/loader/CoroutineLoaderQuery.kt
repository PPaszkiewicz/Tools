package com.github.ppaszkiewicz.tools.coroutines.loader

import android.util.Log
import com.github.ppaszkiewicz.tools.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Query for [CoroutineLoader].
 *
 * This is returned to the user whenever he calls [CoroutineLoader.loadAsync].
 *
 *  @param Q type of query keys (string, long, etc)
 *  @param R type of result
 *  @param T type of receiver
 */
class CoroutineLoaderQuery<Q : Any, R : Any, T : Any> internal constructor(
    /**
     * Key of this query.
     * */
    val key: Q
) {
    companion object {
        const val TAG = "CLoaderQuery"
    }

    /** True if this query joined ongoing task, false if it spawned a new one. */
    var isJoined: Boolean = false
        internal set
    /** True if this query hit result in preResult block - in that case task is always null. */
    internal var preResult = false
    /** True if this query hit result in cache block - in that case task is always null. */
    internal var wasCacheHit = false

    /**
     * Raised after this query is considered complete or otherwise discarded. It should no longer hold
     * references to finish receiver, callbacks or task.
     * */
    var wasReleased = false
        private set

    /** Raised after [cancel]. */
    var isCancelled = false
        private set

    // optional listeners
    private var progressCallback: CoroutineLoaderProgress.OnProgressListener<Q, R, T>? = null
    private var cancellationCallback: CoroutineLoaderCancellation.OnCancelListener<Q, R, T>? = null
    private var errorCallback: CoroutineLoaderError.OnErrorListener<Q, R, T>? = null
    private var weakRefLostCallback : CoroutineLoaderCallback.OnRefLostListener<Q, R>? = null
    /** Run when task finishes or cache is hit. */
    private var resultCallback: CoroutineLoaderResult.Listener<Q, R, T>? = null

    // callback that can receive all events
    private var defaultCallback: CoroutineLoaderCallback.Listener<Q, R, T>? = null

    /**
     * Task this query is waiting for.
     *
     * This value should only be changed from within [CoroutineLoader.sync].
     */
    private var task: CoroutineLoaderTask<Q, R>? = null

    /**
     * Result receivers reference. This is passed along to all callback interfaces. This should not be null until
     * query is released.
     * */
    private var resultReceiver: ObjectRef<T>? = null

    /** Task was started. */
    val isStarted
        get() = task?.isSet == true
    /** Task is running. */
    val isActive
        get() = task?.isActive == true
    /** Query finished successfully for any reason. */
    val isCompleted
        get() = preResult || wasCacheHit || isAsyncFinished
    /** Raised after async query or cache hit completes successfully. Always false if [preResult] is true. */
    var isAsyncFinished = false
        private set

    /**
     *  Attach to task to wait for its result and update callbacks.
     *
     * - Context: [CoroutineLoader.sync].
     * */
    internal fun attachToTask(task: CoroutineLoaderTask<Q, R>) {
        task.addQuery(this)
        this.task = task
    }

    /**
     * Cancel this query.
     *
     * If referenced task has no other queries attached it will be cancelled as well.
     *
     * @param reason cancellation message
     * @param cancelTask it true this will force cancel observed task (cancelling ALL attached queries)
     * */
    fun cancel(reason: String? = null, cancelTask : Boolean = false) {
        isCancelled = true
        //remove self from task on sync thread
        task.also {
            if (it == null) {
                if (!isCompleted)
                    Log.e(
                        TAG,
                        "cancel $key -> $reason - no task attached (was released: $wasReleased)!"
                    )                //fixme: why is wasReleased true here?
                release()
            } else {
                // task should trigger this querys cancellation callback and then release
                if(cancelTask) {
                    it.loader.scope.launch(it.loader.sync){
                        it.performCancel(CancellationException(reason))
                    }
                }else
                    it.cancelQuery(this, reason)
            }
        }
    }

    /** Finish this query. This will disconnect from the task and clear references. */
    internal suspend fun finish(result: CoroutineLoaderResult<Q, R>) {
        isAsyncFinished = true
        postResult(result)
        release()
    }

    /**
     * Callback to receive and execute it if [resultReceiver] is valid. Only call from UI thread.
     *
     * Ignored if [isAsyncFinished] is true.
     * */
    internal fun postProgress(progress: CoroutineLoaderProgress<Q, R>) {
        if (!isAsyncFinished) {
            post(progress){
                progressCallback?.onTaskProgress(it, progress)
                //todo: MORE HANDLING FOR REFERENCE LOST (should disconnect from task?)
            }
        }
    }

    /** Callback to receive and execute it if [resultReceiver] is valid. Only call from UI thread. */
    internal fun postResult(result: CoroutineLoaderResult<Q, R>) = post(result){
            resultCallback?.onTaskResult(it, result)
        }

    /** Callback to receive and execute it if [resultReceiver] is valid. Calling thread not specified. */
    internal fun postCancellation(cancellation: CoroutineLoaderCancellation<Q, R>) = post(cancellation){
            cancellationCallback?.onTaskCancelled(it, cancellation)
        }

    /** Callback to receive and execute it if [resultReceiver] is valid. Calling thread not specified. */
    internal fun postError(error: CoroutineLoaderError<Q, R>) =post(error){
            errorCallback?.onTaskError(it, error)
    }

    /** Internal: post callback to interface and default callback, or handle weak ref lost.*/
    private inline fun <C : CoroutineLoaderCallback<Q,R>>post(callback:C, f : (T) -> Unit){
        try {
            resultReceiver?.get()?.also(f)?.also {
                defaultCallback?.onTaskCallback(it, callback)
            }
        }catch (rl: RefLostCancellationException){
            weakRefLostCallback?.onRefLost(callback)
        }
    }

    /** Release all references. */
    internal fun release() {
        if (!wasReleased) {
            wasReleased = true
            resultReceiver?.clear()
            task = null
            resultCallback = null
            progressCallback = null
            cancellationCallback = null
            errorCallback = null
        }
    }

    /** Query builder that prepares all callbacks before query is executed.*/
    class Builder<Q : Any, R : Any, T : Any>(private val key: Q, private val resultReceiver: T) {
        private var progressCallback: CoroutineLoaderProgress.OnProgressListener<Q, R, T>? = null
        private var cancellationCallback: CoroutineLoaderCancellation.OnCancelListener<Q, R, T>? = null
        private var errorCallback: CoroutineLoaderError.OnErrorListener<Q, R, T>? = null
        private var resultCallback: CoroutineLoaderResult.Listener<Q, R, T>? = null
        private var defaultCallback: CoroutineLoaderCallback.Listener<Q, R, T>? = null
        private var weakRefLostCallback : CoroutineLoaderCallback.OnRefLostListener<Q, R>? = null
        private var useWeakRef = true

        /** Progress is guaranteed to be called on UI thread. */
        fun progress(callback: CoroutineLoaderProgress.OnProgressListener<Q, R, T>): Builder<Q, R, T> {
            progressCallback = callback
            return this
        }

        /** Progress is guaranteed to be called on UI thread. */
        inline fun progress(crossinline callback: (T, CoroutineLoaderProgress<Q, R>) -> Unit): Builder<Q, R, T> =
            progress(object :
                CoroutineLoaderProgress.OnProgressListener<Q, R, T> {
                override fun onTaskProgress(resultReceiver: T, progress: CoroutineLoaderProgress<Q, R>) =
                    callback(resultReceiver, progress)
            })

        /** Progress is guaranteed to be called on UI thread. This invokes callback with [resultReceiver] as receiver. */
        inline fun onProgress(crossinline callback: T.(CoroutineLoaderProgress<Q, R>) -> Unit): Builder<Q, R, T> =
            progress(callback)

        /** Cancellation callback - not guaranteed to be called on UI thread. */
        fun cancel(callback: CoroutineLoaderCancellation.OnCancelListener<Q, R, T>): Builder<Q, R, T> {
            cancellationCallback = callback
            return this
        }

        /** Cancellation callback - not guaranteed to be called on UI thread. */
        inline fun cancel(crossinline callback: (T, CoroutineLoaderCancellation<Q, R>) -> Unit): Builder<Q, R, T> =
            cancel(object :
                CoroutineLoaderCancellation.OnCancelListener<Q, R, T> {
                override fun onTaskCancelled(resultReceiver: T, cancellation: CoroutineLoaderCancellation<Q, R>) =
                    callback(resultReceiver, cancellation)
            })

        /** Cancellation callback - not guaranteed to be called on UI thread. This invokes callback with [resultReceiver] as receiver. */
        inline fun onCancel(crossinline callback: T.(CoroutineLoaderCancellation<Q, R>) -> Unit) = cancel(callback)

        /** Error callback - not guaranteed to be called on UI thread. */
        fun error(callback: CoroutineLoaderError.OnErrorListener<Q, R, T>): Builder<Q, R, T> {
            errorCallback = callback
            return this
        }

        /** Error callback - not guaranteed to be called on UI thread. */
        inline fun error(crossinline callback: (T, CoroutineLoaderError<Q, R>) -> Unit): Builder<Q, R, T> =
            error(object : CoroutineLoaderError.OnErrorListener<Q, R, T> {
                override fun onTaskError(resultReceiver: T, error: CoroutineLoaderError<Q, R>) =
                    callback(resultReceiver, error)
            })

        /** Error callback - not guaranteed to be called on UI thread. This invokes callback with [resultReceiver] as receiver. */
        inline fun onError(crossinline callback: T.(CoroutineLoaderError<Q, R>) -> Unit) = error(callback)

        /** Result is guaranteed to be called on UI thread. */
        fun result(callback: CoroutineLoaderResult.Listener<Q, R, T>): Builder<Q, R, T> {
            resultCallback = callback
            return this
        }

        /** Result is guaranteed to be called on UI thread. */
        inline fun result(crossinline callback: (T, result: CoroutineLoaderResult<Q, R>) -> Unit): Builder<Q, R, T> =
            result(object :
                CoroutineLoaderResult.Listener<Q, R, T> {
                override fun onTaskResult(resultReceiver: T, result: CoroutineLoaderResult<Q, R>) =
                    callback(resultReceiver, result)
            })

        /** Result is guaranteed to be called on UI thread. This invokes callback with [resultReceiver] as receiver. */
        inline fun onResult(crossinline callback: T.(result: CoroutineLoaderResult<Q, R>) -> Unit) = result(callback)


        /** Callback from all events. Triggered after respective callbacks do, on their thread. */
        fun callback(callback: CoroutineLoaderCallback.Listener<Q, R, T>): Builder<Q, R, T> {
            defaultCallback = callback
            return this
        }

        /** Callback from all events. Triggered after respective callbacks do, on their thread. */
        inline fun callback(crossinline callback: (T, call: CoroutineLoaderCallback<Q, R>) -> Unit): Builder<Q, R, T> =
            callback(object :
                CoroutineLoaderCallback.Listener<Q, R, T> {
                override fun onTaskCallback(resultReceiver: T, callback: CoroutineLoaderCallback<Q, R>) =
                    callback(resultReceiver, callback)
            })

        /** Callback from all events. Triggered after respective callbacks do, on their thread. This invokes callback with [resultReceiver] as receiver. */
        inline fun onCallback(crossinline callback: T.(call: CoroutineLoaderCallback<Q, R>) -> Unit) = callback(callback)


        /** Callback when weak reference is lost. If not implemented losing reference is silent cancellation. */
        fun weakRefLost(callback: CoroutineLoaderCallback.OnRefLostListener<Q, R>): Builder<Q, R, T> {
            weakRefLostCallback = callback
            return this
        }

        /** Callback when weak reference is lost. If not implemented losing reference is silent cancellation. */
        inline fun weakRefLost(crossinline callback: (call: CoroutineLoaderCallback<Q, R>) -> Unit) : Builder<Q, R, T> =
                weakRefLost(object :
                CoroutineLoaderCallback.OnRefLostListener<Q, R>{
                    override fun onRefLost(lostCallback: CoroutineLoaderCallback<Q, R>) = callback(lostCallback)
                })

        /** Callback when weak reference is lost. If not implemented losing reference is silent cancellation. */
        inline fun onWeakRefLost(crossinline callback: (call: CoroutineLoaderCallback<Q, R>) -> Unit) = weakRefLost(callback)

        /**
         * Use weak reference to [resultReceiver], prevents result from leaking. This is default.
         * */
        fun useWeakRef(): Builder<Q, R, T> {
            useWeakRef = true
            return this
        }

        /**
         * Use hard reference to [resultReceiver]. This allows task to hold on to disposable objects.
         * */
        fun useHardRef(): Builder<Q, R, T> {
            useWeakRef = false
            return this
        }

        internal fun build() = CoroutineLoaderQuery<Q, R, T>(key).also {
            it.progressCallback = progressCallback
            it.cancellationCallback = cancellationCallback
            it.errorCallback = errorCallback
            it.resultCallback = resultCallback
            it.defaultCallback = defaultCallback
            it.weakRefLostCallback = weakRefLostCallback
            it.resultReceiver = if (useWeakRef) resultReceiver.asWeakRef() else resultReceiver.asHardRef()
        }
    }

    /**
     * Simpler query builder that handles success callback only. No result receiver reference is kept,
     * [Unit] is used as a placeholder to satisfy interface.
     * */
    class NoRefBuilder<Q : Any, R : Any>(private val key: Q) {
        private var resultCallback: CoroutineLoaderResult.Listener<Q, R, Unit>? = null

        /** Result is guaranteed to be called on UI thread. */
        fun result(callback: CoroutineLoaderResult.Listener<Q, R, Unit>): NoRefBuilder<Q, R> {
            resultCallback = callback
            return this
        }

        /** Result is guaranteed to be called on UI thread. */
        inline fun result(crossinline callback: (result: CoroutineLoaderResult<Q, R>) -> Unit): NoRefBuilder<Q, R> =
            result(object :
                CoroutineLoaderResult.Listener<Q, R, Unit> {
                override fun onTaskResult(resultReceiver: Unit, result: CoroutineLoaderResult<Q, R>) =
                    callback(result)
            })

        internal fun build() = CoroutineLoaderQuery<Q, R, Unit>(key).also {
            it.resultCallback = resultCallback
            it.resultReceiver = UnitRef
        }
    }
}