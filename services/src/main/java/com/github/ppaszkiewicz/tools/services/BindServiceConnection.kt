package com.github.ppaszkiewicz.tools.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.system.Os.bind
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
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
    val adapter: Adapter<T>,
    configBuilder: Config.Builder?,
    private val callbacksProxy: BindServiceConnectionLambdas.Proxy<T>
) : LiveData<T?>(), BindServiceConnectionLambdas<T> by callbacksProxy {
    constructor(
        contextDelegate: ContextDelegate,
        adapter: Adapter<T>,
        configBuilder: Config.Builder? = null
    ) : this(contextDelegate, adapter, configBuilder, BindServiceConnectionLambdas.Proxy())

    companion object {
        /**
         * Since binding request and connection callbacks are posted on main looper [notConnectedRunnable]
         * has to be bounced twice to ensure it doesn't execute before they do.
         * */
        private const val NOT_CONNECTED_REPOST_COUNT = 2L

        /** Default value for [Config.notConnectedTimeout]. */
        val NOT_CONNECTED_DEFAULT = NotConnectedTimeout.reposts(NOT_CONNECTED_REPOST_COUNT)

        private val mainHandler = Handler(Looper.getMainLooper())
        private val isP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    /**
     * Possible behaviors to handle [ServiceConnection.onBindingDied].
     *
     * For API below 28 this callback does not exist. Here it can be configured to be called alongside
     * [ServiceConnection.onServiceDisconnected] instead.
     */
    enum class DeadBindingBehavior(val callback: Boolean, val rebind: Boolean) {
        /** Don't recreate lost bindings and don't call [onBindingDied]. */
        IGNORE(false, false),

        /** Call [onBindingDied] on API 28+ only but don't recreate bindings. */
        NATIVE_CALLBACK_ONLY(isP, false),

        /** Call [onBindingDied] and recreate binding on API 28+ only. */
        RECREATE_NATIVE_ONLY(isP, isP),

        /** Call [onBindingDied] but don't recreate bindings. */
        CALLBACK_ONLY(true, false),

        /** Call [onBindingDied] and recreate on all apis. */
        RECREATE(true, true);
    }

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

    /** After [release] binding can't be performed anymore. */
    var isReleased = false
        private set

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
    val stateLifecycleOwner: LifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = _stateLifecycle
        }

    internal val _stateLifecycle = LifecycleRegistry(stateLifecycleOwner)

    /**
     * Represents connection to a particular service object in following way:
     * - `null` before first connection is established
     * - `RESUMED` right before [onFirstConnect] & [onConnect] call
     * - `STOPPED` right before [onDisconnect] call
     * - `DESTROYED` after [dispatchDestroyConnectionLifecycle] which happens:
     *      - before [onConnectionLost] (unexpected disconnect)
     *      - before [onFirstConnect] (if service was restarted while unbound and we got new service object)
     *      - after [release] (connection shutting down)
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
    val connectionLifecycleOwner: LifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = connectionLifecycle
    }

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
        check(!isReleased) { "This BindServiceConnection was already released." }
        if (!isBound) {
            isBound = true
            val bindingExc = performBindImpl(true, flags)
            if (bindingExc != null) {
                isBound = false
                onBindingFailed(bindingExc)
            }
        }
    }

    /** Internal binding call without altering [isBound]. Returns `null` on success, otherwise exception. */
    protected fun performBindImpl(doCallback: Boolean, flags: Int): BindingException? {
        val bindingIntent = adapter.createBindingIntent(context)
        val bindingExc = try {
            if (context.bindService(bindingIntent, serviceConnectionObject, flags)) {
                _stateLifecycle.currentState = Lifecycle.State.CREATED
                if (doCallback) onBind?.invoke()
                startNotConnectedRunnable()
                return null
            }
            null
        } catch (exc: SecurityException) {
            exc
        }
        return BindingException(bindingIntent, bindingExc)
    }

    /** Perform unbinding after triggering event. */
    protected open fun performUnbind() {
        if (isBound) {
            clearNotConnectedRunnable()
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
            check(value == null) { "performRebind cannot be called when there's an active connection" }
            // unbind doesn't need to account for anything since service should've disconnected already
            context.unbindService(serviceConnectionObject)
            if (doCallbacks) onUnbind?.invoke()

            val bindingExc = performBindImpl(doCallbacks, flags) // exception handling for binding
            if (bindingExc != null) {
                isBound = false
                onBindingFailed(bindingExc)
            }
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
        // need to bump state to created so it can be cleanly destroyed and observers are released
        if(stateLifecycle.currentState == Lifecycle.State.INITIALIZED) {
            _stateLifecycle.currentState = Lifecycle.State.CREATED
        }
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
            if (!isP && config.deadBindingBehavior.callback) {
                internalOnBindingDied(name)
            }
        }

        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
            clearNotConnectedRunnable()
            val hasFreshConnectionLifecycle = if (_connectionLifecycle == null) {
                _connectionLifecycle = LifecycleRegistry(connectionLifecycleOwner); true
            } else false
            var isFirstConnect = false
            val oldBinder = currentBinder?.get()
            adapter.transformBinder(name, serviceBinder).also {
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
            clearNotConnectedRunnable()
            // this is always triggered after onServiceDisconnected ?
            if (config.deadBindingBehavior.callback) internalOnBindingDied(name)
        }

        private fun internalOnBindingDied(name: ComponentName?) {
            val callbackConsumed = onBindingDied?.invoke() ?: false
            if (config.deadBindingBehavior.rebind) {
                performRebind(!callbackConsumed, config.defaultBindFlags)
            } else if (isBound) {
                isBound = false
            }
        }

        // added in API level 26
        override fun onNullBinding(name: ComponentName?) {
            clearNotConnectedRunnable()
            //NOTE: this will be called when service is force stopped (context.stopService)
            // even if connection is bound but not connected
            onNullBinding?.invoke()
        }
    }

    private val notConnectedRunnable = object : Runnable {
        var repost = 0L

        override fun run() {
            if (repost-- > 0) {
                mainHandler.post(this)
                return
            }
            if (service == null) onNotConnected?.invoke()
            clearNotConnectedRunnable()
        }
    }

    private fun startNotConnectedRunnable() {
        clearNotConnectedRunnable()

        when {
            config.notConnectedTimeout.x == 0L -> return
            config.notConnectedTimeout.x < 0 -> {
                notConnectedRunnable.repost = -config.notConnectedTimeout.x
                mainHandler.post(notConnectedRunnable)
            }
            else -> {
                notConnectedRunnable.repost = 0
                mainHandler.postDelayed(notConnectedRunnable, config.notConnectedTimeout.x)
            }
        }
    }

    private fun clearNotConnectedRunnable() {
        mainHandler.removeCallbacks(notConnectedRunnable)
    }

    /** Holds configuration. */
    open class Config internal constructor(
        /** Bind flags used as when binding (by default [Context.BIND_AUTO_CREATE]). */
        val defaultBindFlags: Int,
        /** Defines how to handle [onBindingDied]. */
        val deadBindingBehavior: DeadBindingBehavior,
        /** Defines when [onNotConnected] is called. */
        val notConnectedTimeout: NotConnectedTimeout
    ) {
        companion object {
            val DEFAULT = Builder().build()
        }

        /** Holds arguments for configuration. */
        open class Builder {
            /** Bind flags used as when binding (by default [Context.BIND_AUTO_CREATE]). */
            var defaultBindFlags: Int = Context.BIND_AUTO_CREATE

            /** Defines how to handle [onBindingDied] (default: [DeadBindingBehavior.RECREATE]). */
            var deadBindingBehavior: DeadBindingBehavior = DeadBindingBehavior.RECREATE

            /** Defines when [onNotConnected] is called. */
            var notConnectedTimeout: NotConnectedTimeout = NOT_CONNECTED_DEFAULT

            internal open fun build() = Config(
                defaultBindFlags, deadBindingBehavior, notConnectedTimeout
            )
        }
    }


    /** Methods required to connect to service and connection. */
    interface Adapter<T> {
        /** Intent that is used to bind to the service. */
        fun createBindingIntent(context: Context): Intent

        /** Transform [binder] object into valid [LiveData] value of service connection. */
        fun transformBinder(name: ComponentName, binder: IBinder): T
    }

    /** Base for connection factories that connect to service of type [T]. */
    abstract class ConnectionFactory<T> : ConnectionFactoryBase<T,
            Manual<T>,
            Observable<T>,
            LifecycleAware<T>>() {
        /** Factory returning default implementations backed by [adapter].*/
        open class Default<T>(val adapter: Adapter<T>) : ConnectionFactory<T>() {
            override fun createManualConnection(
                contextDelegate: ContextDelegate,
                configBuilder: Config.Builder?
            ) = Manual(contextDelegate, adapter, configBuilder)

            override fun createObservableConnection(
                contextDelegate: ContextDelegate,
                configBuilder: Config.Builder?
            ) = Observable(contextDelegate, adapter, configBuilder)

            override fun createLifecycleConnection(
                contextDelegate: ContextDelegate,
                configBuilder: LifecycleAware.Config.Builder?
            ) = LifecycleAware(contextDelegate, adapter, configBuilder)
        }

        /** Factory intended for third party services that is an [Adapter] itself. */
        abstract class Remote<T> : ConnectionFactory<T>(), Adapter<T> {
            override fun createManualConnection(
                contextDelegate: ContextDelegate,
                configBuilder: Config.Builder?
            ) = Manual(contextDelegate, this, configBuilder)

            override fun createObservableConnection(
                contextDelegate: ContextDelegate,
                configBuilder: Config.Builder?
            ) = Observable(contextDelegate, this, configBuilder)

            override fun createLifecycleConnection(
                contextDelegate: ContextDelegate,
                configBuilder: LifecycleAware.Config.Builder?
            ) = LifecycleAware(contextDelegate, this, configBuilder)
        }
    }

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
     * @param intent Intent produced by [Adapter.createBindingIntent] that caused the failure
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

    /** Timeout modes for [onNotConnected]. */
    @JvmInline
    value class NotConnectedTimeout private constructor(val x: Long) {
        companion object {
            /** Wait given milliseconds. */
            fun milliseconds(ms: Long): NotConnectedTimeout {
                require(ms > 0)
                return NotConnectedTimeout(ms)
            }

            /** Wait given main looper loops. */
            fun reposts(count: Long): NotConnectedTimeout {
                require(count > 0)
                return NotConnectedTimeout(-count)
            }

            /** Never call [onNotConnected]. */
            fun none(): NotConnectedTimeout = NotConnectedTimeout(0)
        }
    }

    /* CONNECTIONS BELOW  **/
    /** Connection to service that requires manual [bind] and [unbind] calls. */
    open class Manual<T>(
        contextDelegate: ContextDelegate,
        adapter: Adapter<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, adapter, configBuilder) {
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
        adapter: Adapter<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, adapter, configBuilder) {
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
        adapter: Adapter<T>,
        configBuilder: Config.Builder? = null
    ) : BindServiceConnection<T>(contextDelegate, adapter, configBuilder),
        LifecycleEventObserver {
        /** Lifecycle state that will trigger the binding. */
        val bindingLifecycleState: Lifecycle.State
            get() = (config as Config).bindingLifecycleState

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
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
            deadBindingBehavior: DeadBindingBehavior,
            notConnectedTimeout: NotConnectedTimeout,
            /**
             * Lifecycle state that will trigger the binding.
             *
             * This has to be either [Lifecycle.State.STARTED], [Lifecycle.State.RESUMED] or [Lifecycle.State.CREATED].
             */
            val bindingLifecycleState: Lifecycle.State = Lifecycle.State.STARTED
        ) : BindServiceConnection.Config(
            defaultBindFlags,
            deadBindingBehavior,
            notConnectedTimeout
        ) {
            companion object {
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
                    deadBindingBehavior,
                    notConnectedTimeout,
                    bindingLifecycleState
                )
            }
        }
    }
}