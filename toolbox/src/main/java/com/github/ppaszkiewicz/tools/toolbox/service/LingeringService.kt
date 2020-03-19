package com.github.ppaszkiewicz.tools.toolbox.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.delegate.contextDelegate
import com.github.ppaszkiewicz.tools.toolbox.service.DirectServiceConnection.BindingMode.*

/*
 *   Requires DirectBindService.kt
 * */

/**
 * [DirectBindService] that gets automatically stopped when it's unbound for [serviceTimeoutMs].
 *
 * Also implements following lifecycle:
 * - [onCreate] = [Lifecycle.State.CREATED] (onCreate)
 * - [onBind] = [Lifecycle.State.RESUMED] (onStart -> onResume)
 * - [onServiceTimeoutStarted] = [Lifecycle.State.STARTED] (onPause)
 * - [onDestroy] = [Lifecycle.State.DESTROYED] (onStop -> onDestroy)
 */
abstract class LingeringService : DirectBindService.Impl(), LifecycleOwner {
    companion object {
        const val TAG = "LingeringService"

        /**
         * Default time before service self destructs. This happens if user pauses activity for
         * that long and doesn't reconnect.
         * */
        const val TIMEOUT_MS = 5000L

        /** Begin timeout for this service. */
        const val ACTION_LINGERING_SERVICE_START_LINGER =
            "$TAG.ACTION_LINGERING_SERVICE_START_LINGER"
    }

    private val mLifecycle = LifecycleRegistry(this)

    override fun getLifecycle(): Lifecycle = mLifecycle

    /** Milliseconds before self stop occurs after no client is bound. */
    var serviceTimeoutMs: Long = TIMEOUT_MS
        protected set

    private val timeoutHandler = Handler()

    private val timeoutRunnable = object : Runnable {
        override fun run() {
            timeoutHandler.removeCallbacks(this)
            onServiceTimeoutFinished()
            stopSelf()
        }
    }

    /** If this is true, service should linger for [serviceTimeoutMs] before stopping self. */
    var isLingeringAllowed = true
        private set

    /** Disable [isLingeringAllowed] to kill service instantly on next unBind. */
    fun setPreventLinger() {
        isLingeringAllowed = false
    }

    override fun onCreate() {
        super.onCreate()
        mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onDestroy() {
        // destroying will trigger onStop as well
        mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        handleBind()
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isLingeringAllowed) {
            stopSelf()
        }
        //otherwise this service should be STARTED (by LingeringServiceConnection)
        //so it doesn't get destroyed until timeout
        return isLingeringAllowed
    }

    override fun onRebind(intent: Intent?) {
        handleBind()
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LINGERING_SERVICE_START_LINGER) {
            timeoutHandler.postDelayed(timeoutRunnable, serviceTimeoutMs)
            onServiceTimeoutStarted()
            mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        return START_STICKY
    }

    /** No clients bound, timeout starting. */
    open fun onServiceTimeoutStarted() {

    }

    /** No clients bounds and timeout finished, service will call [stopSelf] after this. */
    open fun onServiceTimeoutFinished() {

    }

    /** On bind and rebind. */
    private fun handleBind() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        isLingeringAllowed = true
        stopSelf()
        // go to resumed state (if onStart was not triggered before it will be called as well)
        mLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}

/**
 * Connection that should be used with [LingeringService].
 *
 * 1. [Companion.observe] will create connection that causes service to always linger.
 * 2. [Companion.liveData] will create connection that causes service to always linger (based on observer state).
 * 3. [Companion.create] requires manual binding handling in activity start/stop (described below):
 *
 * - call [bind] when needed (usually during onStart)
 * - call [unbind] with false to stop service after a delay (in onPause)
 * - call [unbind] with true to force stop service instantly (for example in onFinish)
 * */
open class LingeringServiceConnection<T : LingeringService>(
    contextDelegate: ContextDelegate,
    serviceClass: Class<T>,
    bindingMode: BindingMode
) : DirectServiceConnection<T>(contextDelegate, serviceClass, bindingMode) {
    companion object {
        /**
         * Observe connection for a given service class. This will be bound to activity lifecycle,
         * so service will always linger for given delay.
         * */
        inline fun <reified T : LingeringService> observe(
            activity: AppCompatActivity,
            bindingEvents: BindingEvents = BindingEvents.START_STOP
        ) = observe<T>(activity.contextDelegate, activity.lifecycle, bindingEvents)

        /**
         * Observe connection for a given service class. This will be bound to fragments lifecycle,
         * so service will always linger for given delay.
         * */
        inline fun <reified T : LingeringService> observe(
            fragment: Fragment,
            bindingEvents: BindingEvents = BindingEvents.START_STOP
        ) = observe<T>(fragment.contextDelegate, fragment.lifecycle, bindingEvents)

        /**
         * Observe connection for a given service class. This will be bound to given lifecycle,
         * so service will always linger for given delay.
         * */
        inline fun <reified T : LingeringService> observe(
            contextDelegate: ContextDelegate,
            lifecycle: Lifecycle,
            bindingEvents: BindingEvents = BindingEvents.START_STOP
        ) = LingeringServiceConnection(contextDelegate, T::class.java, LIFECYCLE).apply {
            bindingLifecycleEvents = bindingEvents
            lifecycle.addObserver(this)
        }

        /**
         * Create connection for a given lingering service class, it will be bound when there are active observers.
         * */
        inline fun <reified T : LingeringService> liveData(context: Context) =
            LingeringServiceConnection(context.contextDelegate, T::class.java, LIVEDATA)

        /**
         * Create connection for a given lingering service class, it will be bound when there are active observers.
         * */
        inline fun <reified T : LingeringService> liveData(fragment: Fragment) =
            LingeringServiceConnection(fragment.contextDelegate, T::class.java, LIVEDATA)

        /**
         * Create connection for a given lingering service class. See [LingeringServiceConnection] how to control it.
         * */
        inline fun <reified T : LingeringService> create(context: Context) =
            LingeringServiceConnection(context.contextDelegate, T::class.java, MANUAL)

        /**
         * Create connection for a given lingering service class. See [LingeringServiceConnection] how to control it.
         * */
        inline fun <reified T : LingeringService> create(fragment: Fragment) =
            LingeringServiceConnection(fragment.contextDelegate, T::class.java, MANUAL)
    }

    // prevent super call because we need extra argument
    override fun performUnbind() = unbind(false)

    /**
     * Unbind from service (requesting a disconnect).
     *
     * Raise [finishImmediately] to finish service without a delay (for example when activity
     * is finishing).
     * */
    fun unbind(finishImmediately: Boolean = false) {
        //Log.d("Lingering", "unbind: $finishImmediately")
        if (isBound) {
            if (finishImmediately) {
                value?.setPreventLinger()
            } else {
                // let service self-start, so it won't get instantly killed due to unbind
                // service implements delayed stopSelf() if it's not rebound soon.
                Intent(context, serviceClass)
                    .setAction(LingeringService.ACTION_LINGERING_SERVICE_START_LINGER)
                    .let { context.startService(it) }
            }
            isBound = false
            context.unbindService(this)
            onUnbind?.invoke()
        }
    }
}