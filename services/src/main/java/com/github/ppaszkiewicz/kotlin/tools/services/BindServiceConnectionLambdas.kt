package com.github.ppaszkiewicz.kotlin.tools.services

/**
 * Callback interface for [BindServiceConnection] that defines hot pluggable lambdas instead of methods.
 * */
interface BindServiceConnectionLambdas<T> {
    /**
     * Called right before [onConnect] when this is first time `bind` call resulted in successful
     * connection to a service or service object changed.
     */
    var onFirstConnect: ((service: T) -> Unit)?

    /**
     * Triggered when service is connected or reconnected.
     * */
    var onConnect: ((service: T) -> Unit)?

    /**
     * Triggered when service is disconnected.
     *
     * To handle lost connection cases provide [onConnectionLost] callback.
     **/
    var onDisconnect: ((service: T) -> Unit)?

    /**
     * Triggered when service connection is lost due to [ServiceConnection.onServiceDisconnected].
     *
     * In most cases it should be impossible to trigger for services running in same process bound with
     * [Context.BIND_AUTO_CREATE].
     *
     * Return `true` to consume callback or [onDisconnect] will be called afterwards.
     **/
    var onConnectionLost: ((service: T) -> Boolean)?

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
     * __Works natively from API level 28.__ For lower versions compat behavior applies (triggered after [onConnectionLost]).
     */
    var onBindingDied: (() -> Boolean)?

    /**
     * Called when [ServiceConnection.onNullBinding] occurs.
     *
     * **Works only from API level 26.**
     */
    var onNullBinding: (() -> Unit)?

    /**
     * Called when internal [Context.bindService] fails.
     *
     * Default behavior is to immediately throw the [exception] as it usually indicates wrong
     * binding intent.
     */
    var onBindingFailed: ((exception: BindServiceConnection.BindingException) -> Unit)

    /** Default implementation that has all possible callbacks null. */
    open class Default<T> : BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((service: T) -> Unit)? = null
        override var onConnect: ((service: T) -> Unit)? = null
        override var onDisconnect: ((service: T) -> Unit)? = null
        override var onConnectionLost: ((service: T) -> Boolean)? = null
        override var onBind: (() -> Unit)? = null
        override var onUnbind: (() -> Unit)? = null
        override var onBindingDied: (() -> Boolean)? = null
        override var onNullBinding: (() -> Unit)? = null
        override var onBindingFailed: ((exception: BindServiceConnection.BindingException) -> Unit) =
            {
                throw it
            }
    }

    /**
     * Adapter with callback lambdas preset to delegate to overrideable methods of [BindServiceConnectionCallbacks].
     *
     * Note that modifying any lambda will prevent [impl] methods from being called.
     * */
    open class Adapter<T>(
        val impl: BindServiceConnectionCallbacks<T> = BindServiceConnectionCallbacks.Adapter()
    ) : BindServiceConnectionCallbacks<T> by impl, BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((service: T) -> Unit)? = ::onFirstConnect
        override var onConnect: ((service: T) -> Unit)? = ::onConnect
        override var onDisconnect: ((service: T) -> Unit)? = ::onDisconnect
        override var onConnectionLost: ((service: T) -> Boolean)? = ::onConnectionLost
        override var onBind: (() -> Unit)? = ::onBind
        override var onUnbind: (() -> Unit)? = ::onUnbind
        override var onBindingDied: (() -> Boolean)? = ::onBindingDied
        override var onNullBinding: (() -> Unit)? = ::onNullBinding
        override var onBindingFailed: ((exception: BindServiceConnection.BindingException) -> Unit) =
            ::onBindingFailed
    }

    /** Delegates everything to modifiable [c] object. */
    class Proxy<T>(var c: BindServiceConnectionLambdas<T> = Default()) :
        BindServiceConnectionLambdas<T> {
        override var onFirstConnect: ((service: T) -> Unit)?
            get() = c.onFirstConnect
            set(value) {
                c.onFirstConnect = value
            }
        override var onConnect: ((service: T) -> Unit)?
            get() = c.onConnect
            set(value) {
                c.onConnect = value
            }
        override var onDisconnect: ((service: T) -> Unit)?
            get() = c.onDisconnect
            set(value) {
                c.onDisconnect = value
            }
        override var onConnectionLost: ((service: T) -> Boolean)?
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
        override var onBindingFailed: ((exception: BindServiceConnection.BindingException) -> Unit)
            get() = c.onBindingFailed
            set(value) {
                c.onBindingFailed = value
            }
    }
}