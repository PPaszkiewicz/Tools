package com.github.ppaszkiewicz.tools.coroutines.service

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.github.ppaszkiewicz.kotlin.tools.toolbox.service.DirectBindService
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.lang.IllegalStateException
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

/**
 * Manages queue of requested tasks and performs them asynchronously.
 *
 * - **Control**
 * - Add tasks using [addTaskToQueue], this also starts completing them if worker threads are idle.
 * - Optionally skip queue and ignore task limit with [beginTask].
 * - Cancel/remove tasks from queue with [cancelRunningTask].
 *
 * Tasks are compared using [isSameTask] which by default returns true if intents data is equal.
 *
 * @param R result of tasks
 * */
abstract class QueuedTaskService<R> : DirectBindService(), CoroutineScope {
    companion object {
        @JvmStatic
        val TAG = "QueuedTaskService"
        /** Adds task to the queue. */
        @JvmStatic
        val ACTION_ADD_TASK = "$TAG.ACTION_ADD_TASK"

        /** Begins execution immediately ignoring queue and job count limit. */
        @JvmStatic
        val ACTION_EXECUTE_TASK_NOW = "$TAG.ACTION_EXECUTE_TASK_NOW"

        /** Cancel job matching this intent. */
        @JvmStatic
        val ACTION_CANCEL = "$TAG.ACTION_CANCEL"

        /** Cancel all queued and running jobs. */
        @JvmStatic
        val ACTION_CANCEL_ALL = "$TAG.ACTION_CANCEL_ALL"

        /**
         * Extra (boolean) for [ACTION_ADD_TASK], if this is true
         * then task is put in front of queue instead (respecting job limit)).
         *
         * *default:* false
         * */
        @JvmStatic
        val EXTRA_SKIP_QUEUE = "$TAG.EXTRA_SKIP_QUEUE"

        /**
         * Extra (boolean) for [ACTION_EXECUTE_TASK_NOW], if this is true
         * then this task is discarded if it's already running. Otherwise it replaces
         * the ongoing task.
         *
         * *default:* true
         * */
        @JvmStatic
        val EXTRA_IGNORE_RUNNING = "$TAG.EXTRA_IGNORE_RUNNING"

        /**
         * Extra (String) for [ACTION_CANCEL] or [ACTION_CANCEL_ALL],
         * reason of cancellation passed to callbacks.
         *
         * *default:* null
         * */
        @JvmStatic
        val EXTRA_CANCEL_REASON = "$TAG.EXTRA_CANCEL_REASON"

        /** Cancel reason passed to tasks that are cancelled due to being replaced. */
        @JvmStatic
        val CANCEL_REASON_TASK_REPLACED = "$TAG.CANCEL_REASON_TASK_REPLACED"

        /** Cancel reason passed to tasks that are cancelled due to service destruction. */
        @JvmStatic
        val CANCEL_REASON_SERVICE_CLOSING = "$TAG.CANCEL_REASON_SERVICE_CLOSING"
    }

    /** True if any start requests came (optimizes clean up). */
    var isAsyncInitialized = false
        private set

    /**
     * True if anything is bound to this service.
     *
     * This must be modified within following methods *if* super is not called:
     *
     * 1. [onBind], [onRebind] -> true
     * 2. [onUnbind] -> false
     * */
    var isBound = false
        protected set

    /** If this service is being destroyed prevent starting new tasks. */
    private var isDestroyed = false

    /** Implementation for [CoroutineScope] - supervisor job so others don't get locked when 1 job fails. */
    override val coroutineContext: CoroutineContext = SupervisorJob()

    //lock context for queues, first to initialize so it raises init flag
    internal val lock by lazy {
        isAsyncInitialized = true
        newFixedThreadPoolContext(1, "${this::class.java.name} - CacheContext")
    }
    //worker context
    private val workContext by lazy {
        newFixedThreadPoolContext(
            backgroundThreadCount,
            "${this::class.java.name} - WorkContext"
        )
    }


    /** Coroutine dispatcher that can be used to run on UI thread. */
    val uiContext by lazy { Handler(Looper.getMainLooper()).asCoroutineDispatcher("${this::class.java.name} - UIContext") }

    /** Queue holding the tasks. Use callback methods to manipulate data since they keep it synchronized. */
    private val queue = ArrayList<Intent>()

    /** Array holding ongoing jobs. */
    internal val jobs = ArrayList<TaskServiceJob<R>>()

