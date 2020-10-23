package com.github.ppaszkiewicz.kotlin.tools.services

import androidx.annotation.RequiresApi

/**
 * Callback interface for [BindServiceConnection] that defines hot pluggable lambdas instead of methods.
 * */
interface BindServiceConnectionLambdas<T> {
    /**
     * Called right before [onConnect] when this is first time `bind` call resulted in successful
     * connection to a service or service object changed.
     */
    var onFirstConnect: ((T) -> Unit)?

    /**
     * Triggered when service is connected or reconnected.
     * */
    var onConnect: ((T) -> Unit)?

    /**
     * Triggered when service is disconnected.
     *
     * To handle lost connection cases provide [onConnectionLost] callback.
     **/
    var onDisconnect: ((T) -> Unit)?

    /**
     * Triggered when service connection is lost due to [ServiceConnection.onServiceDisconnected].
     *
     * In most cases it should be impossible to trigger for services running in same process bound with
     * [Context.BIND_AUTO_CREATE].
     *
     * Return `true` to consume callback or [onDisconnect] will be called afterwards.
     **/
    var onConnectionLost: ((T) -> Boolean)?

    /**
     * Triggered when service binding is requested.
     * */
    var onBind: (() -> Unit)?

    /**
     * Triggered when service unbinding is requested.
     * */
    var onUnbind: (() -> Unit)?

    /**
     * Triggered when binding dies.
     *
     * Return `true` to consume callback or [onUnbind] and [onBind] will be called while rebinding.
     *
     * **Works only from API level 28.**
     */
    var onBindingDied: (() -> Boolean)?

    /**
     * Called when [ServiceConnection.onNullBinding] occurs.
     *
     * **Works only from API level 26.**
     */
    var onNullBinding: (() -> Unit)?

    /** Default implementation that has all callbacks null. */
    open class Default<T> : BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((T) -> Unit)? = null
        override var onConnect: ((T) -> Unit)? = null
        override var onDisconnect: ((T) -> Unit)? = null
        override var onConnectionLost: ((T) -> Boolean)? = null
        override var onBind: (() -> Unit)? = null
        override var onUnbind: (() -> Unit)? = null
        override var onBindingDied: (() -> Boolean)? = null
        override var onNullBinding: (() -> Unit)? = null
    }

    /**
     * Adapter with callback lambdas preset to delegate to overrideable methods.
     *
     * Note that modifying any lambda will prevent interface method from being called.
     * */
    open class Adapter<T>(impl: BindServiceConnectionCallbacks<T> = BindServiceConnectionCallbacks.Adapter()) :
        BindServiceConnectionCallbacks<T> by impl, BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((T) -> Unit)? = ::onFirstConnect
        override var onConnect: ((T) -> Unit)? = ::onConnect
        override var onDisconnect: ((T) -> Unit)? = ::onDisconnect
        override var onConnectionLost: ((T) -> Boolean)? = ::onConnectionLost
        override var onBind: (() -> Unit)? = ::onBind
        override var onUnbind: (() -> Unit)? = ::onUnbind
        override var onBindingDied: (() -> Boolean)? = ::onBindingDied
        override var onNullBinding: (() -> Unit)? = ::onNullBinding
    }

    /** Delegates everything to modifiable [c] object. */
    class Proxy<T>(var c: BindServiceConnectionLambdas<T> = Default()) :
        BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((T) -> Unit)?
            get() = c.onFirstConnect
            set(value) {
                c.onFirstConnect = value
            }
        override var onConnect: ((T) -> Unit)?
            get() = c.onConnect
            set(value) {
                c.onConnect = value
            }
        override var onDisconnect: ((T) -> Unit)?
            get() = c.onDisconnect
            set(value) {
                c.onDisconnect = value
            }
        override var onConnectionLost: ((T) -> Boolean)?
            get() = c.onConnectionLost
            set(value) {
                c.onConnectionLost = value
            }
        override var onBind: (() -> Unit)?
            get() = c.onBind
            set(value) {
                c.onBind = value
            }
        override var onUnbind: (() -> Unit)?
            get() = c.onUnbind
            set(value) {
                c.onUnbind = value
            }
        override var onBindingDied: (() -> Boolean)?
            get() = c.onBindingDied
            set(value) {
                c.onBindingDied = value
            }
        override var onNullBinding: (() -> Unit)?
            get() = c.onNullBinding
            set(value) {
                c.onNullBinding = value
            }
    }
}