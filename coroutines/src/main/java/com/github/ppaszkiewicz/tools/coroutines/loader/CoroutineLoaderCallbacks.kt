package com.github.ppaszkiewicz.tools.coroutines.loader

/** Superclass of all callbacks.*/
sealed class CoroutineLoaderCallback<Q : Any, R : Any>(
    /** Query key used by Task. If coroutine loader implements key mutation, this will be altered. */
    val key: Q,
    /** Requested query key unaffected by mutation. */
    val originalKey: Q
) {
    /** Listener that can receive all callbacks. */
    fun interface Listener<Q : Any, R : Any, T : Any> {
        fun onTaskCallback(resultReceiver: T, callback: CoroutineLoaderCallback<Q, R>)
    }

    /**
     * Debug/error handling interface, invoked when weak reference is lost. Otherwise weak ref loss is silent.
     **/
    fun interface OnRefLostListener<Q : Any, R : Any> {
        fun onRefLost(lostCallback: CoroutineLoaderCallback<Q, R>)
    }
}

/**
 * Wrapper of result from [CoroutineLoaderQuery].
 *
 * @param key key of the query
 * @param originalKey key of query unaffected by mutation
 * @param value result of the query
 * @param info extra info of the query
 */
class CoroutineLoaderResult<Q : Any, R : Any> internal constructor(
    key: Q,
    originalKey: Q,
    val value: R,
    val info: Info
) : CoroutineLoaderCallback<Q, R>(key, originalKey) {
    data class Info(
        /** Query finished in onPreExecute without switching any threads. Task was finished in-line. */
        val wasPreExecuted: Boolean = false,
        /** Query was a cache hit and did no calculation. */
        val wasCached: Boolean = false,
        /** This query did no spawn a new task, instead if joined an ongoing one for the request key. */
        val wasJoined: Boolean = false
    ) {
        /**
         * Query was executed - that is referenced task performed its doInBackground method.
         *
         * This implies that both [wasPreExecuted] and [wasCached] are false.
         * */
        val wasExecuted
            get() = !(wasPreExecuted || wasCached)

        /** Not [wasExecuted] - this implies a fast result from onPreExecute. */
        val wasNotExecuted = !wasExecuted
    }

    /*** Listener for result from [CoroutineLoaderTask]. */
    fun interface Listener<Q : Any, R : Any, T : Any> {
        /** [CoroutineLoaderTask] finished, handle result here. */
        fun onTaskResult(resultReceiver: T, result: CoroutineLoaderResult<Q, R>)
    }
}

/**
 * Wrapper of progress from [CoroutineLoaderQuery].
 *
 * @param key key of query
 * @param originalKey key of query unaffected by mutation
 * @param value progress sent from task
 * */
class CoroutineLoaderProgress<Q : Any, R : Any> internal constructor(key: Q, originalKey: Q, var value: Any?) :
    CoroutineLoaderCallback<Q, R>(key, originalKey) {
    /**
     * Listener for progress forwarded from [CoroutineLoaderTask].
     *
     * Progress uses [Any] type due to requirement of generics (and it's only optional interface).*
     * */
    fun interface OnProgressListener<Q : Any, R : Any, T : Any> {
        /** Updated progress of ongoing [CoroutineLoaderTask].         * */
        fun onTaskProgress(resultReceiver: T, progress: CoroutineLoaderProgress<Q, R>)
    }
}

/**
 * Wrapper of errors from [CoroutineLoaderQuery].
 *
 * @param key key of query
 * @param originalKey key of query unaffected by mutation
 * @param exception exception raised in task
 * */
class CoroutineLoaderError<Q : Any, R : Any> internal constructor(key: Q, originalKey: Q, val exception: Throwable?) :
    CoroutineLoaderCallback<Q, R>(key, originalKey) {
    /***Listener for errors from [CoroutineLoaderTask].     */
    fun interface OnErrorListener<Q : Any, R : Any, T : Any> {
        /** [CoroutineLoaderTask] failed, forwarded exception here. */
        fun onTaskError(resultReceiver: T, error: CoroutineLoaderError<Q, R>)
    }
}

/**
 * Wrapper of cancellation from [CoroutineLoaderQuery].
 *
 * @param key key of query
 * @param originalKey key of query unaffected by mutation
 * @param reason cancellation reason
 * @param cancellationType internal type of cancellation
 * */
class CoroutineLoaderCancellation<Q : Any, R : Any> internal constructor(
    key: Q,
    originalKey: Q,
    val reason: String?,
    val cancellationType: CancellationType
) : CoroutineLoaderCallback<Q, R>(key, originalKey) {
    /*** Listener for cancellations from [CoroutineLoaderTask].     */
    fun interface OnCancelListener<Q : Any, R : Any, T : Any> {
        /** [CoroutineLoaderTask] was cancelled, forwarded reason. */
        fun onTaskCancelled(resultReceiver: T, cancellation: CoroutineLoaderCancellation<Q, R>)
    }

}

/** Internal cancellation type, lets user know of state loader/task is after cancellation. */
enum class CancellationType {
    /** Query was cancelled but task itself is still alive. */
    QUERY,
    /** Task was cancelled. */
    TASK,
    /** Loader is cancelling all ongoing tasks.*/
    LOADER;

    companion object {
        fun from(isTaskCancelled: Boolean, isCancellingAll: Boolean): CancellationType {
            return when {
                isCancellingAll -> LOADER
                isTaskCancelled -> TASK
                else -> QUERY
            }
        }

        fun from(isCancellingAll: Boolean): CancellationType {
            return if (isCancellingAll) LOADER else TASK
        }
    }
}