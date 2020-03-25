package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.view.View

/** Throttle that calls more frequent than [frequencyMs] (for example multiple button clicks that open activities or fragments). */
class Throttle(val frequencyMs : Long = CLICK_THROTTLE_MS){
    companion object{
        /** Default throttle frequency that will drop clicks. */
        const val CLICK_THROTTLE_MS = 500L

        /** Default global throttle. */
        val GLOBAL = Throttle()
    }

    var lastActionTimestamp = 0L
        private set

    /** Check if [frequencyMs] passed since last time this returned true. */
    fun check() = System.currentTimeMillis().let {
            if(it - lastActionTimestamp > frequencyMs){
                lastActionTimestamp = it
                true
            } else false
        }
}

/**
 * Invoke [block] if throttle allows it. This is used for buttons that start activities
 * or create fragments to prevent multi clicks. Uses [Throttle.GLOBAL] by default.
 *
 * @return true if [block] was invoked and timestamp was updated, false if [block] was ignored
 * */
inline fun withThrottle(throttle: Throttle = Throttle.GLOBAL, block : () -> Unit) : Boolean{
    return if(throttle.check()){
        block()
        true
    } else false
}

/** Click listener that uses a throttle for listener invocation. Uses [Throttle.GLOBAL] by default. */
inline fun View.setThrottledOnClickListener(throttle: Throttle = Throttle.GLOBAL, crossinline onClick : (View) -> Unit) = setOnClickListener {
    if(throttle.check()) onClick(it)
}