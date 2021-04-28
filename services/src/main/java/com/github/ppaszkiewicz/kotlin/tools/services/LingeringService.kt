package com.github.ppaszkiewicz.kotlin.tools.services

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.delegate.contextDelegate

/**
 * [DirectBindService] that gets automatically stopped when it's unbound for [serviceTimeoutMs].
 *
 * Also implements following lifecycle:
 * - [onCreate] = [Lifecycle.State.CREATED] (onCreate)
 * - [onBind] = [Lifecycle.State.RESUMED] (onStart -> onResume)
 * - [onServiceTimeoutStarted] = [Lifecycle.State.STARTED] (onPause)
 * - [onDestroy] = [Lifecycle.State.DESTROYED] (onStop -> onDestroy)
 *
 * Use [LingeringService.ConnectionFactory] object to build valid connection objects.
 */
abstract class LingeringService : DirectBindService.Impl(), LifecycleOwner {
    private val mLifecycle = LifecycleRegistry(this)

    override fun getLifecycle(): Lifecycle = mLifecycle

    /** Milliseconds before self stop occurs after no client is bound. */
    var serviceTimeoutMs: Long = TIMEOUT_MS
        protected set

    private val timeoutHandler = Handler(Looper.getMainLooper())

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

    override fun onBind(intent: Intent): IBinder? {
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

        /** Create [ConnectionFactory] for [LingeringService] of class [T]. */
        inline fun <reified T : LingeringService> ConnectionFactory() =
            ConnectionFactory(T::class.java)
    }

    /**
     * Connection factory that creates default connections to [serviceClass].
     *
     * For convenience this can be inherited or created by that services companion object.
     */
    open class ConnectionFactory<T : LingeringService>(protected val serviceClass: Class<T>) {
        /** Create [LingeringLifecycleServiceConnection] - this uses activity lifecycle to connect to service automatically, so
         * it will always linger for given delay. */
        fun lifecycle(
            activity: AppCompatActivity,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = attach(activity, lifecycle(activity.contextDelegate, bindState))

        /** Create [LingeringLifecycleServiceConnection] - this uses fragment lifecycle to connect to service automatically, so
         * it will always linger for given delay. */
        fun lifecycle(
            fragment: Fragment,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = attach(fragment, lifecycle(fragment.contextDelegate, bindState))

        /** Create [LingeringLifecycleServiceConnection] - this can observe lifecycle to connect to service automatically, so
         * it will always linger for given delay. */
        fun lifecycle(
            contextDelegate: ContextDelegate,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = LingeringLifecycleServiceConnection(contextDelegate, serviceClass, bindState)

        /** Create [LingeringObservableServiceConnection], it will be bound when there are active observers and
         * linger when last observer disconnects. */
        fun observable(context: Context) =
            LingeringObservableServiceConnection(context.contextDelegate, serviceClass)

        /** Create [LingeringObservableServiceConnection], it will be bound when there are active observers and
         * linger when last observer disconnects. */
        fun observable(fragment: Fragment) =
            LingeringObservableServiceConnection(fragment.contextDelegate, serviceClass)

        /** Create manual connection. See [LingeringManualServiceConnection] how to control it. */
        fun manual(context: Context) =
            LingeringManualServiceConnection(context.contextDelegate, serviceClass)

        /** Create manual connection. See [LingeringManualServiceConnection] how to control it. */
        fun manual(fragment: Fragment) =
            LingeringManualServiceConnection(fragment.contextDelegate, serviceClass)

        /** Make [conn] observe [lOwner]. */
        protected fun attach(lOwner: LifecycleOwner, conn: LingeringLifecycleServiceConnection<T>) =
            conn.apply { lOwner.lifecycle.addObserver(this) }
    }
}

/**
 * Binds to a LingeringService when [bind] and [unbind] are called and provides basic callbacks.
 *
 * Controlling this connection:
 * - call [bind] when needed (usually during onStart)
 * - call [unbind] with false to stop service after a delay (in onPause)
 * - call [unbind] with true to force stop service instantly (for example in onFinish)
 */
open class LingeringManualServiceConnection<T : LingeringService>(
    contextDelegate: ContextDelegate,
    serviceClass: Class<T>
) : DirectManualServiceConnection<T>(contextDelegate, serviceClass) {
    override fun performUnbind() = unbind(false)
    fun unbind(finishImmediately: Boolean = false) = lingeringUnbind(finishImmediately)
}

/**
 * Binds to a LingeringService when it has active [LiveData] observers and provides basic callbacks.
 */
open class LingeringObservableServiceConnection<T : LingeringService>(
    contextDelegate: ContextDelegate,
    serviceClass: Class<T>
) : DirectObservableServiceConnection<T>(contextDelegate, serviceClass) {
    override fun performUnbind() = unbind(false)
    fun unbind(finishImmediately: Boolean = false) = lingeringUnbind(finishImmediately)
}

/**
 * Binds to a LingeringService based on lifecycle and provides basic callbacks.
 */
open class LingeringLifecycleServiceConnection<T : LingeringService>(
    contextDelegate: ContextDelegate,
    serviceClass: Class<T>,
    bindingLifecycleState: Lifecycle.State
) : DirectLifecycleServiceConnection<T>(contextDelegate, serviceClass, bindingLifecycleState) {
    override fun performUnbind() = unbind(false)
    fun unbind(finishImmediately: Boolean = false) = lingeringUnbind(finishImmediately)
}

// shared implementation
private fun <T : LingeringService> BindServiceConnection<T>.lingeringUnbind(finishImmediately: Boolean = false) {
    if (isBound) {
        if (finishImmediately) {
            value?.setPreventLinger()
        } else {
            // let service self-start, so it won't get instantly killed due to unbind
            // service implements delayed stopSelf() if it's not rebound soon.
            createBindingIntent(context)
                .setAction(LingeringService.ACTION_LINGERING_SERVICE_START_LINGER)
                .let { context.startService(it) }
        }
        isBound = false
        context.unbindService(serviceConnectionObject)
        value?.let{
            _stateLifecycle.currentState = Lifecycle.State.CREATED
            _connectionLifecycle!!.currentState = Lifecycle.State.CREATED
            onDisconnect?.invoke(it)
        }
        onUnbind?.invoke()
    }
}