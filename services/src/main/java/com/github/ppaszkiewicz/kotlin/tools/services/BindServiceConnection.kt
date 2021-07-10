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
    bindFlags: Int = Context.BIND_AUTO_CREATE,
    private val callbacksProxy: BindServiceConnectionLambdas.Proxy<T>
) : LiveData<T?>(), BindServiceConnectionProxy<T>,
    BindServiceConnectionLambdas<T> by callbacksProxy {

    constructor(
        contextDelegate: ContextDelegate,
        /** Default bind flags to use. */
        bindFlags: Int = Context.BIND_AUTO_CREATE
    ) : this(contextDelegate, bindFlags, BindServiceConnectionLambdas.Proxy())

    /**
     * Used to determine if [onFirstConnect] should trigger - this is based on the fact that
     * as long as service is alive we will keep receiving exact same binder object regardless
     * of how many times connection rebinds.
     * */
    private var currentBinder: WeakReference<IBinder>? = null


    /** Context provided by delegate, workaround for fragment lazy context initialization. */
    val context by contextDelegate

    /** Holds configuration. */
    val config = Config(bindFlags)

    /** Holds configuration. */
    class Config internal constructor(
        /**
         * Bind flags used as when binding.
         *
         * By default this is [Context.BIND_AUTO_CREATE].
         * */
        var defaultBindFlags: Int,
        /**
         * Automatically recreate binding using [defaultBindFlags] if [onBindingDied] occurs (default: `true`).
         *
         * This works natively starting from API 28, for lower versions compatibility behavior is
         * enabled by [autoRebindDeadBindingCompat].
         */
        var autoRebindDeadBinding: Boolean = true,
        /**
         * Force rebind when connection dies on devices below API 28.
         */
        var autoRebindDeadBindingCompat: Boolean = true
    )

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
            val bindingIntent = createBindingIntent(context)
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
            context.bindService(createBindingIntent(context), serviceConnectionObject, flags)
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
            transformBinder(name, serviceBinder).also {
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

    /** Base for connection factories that connect to service of type [T]. */
    abstract class ConnectionFactory<T> {
        /** Create [LifecycleBindServiceConnection] - this uses activity lifecycle to connect to service automatically. */
        fun lifecycle(
            activity: AppCompatActivity,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = attach(
            activity,
            createLifecycleConnection(activity.contextDelegate, bindFlags, bindState)
        )

        /** Create [LifecycleBindServiceConnection] - this uses fragment lifecycle to connect to service automatically. */
        fun lifecycle(
            fragment: Fragment,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = attach(
            fragment,
            createLifecycleConnection(fragment.contextDelegate, bindFlags, bindState)
        )

        /**
         * Create [LifecycleBindServiceConnection] that observes view lifecycle of [fragment] when it's ready.
         * */
        fun viewLifecycle(
            fragment: Fragment,
            bindFlags: Int = Context.BIND_AUTO_CREATE
        ) = attach(
            fragment, fragment.viewLifecycleOwnerLiveData,
            createLifecycleConnection(fragment.contextDelegate, bindFlags, Lifecycle.State.RESUMED)
        )

        /** Create [ObservableBindServiceConnection], it will be bound when there are active observers. */
        fun observable(context: Context, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            createObservableConnection(context.contextDelegate, bindFlags)

        /** Create [ObservableBindServiceConnection], it will be bound when there are active observers. */
        fun observable(fragment: Fragment, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            createObservableConnection(fragment.contextDelegate, bindFlags)

        /** Create [ManualBindServiceConnection]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(context: Context, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            createManualConnection(context.contextDelegate, bindFlags)

        /** Create [ManualBindServiceConnection]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(fragment: Fragment, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            createManualConnection(fragment.contextDelegate, bindFlags)

        // implementations
        /** Create [LifecycleBindServiceConnection] to return from [lifecycle] and [viewLifecycle]. */
        abstract fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ): LifecycleBindServiceConnection<T>

        /** Create [ObservableBindServiceConnection] to return from [observable]. */
        abstract fun createObservableConnection(
            contextDelegate: ContextDelegate, bindFlags: Int = Context.BIND_AUTO_CREATE
        ): ObservableBindServiceConnection<T>

        /** Create [ManualBindServiceConnection] to return from [manual]. */
        abstract fun createManualConnection(
            contextDelegate: ContextDelegate, bindFlags: Int = Context.BIND_AUTO_CREATE
        ): ManualBindServiceConnection<T>

        // internal to attach observers
        /** Make [conn] observe [lOwner]. */
        protected fun attach(lOwner: LifecycleOwner, conn: LifecycleBindServiceConnection<T>) =
            conn.apply { lOwner.lifecycle.addObserver(this) }

        /** Make [conn] observe any lifecycle emitted by [ldOwner] as long as [lOwner] is alive. */
        protected fun attach(
            lOwner: LifecycleOwner,
            ldOwner: LiveData<LifecycleOwner?>,
            conn: LifecycleBindServiceConnection<T>
        ) = conn.apply { ldOwner.observe(lOwner, Observer { it?.lifecycle?.addObserver(this) }) }
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
}

/** Connection to service that requires manual [bind] and [unbind] calls. */
abstract class ManualBindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : BindServiceConnection<T>(contextDelegate, bindFlags) {
    /**
     * Flag that was used for ongoing binding - this might be different from [defaultBindFlags] if
     * user provided custom argument for [bind].
     *
     * If not bound this is -1.
     * */
    var currentBindFlags = -1
        protected set

    /**
     * Bind to service using [connectionFlags] (by default [defaultBindFlags]).
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
abstract class ObservableBindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : BindServiceConnection<T>(contextDelegate, bindFlags) {
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
abstract class LifecycleBindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /**
     * Lifecycle state that will trigger the binding.
     *
     * This has to be either [Lifecycle.State.STARTED], [Lifecycle.State.RESUMED] or [Lifecycle.State.CREATED].
     */
    val bindingLifecycleState: Lifecycle.State,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : BindServiceConnection<T>(contextDelegate, bindFlags), LifecycleObserver {
    init {
        require(
            bindingLifecycleState == Lifecycle.State.STARTED ||
                    bindingLifecycleState == Lifecycle.State.RESUMED ||
                    bindingLifecycleState == Lifecycle.State.CREATED
        )
    }

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
}