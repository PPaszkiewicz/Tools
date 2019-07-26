package com.github.ppaszkiewicz.kotlin.tools.toolbox.extensions

import android.view.View


/** Throttle that will drop clicks. */
@PublishedApi
internal const val CLICK_THROTTLE_MS = 500L

/** Throttle button clicks - crude way to wait for fragmentmanager and activities to catch up. */
@PublishedApi
internal var lastThrottleClickTimestamp = 0L

/**
 * Invoke [block] if global throttle allows it. This is used for buttons that start activities
 * or create fragments to prevent multi clicks.
 *
 * @return true if [block] was invoked and click timestamp was touched, false if [block] was ignored
 * */
inline fun withGlobalThrottle(block : () -> Unit) : Boolean{
    System.currentTimeMillis().let {
        return if (it - lastThrottleClickTimestamp > CLICK_THROTTLE_MS) {
            lastThrottleClickTimestamp = it
            block()
            true
        }else false
    }
}

/** Click listener that shares a global throttle for listener invocation. */
fun View.setThrottledOnClickListener(onClick : (View) -> Unit) = setOnClickListener {
    withGlobalThrottle { onClick(it) }
}