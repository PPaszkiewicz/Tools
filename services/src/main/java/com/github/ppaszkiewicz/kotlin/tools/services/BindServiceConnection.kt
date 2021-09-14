package com.github.ppaszkiewicz.kotlin.tools.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.system.Os.bind
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.delegate.contextDelegate
import java.lang.ref.WeakReference

/**
 * Base for bind service connection implementations.
 *
 * Implementing classes need to call [performBind] and [performUnbind] as they see fit and [release]
 * when object hosting this connection is destroyed (after performing last unbind).
 *
 * Implements two lifecycles: [stateLifecycle] and [connectionLifecycle].
 * */
abstract class BindServiceConnection<T> private constructor(
    contextDelegate: ContextDelegate,
    /** Creates binding intent and transforms binder. */
    val connectionProxy: BindServiceConnectionProxy<T>,
    configBuilder: Config.Builder?,
    private val callbacksProxy: BindServiceConnectionLambdas.Proxy<T>
) : LiveData<T?>(), BindServiceConnectionLambdas<T> by callbacksProxy {

    constructor(
        contextDelegate: ContextDelegate,
        connectionProxy: BindServiceConnectionProxy<T>,
        configBuilder: Config.Builder? = null
    ) : this(contextDelegate, connectionProxy, configBuilder, BindServiceConnectionLambdas.Proxy())

    /**
     * Used to determine if [onFirstConnect] should trigger - this is based on the fact that
     * as long as service is alive we will keep receiving exact same binder object regardless
     * of how many times connection rebinds.
     * */
    private var currentBinder: WeakReference<IBinder>? = null


    /** Context provided by delegate, workaround for fragment lazy context initialization. */
    val context by contextDelegate

    /** Holds configuration. */
    @Suppress("LeakingThis")
    val config = configBuilder?.build() ?: getDefaultConfig()

    /** Get default config object if provided configBuilder is `null` - only return final static objects. */
    protected open fun getDefaultConfig(): Config = Config.DEFAULT

    /** Raised if [performBind] was called without matching [performUnbind]. */
    var isBound = false
        internal set

    /** True if service is connected. */
    val isConnected
        get() = value != null

    /** Alias for [getValue]. Returns service (or binder) object if this connection is connected. */
    val service: T?
        get() = value

    // lifecycles
    /** Lifecycle that represents general connection state:
     * - `INITIALIZED` before first [onBind]
     * - `CREATED` after [onBind]
     * - `RESUMED` right before [onFirstConnect] & [onConnect] call
     * - `STOPPED` right before [onDisconnect] call
     * - `DESTROYED` after calling [release]
     * */
    val stateLifecycle: Lifecycle
        get() = _stateLifecycle

    /** Owner of [stateLifecycle]. */
    val stateLifecycleOwner: LifecycleOwner = LifecycleOwner { _stateLifecycle }

    internal val _stateLifecycle = LifecycleRegistry(stateLifecycleOwner)

    /**
     * Represents connection to a particular service object in following way:
     * - `null` before first connection is established
     * - `RESUMED` right before [onFirstConnect] & [onConnect] call
     * - `STOPPED` right before [onDisconnect] call
     * - `DESTROYED` after [dispatchDestroyConnectionLifecycle] which happens:
     *      - before [onConnectionLost]
     *      - before [onFirstConnect] (if service was restarted while unbound and we got new service object)
     *
     * This is purposefully unaware of binding call as (during reconnection) its impossible to determine if we're
     * going to re-connect or connect to a new service.
     * */
    val connectionLifecycle: Lifecycle
        get() {
            return _connectionLifecycle
                ?: throw IllegalStateException("Cannot access connection lifecycle before connection succeeds.")
        }

    /** Owner of [connectionLifecycle]. */
    val connectionLifecycleOwner: LifecycleOwner = LifecycleOwner { connectionLifecycle }

    internal var _connectionLifecycle: LifecycleRegistry? = null

    // modify callbacks

    /** Modify all underlying callbacks to use [callbackInterface]. */
    fun setCallbackInterface(callbackInterface: BindServiceConnectionCallbacks<T>) {
        callbacksProxy.c = BindServiceConnectionLambdas.Adapter(callbackInterface)
    }

    /** Modify all underlying callbacks to use [callbackLambdas]. */
    fun setCallbackLambdas(callbackLambdas: BindServiceConnectionLambdas<T>) {
        callbacksProxy.c = callbackLambdas
    }

    // core implementation

    /** Perform binding during specific triggering event. */
    protected open fun performBind(flags: Int) {
        if (!isBound) {
            isBound = true
            val bindingIntent = connectionProxy.createBindingIntent(context)
            val bindingExc = try {
                if (context.bindService(bindingIntent, serviceConnectionObject, flags)) {
                    _stateLifecycle.currentState = Lifecycle.State.CREATED
                    onBind?.invoke()
                    return
                }
                null
            } catch (exc: SecurityException) {
                exc
            }
            isBound = false
            onBindingFailed(BindingException(bindingIntent, bindingExc))
        }
    }

    /** Perform unbinding after triggering event. */
    protected open fun performUnbind() {
        if (isBound) {
            isBound = false
            context.unbindService(serviceConnectionObject)
            value?.let {
                _stateLifecycle.currentState = Lifecycle.State.CREATED
                _connectionLifecycle!!.currentState = Lifecycle.State.CREATED
                onDisconnect?.invoke(it)
            }
            onUnbind?.invoke()
            if (value != null) value = null
        }
    }

    /** Perform rebind after unexpected binding death. */
    protected open fun performRebind(doCallbacks: Boolean, flags: Int) {
        if (isBound) {
            context.unbindService(serviceConnectionObject)
            if (doCallbacks) onUnbind?.invoke()
            context.bindService(
                connectionProxy.createBindingIntent(context),
                serviceConnectionObject,
                flags
            )
            if (doCallbacks) onBind?.invoke()
        }
    }

    override fun observe(owner: LifecycleOwner, observer: Observer<in T?>) {
        require(owner !== stateLifecycleOwner && owner !== connectionLifecycleOwner) {
            "Invalid LifecycleOwner - service connection is not allowed to observe self."
        }
        super.observe(owner, observer)
    }

    /**
     * Notify this connection it will never be used again (destroy lifecycles to disconnect observers).
     *
     * @throws IllegalStateException if binding is still active
     * */
    fun release() {
        check(!isBound) { "Cannot release while binding is still active" }
        dispatchDestroyLifecycles()
    }

    /**
     * Force [_stateLifecycle] and [connectionLifecycle] to move from `STOPPED` state into `DESTROYED` state and release
     * connection lifecycle object.
     *
     * Use this to forcefully disconnect all listeners that were observing either lifecycle.
     * */
    private fun dispatchDestroyLifecycles() {
        dispatchDestroyConnectionLifecycle(true)
        check(_stateLifecycle.currentState < Lifecycle.State.RESUMED) { "Cannot be called while binding is active" }
        _stateLifecycle.currentState = Lifecycle.State.DESTROYED
    }

    /**
     * Destroy and release connection lifecycle only.
     *
     * @param releaseBinderRef (default: `true`) discard internal weak binder reference. This will force
     * [onFirstConnect] to be called on next connection even if it reconnects to the same service.
     * */
    protected fun dispatchDestroyConnectionLifecycle(releaseBinderRef: Boolean) {
        if (releaseBinderRef) currentBinder = null
        _connectionLifecycle?.let {
            check(it.currentState < Lifecycle.State.RESUMED) { "Cannot be called while binding is active" }
            it.currentState = Lifecycle.State.DESTROYED
            _connectionLifecycle = null
        }
    }

    /** Internal [ServiceConnection] object that's actually registered for binding. **/
    internal val serviceConnectionObject = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            value?.let { service ->
                _stateLifecycle.currentState = Lifecycle.State.CREATED
                _connectionLifecycle!!.currentState = Lifecycle.State.CREATED
                dispatchDestroyConnectionLifecycle(true)
                val callbackConsumed = onConnectionLost?.let { it(service) }
                if (callbackConsumed != true) onDisconnect?.invoke(service)
                value = null
            } ?: Log.e(
                "BindServiceConn", "unexpected onServiceDisconnected: service object missing. " +
                        "Connection: ${this::javaClass.name}, Service name: $name"
            )
            // compat behavior - assume this event killed the binding
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                && config.autoRebindDeadBindingCompat
                && config.autoRebindDeadBinding
            ) {
                onBindingDied(name)
            }
        }

        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
            val hasFreshConnectionLifecycle = if (_connectionLifecycle == null) {
                _connectionLifecycle = LifecycleRegistry(connectionLifecycleOwner); true
            } else false
            var isFirstConnect = false
            val oldBinder = currentBinder?.get()
            connectionProxy.transformBinder(name, serviceBinder).also {
                this@BindServiceConnection.value = it
                if (oldBinder !== serviceBinder) { // this is not reconnecting event
                    if (!hasFreshConnectionLifecycle && currentBinder != null) {
                        Log.w(
                            "BindServiceConn",
                            "service was restarted while connection was unbound - invalidating connection lifecycle"
                        )
                        dispatchDestroyConnectionLifecycle(false)
                        _connectionLifecycle = LifecycleRegistry(connectionLifecycleOwner)
                    }
                    isFirstConnect = true
                    currentBinder = WeakReference(serviceBinder)
                }
                _stateLifecycle.currentState = Lifecycle.State.RESUMED
                _connectionLifecycle!!.currentState = Lifecycle.State.RESUMED
                if (isFirstConnect) onFirstConnect?.invoke(it)
                onConnect?.invoke(it)
            }
        }

        // added in API level 28
        override fun onBindingDied(name: ComponentName?) {
            // this is always triggered after onServiceDisconnected ?
            val callbackConsumed = onBindingDied?.invoke() ?: false
            if (config.autoRebindDeadBinding) {
                performRebind(!callbackConsumed, config.defaultBindFlags)
            } else if (isBound) {
                isBound = false
            }
        }

        // added in API level 26
        override fun onNullBinding(name: ComponentName?) {
            onNullBinding?.invoke()
        }
    }

    /** Holds configuration. */
    open class Config internal constructor(
        /**
         * Bind flags used as when binding (by default [Context.BIND_AUTO_CREATE]).
         * */
        val defaultBindFlags: Int,
        /**
         * Automatically recreate binding using [defaultBindFlags] if [onBindingDied] occurs (default: `true`).
         *
         * This works natively starting from API 28, for lower versions compatibility behavior is
         * enabled by [autoRebindDeadBindingCompat].
         */
        val autoRebindDeadBinding: Boolean,
        /**
         * If [autoRebindDeadBinding] is set enables compat behavior on devices below API 28 - rebind when
         * connection dies.
         */
        val autoRebindDeadBindingCompat: Boolean
    ) {
        companion object {
            val DEFAULT = Builder().build()
        }

        /** Holds arguments for configuration. */
        open class Builder {
            /**
             * Bind flags used as when binding (by default [Context.BIND_AUTO_CREATE]).
             * */
            var defaultBindFlags: Int = Context.BIND_AUTO_CREATE

            /**
             * Automatically recreate binding using [defaultBindFlags] if [onBindingDied] occurs (default: `true`).
             *
             * This works natively starting from API 28, for lower versions compatibility behavior is
             * enabled by [autoRebindDeadBindingCompat].
             */
            var autoRebindDeadBinding: Boolean = true

            /**
             * If [autoRebindDeadBinding] is set enables compat behavior on devices below API 28 - rebind when
             * connection dies.
             */
            var autoRebindDeadBindingCompat: Boolean = true

            internal open fun build() = Config(
                defaultBindFlags, autoRebindDeadBinding, autoRebindDeadBindingCompat
            )
        }
    }

    /** Base for connection factories that connect to service of type [T]. */
    abstract class ConnectionFactory<T> : ConnectionFactoryBase<T,
            BindServiceConnection.Manual<T>,
            BindServiceConnection.Observable<T>,
            BindServiceConnection.LifecycleAware<T>>()

    /** Base of connection factory that can be used if custom connection types are returned. */
    abstract class ConnectionFactoryBase<T,
            MANUAL : Manual<T>,
            OBSERVABLE : Observable<T>,
            LIFECYCLE : LifecycleAware<T>> {
        // required implementations
        /** Create [BindServiceConnection.Manual] to return from [BindServiceConnection.Manual]. */
        abstract fun createManualConnection(
            contextDelegate: ContextDelegate, configBuilder: Config.Builder?
        ): MANUAL

        /** Create [[BindServiceConnection.Observable]] to return from [observable]. */
        abstract fun createObservableConnection(
            contextDelegate: ContextDelegate, configBuilder: Config.Builder?
        ): OBSERVABLE

        /** Create [BindServiceConnection.LifecycleAware] to return from [lifecycle] and [viewLifecycle]. */
        abstract fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            configBuilder: LifecycleAware.Config.Builder? = null
        ): LIFECYCLE

        // default calls
        /** Create [BindServiceConnection.Manual]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(context: Context, configBuilder: Config.Builder? = null) =
            createManualConnection(context.contextDelegate, configBuilder)

        /** Create [BindServiceConnection.Manual]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(fragment: Fragment, configBuilder: Config.Builder? = null) =
            createManualConnection(fragment.contextDelegate, configBuilder)

        /** Create [[BindServiceConnection.Observable]], it will be bound when there are active observers. */
        fun observable(context: Context, configBuilder: Config.Builder? = null) =
            createObservableConnection(context.contextDelegate, configBuilder)

        /** Create [[BindServiceConnection.Observable]], it will be bound when there are active observers. */
        fun observable(fragment: Fragment, configBuilder: Config.Builder? = null) =
            createObservableConnection(fragment.contextDelegate, configBuilder)

        /** Create [BindServiceConnection.LifecycleAware] - this uses given context and lifecycle to connect to service automatically. */
        fun lifecycle(
            contextDelegate: ContextDelegate,
            lifecycleOwner: LifecycleOwner,
            configBuilder: LifecycleAware.Config.Builder? = null
        ) = attach(lifecycleOwner, createLifecycleConnection(contextDelegate, configBuilder))

        /** Create [BindServiceConnection.LifecycleAware] - this uses activity lifecycle to connect to service automatically. */
        fun lifecycle(
            activity: AppCompatActivity,
            configBuilder: LifecycleAware.Config.Builder? = null
        ) = lifecycle(activity.contextDelegate, activity, configBuilder)

        /** Create [BindServiceConnection.LifecycleAware] - this uses fragment lifecycle to connect to service automatically. */
        fun lifecycle(
            fragment: Fragment,
            configBuilder: LifecycleAware.Config.Builder? = null
        ) = lifecycle(fragment.contextDelegate, fragment, configBuilder)

        /**
         * Create [BindServiceConnection.LifecycleAware] that observes view lifecycle of [fragment] when it's ready.
         * */
        fun viewLifecycle(
            fragment: Fragment,
            configBuilder: LifecycleAware.Config.Builder? = null
        ) = attach(
            fragment, fragment.viewLifecycleOwnerLiveData,
            createLifecycleConnection(fragment.contextDelegate, configBuilder)
        )
        // inline overloads with lambda for initializing the config
        /** Create [BindServiceConnection.Manual]. Need to manually call [bind] and [unbind] to connect.*/
        inline fun manual(context: Context, configBuilder: Config.Builder.() -> Unit) =
            manual(context, config(configBuilder))

        /** Create [BindServiceConnection.Manual]. Need to manually call [bind] and [unbind] to connect.*/
        inline fun manual(fragment: Fragment, configBuilder: Config.Builder.() -> Unit) =
            manual(fragment, config(configBuilder))

        /** Create [BindServiceConnection.Observable], it will be bound when there are active observers. */
        inline fun observable(
            context: Context,
            configBuilder: Config.Builder.() -> Unit
        ) = observable(context, config(configBuilder))

        /** Create [BindServiceConnection.Observable], it will be bound when there are active observers. */
        inline fun observable(
            fragment: Fragment,
            configBuilder: Config.Builder.() -> Unit
        ) = observable(fragment, config(configBuilder))

        /** Create [BindServiceConnection.LifecycleAware] - this uses given context and lifecycle to connect to service automatically. */
        inline fun lifecycle(
            contextDelegate: ContextDelegate,
            lifecycleOwner: LifecycleOwner,
            configBuilder: LifecycleAware.Config.Builder.() -> Unit
        ) = lifecycle(contextDelegate, lifecycleOwner, lifecycleConfig(configBuilder))

        /** Create [BindServiceConnection.LifecycleAware] - this uses activity lifecycle to connect to service automatically. */
        inline fun lifecycle(
            activity: AppCompatActivity,
            configBuilder: LifecycleAware.Config.Builder.() -> Unit
        ) = lifecycle(activity, lifecycleConfig(configBuilder))

        /** Create [BindServiceConnection.LifecycleAware] - this uses fragment lifecycle to connect to service automatically. */
        inline fun lifecycle(
            fragment: Fragment,
            configBuilder: LifecycleAware.Config.Builder.() -> Unit
        ) = lifecycle(fragment, lifecycleConfig(configBuilder))

        /**
         * Create [BindServiceConnection.LifecycleAware] that observes view lifecycle of [fragment] when it's ready.
         * */
        inline fun viewLifecycle(
            fragment: Fragment,
            configBuilder: LifecycleAware.Config.Builder.() -> Unit
        ) = viewLifecycle(fragment, lifecycleConfig(configBuilder))

        // internal to attach observers
        /** Make [conn] observe [lOwner]. */
        protected fun attach(lOwner: LifecycleOwner, conn: LifecycleAware<T>) =
            conn.apply { lOwner.lifecycle.addObserver(this) }

        /** Make [conn] observe any lifecycle emitted by [ldOwner] as long as [lOwner] is alive. */
        protected fun attach(
            lOwner: LifecycleOwner,
            ldOwner: LiveData<LifecycleOwner?>,
            conn: LifecycleAware<T>
        ) = conn.apply { ldOwner.observe(lOwner, Observer { it?.lifecycle?.addObserver(this) }) }

        // inline helpers
        @PublishedApi
        internal inline fun lifecycleConfig(f: LifecycleAware.Config.Builder.() -> Unit) =
            LifecycleAware.Config.Builder().apply { f() }

        @PublishedApi
        internal inline fun config(f: Config.Builder.() -> Unit) = Config.Builder().apply { f() }
    }

    /**
     * Thrown when binding to the service fails.
     *
     * @param intent Intent produced by [createBindingIntent] that caused the failure
     * @param rootException If binding returned `false` this is `null`, otherwise this is exception that was thrown
     * */
    class BindingException(
        val intent: Intent,
        val rootException: SecurityException?
    ) : Exception() {
        override val message = "Failed to bind service using $intent: ${failureReasonMessage()}"

        private fun failureReasonMessage() = when (rootException) {
            null -> "bind returned false"
            else -> rootException.message
        }
    }


    /** Connection to service that requires manual [bind] and [unbind] calls. */
    open class Manual<T>(
        contextDelegate: ContextDelegate,
        connectionProxy: BindServiceConnectionProxy<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, connectionProxy, configBuilder) {
        /**
         * Flag that was used for ongoing binding - this might be different from [defaultBindFlags] if
         * user provided custom argument for [bind].
         *
         * If not bound this is -1.
         * */
        var currentBindFlags = -1
            protected set

        /**
         * Bind to service using [connectionFlags] (ignoring [Config.defaultBindFlags]).
         * */
        fun bind(connectionFlags: Int = config.defaultBindFlags) {
            if (!isBound) currentBindFlags = connectionFlags
            performBind(connectionFlags)
        }

        /**
         * Request unbind (disconnect) from service.
         */
        fun unbind() {
            performUnbind()
            currentBindFlags = -1
        }

        override fun performRebind(doCallbacks: Boolean, flags: Int) {
            currentBindFlags = flags
            super.performRebind(doCallbacks, flags)
        }
    }

    /**
     * Connection to service that relies on this object also being a [LiveData]: binds as long as there's any
     * active observer.
     * */
    open class Observable<T>(
        contextDelegate: ContextDelegate,
        connectionProxy: BindServiceConnectionProxy<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, connectionProxy, configBuilder) {
        // override livedata methods
        override fun onActive() {
            performBind(config.defaultBindFlags)
        }

        override fun onInactive() {
            performUnbind()
        }
    }


    /**
     * Connection to service that is a [LifecycleObserver] and aligns binding with [bindingLifecycleState].
     *
     * This automatically calls [release] when observed lifecycle is destroyed.
     * */
    open class LifecycleAware<T>(
        contextDelegate: ContextDelegate,
        connectionProxy: BindServiceConnectionProxy<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, connectionProxy, configBuilder), LifecycleObserver {
        /** Lifecycle state that will trigger the binding. */
        val bindingLifecycleState: Lifecycle.State
            get() = (config as Config).bindingLifecycleState

        @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
        fun onLifecycleEvent(source: LifecycleOwner, event: Lifecycle.Event) {
            when (bindingLifecycleState) {
                Lifecycle.State.STARTED -> when (event) {
                    Lifecycle.Event.ON_START -> performBind(config.defaultBindFlags)
                    Lifecycle.Event.ON_STOP -> performUnbind()
                    else -> Unit // ignored
                }
                Lifecycle.State.RESUMED -> when (event) {
                    Lifecycle.Event.ON_RESUME -> performBind(config.defaultBindFlags)
                    Lifecycle.Event.ON_PAUSE -> performUnbind()
                    else -> Unit // ignored
                }
                Lifecycle.State.CREATED -> when (event) {
                    Lifecycle.Event.ON_CREATE -> performBind(config.defaultBindFlags)
                    Lifecycle.Event.ON_DESTROY -> performUnbind()
                    else -> Unit // ignored
                }
                else -> throw IllegalStateException("invalid bindingLifecycleEvent")
            }
            if (event == Lifecycle.Event.ON_DESTROY) release()
        }

        override fun getDefaultConfig() = Config.DEFAULT

        /** Extended config. */
        open class Config(
            defaultBindFlags: Int,
            autoRebindDeadBinding: Boolean,
            autoRebindDeadBindingCompat: Boolean,
            /**
             * Lifecycle state that will trigger the binding.
             *
             * This has to be either [Lifecycle.State.STARTED], [Lifecycle.State.RESUMED] or [Lifecycle.State.CREATED].
             */
            val bindingLifecycleState: Lifecycle.State = Lifecycle.State.STARTED
        ) : BindServiceConnection.Config(
            defaultBindFlags,
            autoRebindDeadBinding,
            autoRebindDeadBindingCompat
        ) {
            companion object{
                val DEFAULT = Builder().build()
            }
            init {
                require(bindingLifecycleState.isAtLeast(Lifecycle.State.CREATED))
                { "Provided lifecycle state must be STARTED, RESUMED or CREATED, got $bindingLifecycleState" }
            }

            /** Holds configuration data for service. */
            open class Builder : BindServiceConnection.Config.Builder() {
                /**
                 * Lifecycle state that will trigger the binding.
                 *
                 * This has to be either [Lifecycle.State.STARTED], [Lifecycle.State.RESUMED] or [Lifecycle.State.CREATED].
                 */
                var bindingLifecycleState: Lifecycle.State = Lifecycle.State.STARTED

                override fun build(): BindServiceConnection.Config = Config(
                    defaultBindFlags,
                    autoRebindDeadBinding,
                    autoRebindDeadBindingCompat,
                    bindingLifecycleState
                )
            }
        }
    }
}