    /**
     * Amount of threads to perform task calculations in. By default available processors minus 2.
     *
     * Cannot be modified after async tasks had begun.
     * */
    open var backgroundThreadCount = max(1, Runtime.getRuntime().availableProcessors() - 2)
        set(value) {
            if (isAsyncInitialized) throw IllegalStateException("Cannot modify thread count after async tasks begun")
            field = value
        }

    /**
     * Job count that can run simultaneously. Note that calls to [beginTask] ignore this limitation.
     *
     * If changed while async tasks are running this value won't be respected until next item is popped from the queue.
     * */
    open var maxJobCount = max(1, Runtime.getRuntime().availableProcessors() - 2)


    /** Default onStartCommand: handles basic queue actions. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand ${intent?.action} - ${intent?.dataString}")
        when (intent?.action) {
            ACTION_ADD_TASK -> {
                addTaskToQueue(intent, intent.getBooleanExtra(EXTRA_SKIP_QUEUE, false))
            }
            ACTION_EXECUTE_TASK_NOW -> {
                beginTask(intent, intent.getBooleanExtra(EXTRA_IGNORE_RUNNING, true))
            }
            ACTION_CANCEL -> {
                cancel(intent, intent.getStringExtra(EXTRA_CANCEL_REASON))
            }
            ACTION_CANCEL_ALL -> {
                cancelAll(intent.getStringExtra(EXTRA_CANCEL_REASON))
            }
            else -> {
                Log.e(TAG, "onStartCommand: Cannot start the service with ${intent?.action}")
            }
        }
        onStarting(intent)
        return START_NOT_STICKY
    }

    /** Called during [onStartCommand], [intent] is forwarded. */
    open fun onStarting(intent: Intent?) {}

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind $intent")
        isBound = true
        return super.onBind(intent)
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind $intent")
        isBound = true
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        Log.d(TAG, "onUnbind $intent")
        if (isEmpty())
            stopSelf()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isDestroyed = true
        //cancel all jobs, don't bother clearing queue because it will be discarded
        internalCancelAll(
            false,
            CANCEL_REASON_SERVICE_CLOSING
        )
        // reclaim threads
        if (isAsyncInitialized) {
            lock.close()
            workContext.close()
        }
    }

    /** Number of intents awaiting to be processed */
    fun getQueueSize() = queue.size

    /** Number of intents being processed. */
    fun getJobsSize() = jobs.size

    /** Number of queue + jobs. */
    fun getTotalSize() = queue.size + jobs.size

    /** True if given [intent] evaluates [isSameTask] with one of running jobs. */
    fun isRunning(intent: Intent) = jobs.indexOfFirst { isSameTask(intent, it.intent) } > -1

    /**
     * True if something is still being processed.
     *
     * Result guaranteed to be valid only within [lock] context.
     * */
    fun isRunning() = jobs.isEmpty()

    /**
     * True if there's nothing in queue or jobs.
     *
     * Result guaranteed to be valid only within [lock] context.
     * */
    fun isEmpty() = queue.isEmpty() && jobs.isEmpty()

    /** Override to evaluate if queued/running task is equal to request. By default it compares [Intent.getData]. */
    protected open fun isSameTask(newRequest: Intent, oldRequest: Intent): Boolean {
        return newRequest.data == oldRequest.data
    }

    /** Customize created TaskServiceJob objects. Those are created when intent is being processed and perform [TaskServiceJob.doInBackground]. */
    protected abstract fun createTaskServiceJob(intent: Intent): TaskServiceJob<R>

    /**
     * Add Intent to task queue. If no jobs are running this will start calculation. This is main method
     * for using task queue service.
     * */
    fun addTaskToQueue(intent: Intent, skipQueue: Boolean) {
        if (isDestroyed) {
            Log.e("QueTaskService", "addTaskToQueue: cannot start because service is destroyed.")
            return
        }

        //synchronize queue with context
        launch(lock) {
            if (addToQueue(queue, intent, skipQueue))
                launch(uiContext) {
                    onTaskAddedToQueue(intent)
                    onTotalSizeChanged()
                }
            else
                Log.i(TAG, "addTaskToQueue: intent not added to queue because it's already present: $intent")
            // start if no jobs
            startJobFromQueue(this)
        }
    }

    /**
     * Begin task immediately, without waiting in queue.
     * @param intent task to begin
     * @param ignoreIfRunning don't start if this task is already in running jobs. Otherwise cancel
     * the running job and start this one instead.
     * */
    fun beginTask(intent: Intent, ignoreIfRunning: Boolean = false) {
        if (isDestroyed) {
            Log.e("QueTaskService", "beginTask: cannot start because service is destroyed.")
            return
        }
        launch(lock) {
            if (isRunning(intent)) {
                if (ignoreIfRunning)
                    return@launch
                else
                    cancelRunningTask(
                        intent,
                        CANCEL_REASON_TASK_REPLACED
                    )
            }
            removeFromQueue(intent)
            startJob(this, intent)
        }
    }

    /**
     * Pops awaiting job or triggers [allTasksFinished] if empty.
     *
     * Must be ran within [lock] context.
     * */
    internal suspend fun startJobFromQueue(lockScope: CoroutineScope) {
        if (isDestroyed) {
            Log.e("QueTaskService", "startJobFromQueue: cannot start because service is destroyed.")
            return
        }

        // check if there's still some room to start a job
        if (jobs.size < maxJobCount) {
            if (queue.isNotEmpty()) {
                // pop a job from queue
                val i = takeJobFromQueue(queue)
                startJob(lockScope, i)
            } else if (jobs.isEmpty()) {
                // nothing to start and nothing is running
                lockScope.launch(uiContext) {
                    allTasksFinished()
                }
            }
        } else {
            Log.d(
                "QueTaskService",
                "startJobFromQueue: jobs are full: ${jobs.size} / $maxJobCount, queue: ${queue.size}"
            )
        }
    }

    /**
     * Override this function to customize what task will be processed next.
     *
     * Task must be taken and removed from the queue list.
     *
     * By default first item from queue is popped.
     * */
    protected open fun takeJobFromQueue(queue: ArrayList<Intent>): Intent {
        return queue.removeAt(0)
    }

    /** Must be ran on [lock] scope/context. Starts a job now. */
    private suspend fun startJob(lockScope: CoroutineScope, intent: Intent) {
        val job = createTaskServiceJob(intent)
        // inject intent
        job.context = this
        job.intent = intent
        jobs.add(job)
        // use job.intent to release direct intent reference
        //notify on UI that this task started
        val startOk = withContext(uiContext) {
            try {
                job.onTaskStarted(job.intent)
            } catch (ex: Exception) {
                onTaskError(job.intent, ex)
                false
            }
        }
        if (!startOk) {
            // failed to go through onTaskStarted
            // try to pop new job from queue instead
            jobs.remove(job)
            startJobFromQueue(lockScope)
            return
        }

        // job of task is calculated in async thread context
        // calls onTaskComplete to remove this from jobs when it finishes.
        job.setJob(lockScope, workContext) {
            job.coroutineScope = this
            job.doInBackground(job.intent)
        }

        // job list modified, try to pop next job if possible
        startJobFromQueue(lockScope)

        // deploy callback on UI thread
        lockScope.launch(uiContext) {
            val result = try {
                job.await()
            } catch (ex: Exception) {
                // exception in doInBackground is passed to callbacks,
                // no need to handle it here
                null
            }
            if (result != null) {
                job.onTaskFinished(job.intent, result)  // task finished is not allowed to throw exception
                onTotalSizeChanged()
            }
        }
    }

    /**
     *  Adding item to queue.
     *  @return true if queue size was changed (added without removing duplicate)
     *  false if it failed (already in queue or running).
     * */
    private fun addToQueue(queue: ArrayList<Intent>, intent: Intent, skipQueue: Boolean): Boolean {
        // common case - empty queue and jobs
        if (jobs.isEmpty() && queue.isEmpty()) {
            queue.add(intent)
            return true
        }

        // remove duplicate query if needed. This will not interrupt ongoing download
        val removedFromQueue = removeFromQueue(intent)
        // don't add to queue if already running
        if (isRunning(intent))
            return false

        // add to query
        if (skipQueue) {
            queue.add(0, intent)
        } else {
            queue.add(intent)
        }
        return removedFromQueue == null
    }

    /**
     * Removing from queue.
     *
     * @return removed value or null
     * */
    private fun removeFromQueue(intent: Intent): Intent? {
        val dupe = queue.indexOfFirst { isSameTask(intent, it) }
        return if (dupe > -1) {
            queue.removeAt(dupe)
        } else
            null
    }

    /**
     * Internal call to cancel running task.
     *
     * @return if job was actually cancelled
     * */
    @Suppress("ReplaceSingleLineLet")
    private fun cancelRunningTask(intent: Intent, reason: String?): Boolean {
        val runningJob = jobs.find { isSameTask(intent, it.intent) }
        return runningJob?.let {
            it.cancel(CancellationException(reason))
        } ?: false
    }

    /** Internal call when all tasks finish. */
    internal fun allTasksFinished() {
        onAllTasksFinished()
        // everything unbound as well, service can destroy itself
        if (!isBound)
            stopSelf()
    }

    /** Cancel task, remove it from queue and running jobs.*/
    fun cancel(intent: Intent, reason: String?) {
        // prevent async task initialization here if not required
        if (!isAsyncInitialized) {
            allTasksFinished()    // callback to stop foreground
            return
        }
        launch(lock) {
            val removedIntent = removeFromQueue(intent)
            if (removedIntent == null) {
                // not in queue, is it running?
                if (!cancelRunningTask(intent, reason)) //cancel task should trigger all finished from callback
                    if (isEmpty())
                        allTasksFinished()
                // don't explicitly call onTotalSizeChanged, handled in coroutine callback
            } else {
                launch(uiContext) {
                    onTaskRemovedFromQueue(removedIntent)
                    onTotalSizeChanged()
                }
                // removed from queue, if que and jobs are empty call finish.
                if (isEmpty())
                    allTasksFinished()
            }
        }
    }

    /**
     * Cancel all ongoing tasks.
     * */
    fun cancelAll(reason: String?) = internalCancelAll(true, reason)

    /**
     * Cancel all ongoing tasks. Use [clearQueue] to clear queue as well and keep service useable
     * (use false only if service is shutting down and queue will be garbage collected).
     * */
    private fun internalCancelAll(clearQueue: Boolean, reason: String?) {
        if (isAsyncInitialized) {
            launch(lock) {
                val jobsEmpty = isEmpty()
                if (clearQueue)
                    queue.clear()
                CancellingAllException(reason).let { e ->
                    jobs.forEach {
                        it.cancel(e)
                    }
                }
                if (jobsEmpty && !isDestroyed) {
                    // there was no running jobs, so we won't receive any finish callbacks
                    // force call all finished here instead
                    allTasksFinished()
                }
                if (isDestroyed) {
                    // free up threads
                    workContext.close()
                    lock.close()
                }
            }
        } else if (!isDestroyed) {
            // async was not initialized (idle)
            allTasksFinished()
        }
    }


    /** Run the [block] on UI thread. */
    @Suppress("DeferredResultUnused")
    fun runOnUiThread(block: suspend CoroutineScope.() -> Unit) {
        // ignore deferred result - unlike launch async doesn't crash the app
        async(uiContext, block = block)
    }

    /** Called after task is removed from queue without being replaced. ([getQueueSize] changed) */
    open fun onTaskRemovedFromQueue(intent: Intent) {

    }

    /** Callback for when new task was added to queue without replacing an old task. (value of [getQueueSize] changed). */
    open fun onTaskAddedToQueue(intent: Intent) {

    }

    /** Override to handle cancellation by [cancel]. */
    open fun onTaskCancelled(intent: Intent, message: String?, cancellingAll: Boolean) {

    }

    /** Override to handle loading error. */
    open fun onTaskError(intent: Intent, cause: Throwable) {

    }

    /**
     * Triggered when [getTotalSize] changes (new task added or one of tasks finishes, errors or gets cancelled).
     *
     * This is not called after [cancelAll]. Super implementation is empty.
     *
     * @param tasksCount size of queue + running jobs
     **/
    open fun onTotalSizeChanged(tasksCount: Int = getTotalSize()) {}

    /**
     * Called on UI thread when new task is about to start processing.
     *
     * If this method throws an exception, task is discarded and [onTaskError] is called.
     *
     * @return true to proceed, false to discard the task.
     * */
    open fun onTaskStarted(intent: Intent): Boolean = true

    /** Called on main looper thread when task is finished successfully. Super implementation is empty. */
    abstract fun onTaskFinished(intent: Intent, result: R)

    /**
     * Callback when all tasks are finished (nothing runs, nothing in queue).
     *
     * If nothing [isBound], service calls [stopSelf] after this method.
     * */
    open fun onAllTasksFinished() {}
}

/** Special [CancellationException] subclass passed when cancelling all requests at once. */
internal class CancellingAllException(msg: String? = null) : CancellationException(msg)