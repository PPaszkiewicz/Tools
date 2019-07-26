package com.github.ppaszkiewicz.tools.demo.coroutines.loaderDemo

import android.util.Log
import com.github.ppaszkiewicz.tools.coroutines.impl.FifoCoroutineLoader
import com.github.ppaszkiewicz.tools.coroutines.impl.FifoCoroutineLoaderTask
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Demo loader performing simple waiting. */
class DemoLoader : FifoCoroutineLoader<Int, Boolean>() {
    // only required method - everything else is default
    override fun createTask(key: Int, params: Any?): FifoCoroutineLoaderTask<Int, Boolean> {
        return DemoTask()
    }
}

/** Params for task. */
class TaskParams(val duration: Int, val crashChance: Float)

/** Task object instantiated by loader. */
class DemoTask : FifoCoroutineLoaderTask<Int, Boolean>() {
    companion object{
        const val TAG = "DemoTask"
    }
    override suspend fun doInBackground(key: Int, params: Any?): Boolean {
        Log.d(TAG, "loading in BG for $key")
        params as TaskParams // cast params
        val random = Random(key)
        repeat(params.duration) {
            cancelIfInactive()

            // perform blocking work (dummy - wait)
            delay(1000L)
            // crash randomly if possible
            // never crash on first two cycles so user can see a little bit of progress
            if (it > 1 && params.crashChance > 0.0f && random.nextFloat() < params.crashChance) {
                throw RuntimeException("Task $key crashed at $it second (at ${params.crashChance*100}%)!")
            }
            // post progress (percentage) if haven't crashed
            postProgress((it / params.duration.toFloat()))
        }
        return true
    }

    override fun onFinish() {
        Log.d(TAG, "finished $key")
    }
}