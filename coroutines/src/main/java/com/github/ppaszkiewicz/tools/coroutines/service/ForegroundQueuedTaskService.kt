package com.github.ppaszkiewicz.tools.coroutines.service

import android.content.Intent

/**
 * [QueuedTaskService] with abstract methods for controlling foreground state.
 *
 * When using this as superclass service must always be started in foreground using [startForegroundService]
 * (required since android O).
 * */
abstract class ForegroundQueuedTaskService<R : Any> : QueuedTaskService<R>() {
    /** Checks if this service is in foreground - should always be equal to [isRunning]. */
    var isInForeground = false
        private set


    final override fun onStarting(intent: Intent?) {
        isInForeground = true
        doStartForeground(intent)
    }

    override fun onTotalSizeChanged(tasksCount: Int) {
        doModifyForegroundNotification(tasksCount, null)
    }

    override fun onTaskStarted(intent: Intent): Boolean {
        doModifyForegroundNotification(getTotalSize(), intent)
        return super.onTaskStarted(intent)
    }

    final override fun onAllTasksFinished() {
        isInForeground = false
        doStopForeground()
    }


    /**
     * Call [startForeground] inside this method. [intent] is forwarded from [onStartCommand].
     *
     * This is required even if service is going to self-stop instantly (when nothing is bound and intent doesn't start
     * a task) to fulfill foreground promise set by [startForegroundService].
     * */
    abstract fun doStartForeground(intent: Intent?)

    /**
     * Modify displayed notification after amount of tasks changed. If [startingIntent] is provided, this was called
     * after new task has begun. If [tasksCount] is 1, this implies [startingIntent] is the only job present.
     * */
    abstract fun doModifyForegroundNotification(tasksCount: Int, startingIntent: Intent?)

    /** Call [stopForeground] inside this method. This is called instead of [onAllTasksFinished]. */
    abstract fun doStopForeground()

    // feel free to add custom method that will modify notification (on ui thread)
    // for example display progress from ongoing tasks
}