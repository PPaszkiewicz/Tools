package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.github.ppaszkiewicz.tools.toolbox.service.DirectBindService
import com.github.ppaszkiewicz.tools.toolbox.service.LingeringService

/** Demo for [LingeringService] and [DirectBindService]. */
class DemoLingeringService : LingeringService(){
    companion object{
        const val TAG = "DEMO_SERVICE"
    }

    init {
        // reduce timeout to 2 sec down from default 5
        serviceTimeoutMs = 2000L
        //debug...
        this.lifecycle.addObserver(object : LifecycleObserver{
            @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
            fun onLifecycleEvent(source : LifecycleOwner, event: Lifecycle.Event) {
                Log.d(TAG, "observer, event is $event")
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        Toast.makeText(this, "service created", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        Toast.makeText(this, "service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceTimeoutStarted() {
        Log.d(TAG, "onServiceTimeoutStarted: ${serviceTimeoutMs}ms")
    }

    override fun onServiceTimeoutFinished() {
        Log.d(TAG, "onServiceTimeoutFinished")
    }
}