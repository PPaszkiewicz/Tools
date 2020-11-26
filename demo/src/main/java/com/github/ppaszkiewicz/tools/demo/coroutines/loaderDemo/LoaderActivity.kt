package com.github.ppaszkiewicz.tools.demo.coroutines.loaderDemo

import android.os.Bundle
import android.util.Log
import androidx.core.view.children
import com.github.ppaszkiewicz.tools.coroutines.loader.CancellationType
import com.github.ppaszkiewicz.tools.demo.coroutines.ProgressTextView
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityBase
import java.lang.IllegalStateException
import kotlin.random.Random

/**
 * Activity displaying loader functionality.
 *
 * Note that this does not support configuration change so loader is cancelled on device rotation.
 * */
class LoaderActivity : TestActivityBase() {
    companion object{
        const val TAG = "LoaderActivity"
    }
    var demoLoader = DemoLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.testButton1.setOnClickListener { startLoader() }
        binding.testButton2.setOnClickListener { cancelLoader() }
        binding.testButton3.setOnClickListener {
            // to lazy to refresh layout right now, force user to refresh activity
            recreate()
        }
    }

    private fun startLoader() {
        binding.testButton1.isEnabled = false
        if (demoLoader.wasReleased) throw IllegalStateException("unable to start after release")
        val r = Random(System.currentTimeMillis())
        // begin loader and attach views as callbacks
        binding.layTestLoaderContainer.children.forEach {view ->
            // cast to custom progress view
            view as ProgressTextView

            // build params for each task
            val taskParams =
                TaskParams(testActivityParams.taskDurationSeconds + r.nextInt(testActivityParams.taskDurationSecondsVariance),
                    testActivityParams.taskRandomCrashChance)

            // if there is more views than tasks, modulo key down
            // in that case taskParams will be silently ignored and load query will attach to ongoing task for this key
            val key = view.tag as Int % testActivityParams.taskCount

            // start or join task
            val task = demoLoader.loadAsyncWithCallbacks(key, view, taskParams) {
                onResult {
                    setComplete()
                }
                onCancel {
                    if (it.cancellationType == CancellationType.QUERY)
                        setCancelled()
                    else {
                        Log.d(TAG, "task ${it.key} cancelled with ${it.cancellationType.name} -> ${it.reason}")
                        setCancelledAll()
                    }
                }
                onError {
                    setError()
                    Log.e(TAG, "task ${it.key} failed with ${it.exception}")
                }
                onProgress {
                    setProgress(it.value as Float)
                }
            }

            // let user cancel given query by clicking the view
            view.setOnClickListener {
                task.cancel("User Request")
            }

            // let user cancel entire task by long clicking the view
            // this is different from click as multiple queries can be attached to a single task
            view.setOnLongClickListener {
                task.cancel("User Request", true)
                true
            }

            view.setReady()
        }
    }

    private fun cancelLoader() {
        demoLoader.release("Manually cancelling all")
        binding.testButton1.isEnabled = false
        binding.testButton2.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!demoLoader.wasReleased)
            demoLoader.release()
    }
}