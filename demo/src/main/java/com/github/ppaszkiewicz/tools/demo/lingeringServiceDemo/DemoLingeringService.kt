package com.github.ppaszkiewicz.tools.demo.lingeringServiceDemo

import android.util.Log
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
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun onServiceTimeoutStarted() {
        Log.d(TAG, "onServiceTimeoutStarted: ${serviceTimeoutMs}ms")
    }

    override fun onServiceTimeoutFinished() {
        Log.d(TAG, "onServiceTimeoutFinished")
    }
}