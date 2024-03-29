package com.github.ppaszkiewicz.tools.services

import android.content.Context
import android.content.ServiceConnection

/**
 * Callback interface for [BindServiceConnection].
 */
interface BindServiceConnectionCallbacks<T> {
    /**
     * Called right before [onConnect] when this is first time `bind` call resulted in successful
     * connection to a service or service object changed.
     */
    fun onFirstConnect(service: T)

    /**
     * Triggered when service is connected or reconnected.
     * */
    fun onConnect(service: T)

    /**
     * Triggered when service is disconnected.
     *
     * To handle lost connection cases provide [onConnectionLost] callback.
     **/
    fun onDisconnect(service: T)

    /**
     * Triggered when service connection is lost due to [ServiceConnection.onServiceDisconnected].
     *
     * In most cases it should be impossible to trigger for services running in same process bound with
     * [Context.BIND_AUTO_CREATE].
     *
     * Return `true` to consume callback or [onDisconnect] will be called afterwards.
     **/
    fun onConnectionLost(service: T): Boolean

    /**
     * Triggered when service binding is requested.
     * */
    fun onBind()

    /**
     * Triggered when service unbinding is requested.
     * */
    fun onUnbind()

    /**
     * Triggered when binding dies.
     *
     * Return `true` to consume callback or [onUnbind] and [onBind] will be called while rebinding.
     *
     * **Works natively from API level 28. For lower versions compat behavior applies (triggered after [onConnectionLost]). **
     */
    fun onBindingDied(): Boolean

    /**
     * Called when [ServiceConnection.onNullBinding] occurs.
     *
     * **Works only from API level 26.**
     */
    fun onNullBinding()

    /**
     * Called when bind succeeded but connection was not established within [BindServiceConnection.Config.notConnectedTimeout].
     *
     * This will happen if connection is __not__ using [Context.BIND_AUTO_CREATE] and service is not started or it's binding
     * to remote process and service is still launching.
     *
     * Even after this is called service might still connect as long as binding is active.
     * */
    fun onNotConnected()

    /**
     * Called when internal [Context.bindService] fails.
     *
     * Default behavior is to immediately throw the [exception] as it usually indicates
     * bad configuration of binding intent.
     */
    fun onBindingFailed(exception: BindServiceConnection.BindingException)

    /** Adapter for selective override. */
    open class Adapter<T> : BindServiceConnectionCallbacks<T> {
        override fun onFirstConnect(service: T) {}
        override fun onConnect(service: T) {}
        override fun onDisconnect(service: T) {}
        override fun onConnectionLost(service: T) = false
        override fun onBind() {}
        override fun onUnbind() {}
        override fun onBindingDied() = false
        override fun onNullBinding() {}
        override fun onNotConnected() {}
        override fun onBindingFailed(exception: BindServiceConnection.BindingException): Unit =
            throw exception
    }
}