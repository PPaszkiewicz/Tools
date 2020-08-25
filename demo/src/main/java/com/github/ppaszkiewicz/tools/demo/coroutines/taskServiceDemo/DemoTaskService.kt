package com.github.ppaszkiewicz.tools.demo.coroutines.taskServiceDemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import com.github.ppaszkiewicz.tools.coroutines.service.QueuedTaskService
import com.github.ppaszkiewicz.tools.coroutines.service.TaskServiceJob
import com.github.ppaszkiewicz.tools.demo.coroutines.taskServiceDemo.DemoTaskService.Companion.EXTRA_PARAMS
import com.github.ppaszkiewicz.tools.toolbox.service.DirectBindService
import com.github.ppaszkiewicz.tools.toolbox.liveData.LiveDataProgressMap
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

class DemoTaskService : QueuedTaskService<Boolean>() {
    /**
     * Companion contains static helpers for building intent.
     * */
    companion object {
        const val TAG = "DemoTaskService"
        const val EXTRA_PARAMS = "EXTRA_PARAMS"

        /** Connection factory to this service.  */
        val connectionFactory = DirectBindService.ConnectionFactory<DemoTaskService>()

        fun loadTask(context: Context, key: String, params: JobParams) =
            startServiceImpl(context, ACTION_ADD_TASK, key, params)

        fun cancelTask(context: Context, key: String) = startServiceImpl(context, ACTION_CANCEL, key)

        fun cancelAllTasks(context: Context) = startServiceImpl(context, ACTION_CANCEL_ALL)

        private fun startServiceImpl(
            context: Context,
            action: String,
            key: String? = null,
            params: JobParams? = null
        ) {
            Intent(context, DemoTaskService::class.java)
                .setAction(action)
                .apply {
                    key?.let { setData(Uri.parse(it)) }
                    params?.let { putExtra(EXTRA_PARAMS, it) }
                }
                .let { context.startService(it) }
        }
    }

    /** Checked by client after binding. */
    var testIsActive = false

    /** Store progresses here. This is for demo purposes only. */
    // field like this should not exist in production, service should update database/etc
    // and activities should
    val progressMap = LiveDataProgressMap<Int, Float, Boolean>()

    override fun createTaskServiceJob(intent: Intent): TaskServiceJob<Boolean> {
        return DemoServiceJob()
    }

    override fun onTaskFinished(intent: Intent, result: Boolean) {
        Log.d(TAG, "onTaskFinished: ${intent.dataString}")
        // callback to modify database etc. on UI thread
        if(testIsActive)
            progressMap.setResult(intent.dataString.toInt(), result)
    }

    override fun onTaskCancelled(intent: Intent, message: String?, cancellingAll: Boolean) {
        Log.d(TAG, "onTaskCancelled: ${intent.dataString}")
        if(testIsActive)
            progressMap.setError(intent.dataString.toInt(), TaskCancelledException(cancellingAll, message))
    }

    override fun onTaskError(intent: Intent, cause: Throwable) {
        Log.e(TAG, "onTaskError: ${intent.dataString} $cause")
        if(testIsActive)
            progressMap.setError(intent.dataString.toInt(), cause)
    }

    override fun onDestroy() {
        super.onDestroy()
        progressMap.clear()
    }

    class TaskCancelledException(val isCancellingAll : Boolean, message: String? = null) : CancellationException(message)
}

/** Params for job. */
@Parcelize
class JobParams(val duration: Int, val crashChance: Float) : Parcelable

class DemoServiceJob : TaskServiceJob<Boolean>() {
    companion object {
        const val TAG = "DemoServiceJob"
    }

    // need to cast context to parent service to refer to it
    // less overhead than injecting Type argument into everything
    val parentService : DemoTaskService
        get() = context as DemoTaskService

    override suspend fun doInBackground(intent: Intent): Boolean {
        Log.d(TAG, "loading in service for ${intent.dataString}")
        val key = intent.dataString.toInt()
        val params = intent.getParcelableExtra<JobParams>(EXTRA_PARAMS)// cast params
        val random = Random(key)
        repeat(params.duration) {
            cancelIfInactive()

            // perform blocking work (dummy - wait)
            // can use blocking functions and they will be handled correctly during cancel
            work {
                Thread.sleep(1000L)
            }
            // crash randomly if possible
            // never crash on first two cycles so user can see a little bit of progress
            if (it > 1 && params.crashChance > 0.0f && random.nextFloat() < params.crashChance) {
                throw RuntimeException("Task $key crashed at $it second (at ${params.crashChance * 100}%)!")
            }

            // post progress (percentage) if haven't crashed
            parentService.progressMap.postProgress(key, (it / params.duration.toFloat()))

            Log.d(TAG, "loading ${intent.dataString} progress: ${(it / params.duration.toFloat())}")
        }
        return true
    }
}