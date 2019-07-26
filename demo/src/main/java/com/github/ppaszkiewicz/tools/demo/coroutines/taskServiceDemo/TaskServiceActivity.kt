package com.github.ppaszkiewicz.tools.demo.coroutines.taskServiceDemo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.view.children
import androidx.lifecycle.Observer
import com.github.ppaszkiewicz.tools.toolbox.service.DirectServiceConnection
import com.github.ppaszkiewicz.tools.demo.coroutines.TestActivityBase
import com.github.ppaszkiewicz.tools.demo.coroutines.ProgressTextView
import kotlinx.android.synthetic.main.activity_test.*
import kotlin.random.Random

//todo: unstable test
/** Activity running and listening to progress of tasks in a service. */
class TaskServiceActivity : TestActivityBase() {
    companion object {
        const val TAG = "TaskServiceActivity"
    }

    val serviceConnection = DirectServiceConnection.observe<DemoTaskService>(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceConnection.onConnect = ::onConnected
        serviceConnection.onDisconnect = ::onDisconnected

        test_button_1.setOnClickListener { startAllTasks() }
        test_button_2.setOnClickListener { DemoTaskService.cancelAllTasks(this) }
        test_button_3.setOnClickListener {
            serviceConnection.value?.let {service ->
                // force kill service and recreate activity?
                DemoTaskService.cancelAllTasks(this)
                service.testIsActive = false
                service.progressMap.clear(this)
                recreate()
            } ?: run {
                Toast.makeText(this, "Service not connected yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAllTasks() {
        if (serviceConnection.value == null) {
            Toast.makeText(this, "Service not connected yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (serviceConnection.value!!.testIsActive) {
            Toast.makeText(this, "Test already running", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Beginning test", Toast.LENGTH_SHORT).show()

        val r = Random(System.currentTimeMillis())
        // begin tasks in the service
        layTestLoaderContainer.children.forEach { view ->
            // cast to custom progress view
            view as ProgressTextView

            // build params for each task
            val jobParams = JobParams(
                    testActivityParams.taskDurationSeconds + r.nextInt(testActivityParams.taskDurationSecondsVariance),
                    testActivityParams.taskRandomCrashChance
            )

            // if there is more views than tasks, modulo key down
            // in that case taskParams will be silently ignored and request will be ignored (key will match in the service)
            val key = view.tag as Int % testActivityParams.taskCount

            // begin task for given key
            DemoTaskService.loadTask(this, key.toString(), jobParams)
            view.setReady()
        }

        // eveyrhting is setup so raise flag in the service
        serviceConnection.value!!.testIsActive = true
    }

    private fun onConnected(demoTaskService: DemoTaskService) {
        Log.d(TAG, "connected to $demoTaskService")
        if(demoTaskService.testIsActive){
            Toast.makeText(this, "Test already running!!", Toast.LENGTH_SHORT).show()
        }else if(!demoTaskService.isAsyncInitialized){
            // modify service async job and thread count
            demoTaskService.maxJobCount = testActivityParams.maxJobSize
            demoTaskService.backgroundThreadCount = testActivityParams.maxJobSize
        }else{
            if(!demoTaskService.progressMap.isEmpty())
                Toast.makeText(this, "Leaked progress!!", Toast.LENGTH_SHORT).show()
        }
        // attach observers for views
        attachObservers()
    }

    private fun onDisconnected(demoTaskService: DemoTaskService) {
        Log.d(TAG, "disconnected from $demoTaskService")
        // force detach observers
        demoTaskService.progressMap.removeObserversFromMap(this)
    }

    private fun attachObservers(){
        layTestLoaderContainer.children.forEach { view ->
            // cast to custom progress view
            view as ProgressTextView
            // if there is more views than tasks, modulo key down
            // in that case taskParams will be silently ignored and load query will attach to ongoing task for this key
            val key = view.tag as Int % testActivityParams.taskCount

            // attach progress listeners - this is a bit ugly because it's map of livedatas stored in service
            serviceConnection.value!!.let { service ->
                service.progressMap.observeKey(this, key, Observer {
                    val prg = it.progress
                    val err = it.error
                    when {
                        it.result != null -> view.setComplete()
                        err != null -> when (err) {
                            is DemoTaskService.TaskCancelledException -> if (err.isCancellingAll)
                                view.setCancelledAll() else view.setCancelled()
                            else -> view.setError()
                        }
                        prg != null -> view.setProgress(prg)
                        else -> view.setReady()
                    }
                })
            }

            // let user cancel given task by clicking the view
            view.setOnClickListener {
                DemoTaskService.cancelTask(this, key.toString())
            }
        }
    }
}