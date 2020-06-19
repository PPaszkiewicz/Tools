package com.github.ppaszkiewicz.tools.toolbox.viewModel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * LiveData throttling value emissions so they don't happen more often than [delayMs].
 */
class ThrottledLiveData<T>(source: LiveData<T>, delayMs: Long) : MediatorLiveData<T>() {
    val handler = Handler(Looper.getMainLooper())
    var delayMs = delayMs
        private set

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

    /**
     * Stop throttling now. If [immediate] emit any pending value now.
     *
     * If throttling is already stopped this is no-op.
     * */
    fun stopThrottling(immediate: Boolean = false) {
        if(delayMs <= 0) return
        delayMs *= -1
        if (immediate) runPendingDelay()
    }

    /**
     * Start throttling or modify the delay.
     *
     * If [newDelay] is `0` (default) reuse old delay value.
     * */
    fun startThrottling(newDelay: Long = 0L) {
        require(newDelay >= 0L)
        if (newDelay == 0L) when {
            delayMs < 0 -> delayMs *= -1
            delayMs > 0 -> return
            else -> throw IllegalArgumentException("newDelay cannot be zero if old delayMs is zero")
        } else delayMs = newDelay
    }

    override fun onInactive() {
        super.onInactive()
        runPendingDelay()
    }

    private fun startDelay() {
        delayRunnable?.let { handler.removeCallbacks(it) }
        if (delayMs > 0 && hasActiveObservers())
            DelayRunnable().let {
                handler.postDelayed(it, delayMs)
                delayRunnable = it
            }
        else delayRunnable = null
    }

    private fun runPendingDelay() {
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