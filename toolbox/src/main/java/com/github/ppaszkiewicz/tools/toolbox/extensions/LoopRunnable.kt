package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

/**
 * Runnable that keeps re-posting self inside [handler] every [periodMs] until stopped or
 * [runBlock] returns `false`.
 */
open class LoopRunnable(
    protected val handler: Handler,
    var periodMs: Long,
    protected val runBlock: () -> Boolean
) : Runnable, LifecycleObserver {
    /**
     * Runnable that keeps re-posting self on main looper every [periodMs] until stopped or
     * [runBlock] returns `false`.
     */
    constructor(periodMs: Long, runBlock: () -> Boolean) : this(
        Handler(Looper.getMainLooper()),
        periodMs,
        runBlock
    )

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    override fun run() {
        handler.removeCallbacks(this)
        if (runBlock()) handler.postDelayed(this, periodMs)
    }

    /** Start repost loop without immediate run. */
    open fun runDelayed() {
        handler.postDelayed(this, periodMs)
    }

    /** Invoke [runBlock] without affecting posting loop. */
    open fun runOnce() = runBlock()

    /** Stop reposting loop. */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    open fun stop() = handler.removeCallbacks(this)

    /** Observe given lifecycle to align update loop with it. */
    fun observe(lifecycleOwner: LifecycleOwner): LoopRunnable {
        lifecycleOwner.lifecycle.addObserver(this)
        return this
    }
}