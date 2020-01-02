package com.github.ppaszkiewicz.tools.toolbox.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.ppaszkiewicz.tools.toolbox.extensions.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.extensions.contextDelegate

/*
 *   Requires DirectBindService.kt
 * */

/**
 * [DirectBindService] that gets automatically stopped when it's unbound for [serviceTimeoutMs].
 */
abstract class LingeringService : DirectBindService() {
    companion object {
        const val TAG = "LingeringService"
        /**
         * Default time before service self destructs. This happens if user pauses activity for
         * that long and doesn't reconnect.
         * */
        const val TIMEOUT_MS = 5000L
        /** Begin timeout for this service. */
        const val ACTION_LINGERING_SERVICE_START_LINGER = "$TAG.ACTION_LINGERING_SERVICE_START_LINGER"
    }

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

    /** If this is raised, host activity is finishing and service will finish as well. */
    var isFinishing = false
        private set

    /** Raise is finishing flag to kill service instantly on next unBind. */
    fun setIsFinishing() {
        isFinishing = true
    }

    override fun onBind(intent: Intent?): IBinder {
        handleBind()
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isFinishing) {
            stopSelf()
        }
        //otherwise this service should be STARTED (by LingeringServiceConnection)
        //so it doesn't get destroyed until timeout
        return !isFinishing
    }

    override fun onRebind(intent: Intent?) {
        handleBind()
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LINGERING_SERVICE_START_LINGER) {
            timeoutHandler.postDelayed(timeoutRunnable, serviceTimeoutMs)
            onServiceTimeoutStarted()
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
        isFinishing = false
        stopSelf()
    }
}

/**
 * Connection that should be used with [LingeringService].
 *
 * 1. [Companion.observe] will create connection that causes service to always linger.
 * 2. [Companion.create] requires manual binding handling in activity start/stop (described below):
 *
 * - call [bind] when needed (usually during onStart)
 * - call [unbind] with false to stop service after a delay (in onPause)
 * - call [unbind] with true to force stop service instantly (for example in onFinish)
 * */
open class LingeringServiceConnection<T : LingeringService>(
    contextDelegate: ContextDelegate,
    serviceClass: Class<T>
) : DirectServiceConnection<T>(contextDelegate, serviceClass) {
    companion object {
        /**
         * Observe connection for a given service class. This will be bound to activity lifecycle,
         * so service will always linger for given delay.
         * */
        inline fun <reified T : LingeringService> observe(activity: AppCompatActivity) =
            LingeringServiceConnection(activity.contextDelegate, T::class.java).apply {
                activity.lifecycle.addObserver(this)
            }

        /**
         * Observe connection for a given service class. This will be bound to fragments lifecycle,
         * so service will always linger for given delay.
         * */
        inline fun <reified T : LingeringService> observe(fragment: Fragment) =
            LingeringServiceConnection(fragment.contextDelegate, T::class.java).apply {
                fragment.lifecycle.addObserver(this)
            }

        /**
         * Create connection for a given lingering service class. See [LingeringServiceConnection] how to control it.
         * */
        inline fun <reified T : LingeringService> create(context: Context) =
            LingeringServiceConnection(context.contextDelegate, T::class.java)

        /**
         * Create connection for a given lingering service class. See [LingeringServiceConnection] how to control it.
         * */
        inline fun <reified T : LingeringService> create(fragment: Fragment) =
            LingeringServiceConnection(fragment.contextDelegate, T::class.java)
    }

    // Block super implementation.
    override fun unbind() = unbind(false)

    /**
     * Disconnect/unbind from service.
     *
     * Provide [isFinishing] from activity to finish service without a delay.
     * */
    fun unbind(isFinishing: Boolean = false) {
        Log.d("Lingering", "unbind: $isFinishing")
        if (isBound) {
            if (isFinishing) {
                value?.setIsFinishing()
            } else {
                // let service self-start, so it won't get instantly killed due to unbind
                // service implements delayed stopSelf() if it's not rebound soon.
                Intent(context, serviceClass)
                    .setAction(LingeringService.ACTION_LINGERING_SERVICE_START_LINGER)
                    .let { context.startService(it) }
            }
            isBound = false
            context.unbindService(this)
        }
    }
}