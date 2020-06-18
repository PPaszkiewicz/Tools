package com.github.ppaszkiewicz.tools.toolbox.viewModel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * LiveData throttling value emissions so they don't happen more often than [delayMs].
 */
class ThrottledLiveData<T>(source: LiveData<T>, var delayMs: Long) : MediatorLiveData<T>() {
    private val handler = Handler(Looper.getMainLooper())
    private var isValueDelayed = false
    private var delayedValue: T? = null

    private var delayRunnable: Runnable? = null

    init {
        addSource(source) { newValue ->
            if (delayRunnable == null) {
                value = newValue
                startDelay()
            } else {
                isValueDelayed = true
                delayedValue = newValue
            }
        }
    }

    /** Stop throttling now. If [immediate] emit any pending value now. */
    fun stopThrottling(immediate : Boolean = false) {
        delayMs = 0
        if(immediate) runPendingDelay()
    }

    override fun onInactive() {
        super.onInactive()
        runPendingDelay()
    }

    private fun startDelay(){
        delayRunnable?.let {handler.removeCallbacks(it)}
        if (delayMs > 0)
            DelayRunnable().let {
                handler.postDelayed(it, delayMs)
                delayRunnable = it
            }
        else delayRunnable = null
    }

    private fun runPendingDelay(){
        delayRunnable?.let {
            handler.removeCallbacks(it)
            it.run()
        }
    }

    private inner class DelayRunnable : Runnable {
        override fun run() {
            delayRunnable = null
            if (isValueDelayed) {
                value = delayedValue
                delayedValue = null
                isValueDelayed = false
                startDelay()
            }
        }
    }
}