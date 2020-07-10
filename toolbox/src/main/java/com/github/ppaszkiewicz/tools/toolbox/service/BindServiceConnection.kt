package com.github.ppaszkiewicz.tools.toolbox.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.system.Os.bind
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.delegate.contextDelegate
import java.lang.ref.WeakReference

/* requires context delegates from delegate.Context.kt */

/**
 * Base for bind service connection implementations. Implements [LifecycleOwner] that's resumed
 * only while service is connected.
 *
 * Implementing classes need to call [performBind] and [performUnbind] as they see fit.
 * */
abstract class BindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : LiveData<T?>(), ServiceConnection, LifecycleOwner {
    /** Intent that is used to bind to the service. */
    abstract fun createBindingIntent(context: Context): Intent

    /** Transform [binder] object into valid [LiveData] value of this object. */
    abstract fun transformBinder(name: ComponentName, binder: IBinder): T

    /** Used to determine if [onFirstConnect] should trigger. */
    private var previousValue: WeakReference<T>? = null

    @Suppress("LeakingThis")
    private val _lifecycle = LifecycleRegistry(this)

    /** Context provided by delegate, workaround for fragment lazy context initialization. */
    val context by contextDelegate

    /**
     * Bind flags used as when binding.
     *
     * By default this is [Context.BIND_AUTO_CREATE].
     * */
    var defaultBindFlags = bindFlags

    /**
     * Automatically recreate binding using [defaultBindFlags] if [onBindingDied] occurs (default: `true`).
     */
    var autoRebindDeadBinding = true

    /** Raised if [bind] was called without matching [unbind]. */
    var isBound = false
        internal set

    /** True if service is connected. */
    val isConnected
        get() = value != null

    /** Alias for [getValue]. Returns service (or binder) object if this connection is connected. */
    val service: T?
        get() = value

    // optionally injectable listeners

    /**
     * Triggered when service is connected.
     * */
    var onConnect: ((T) -> Unit)? = null

    /**
     * Called after [onConnect] when this is first time `bind` call resulted in successful
     * connection to a service or service object changed.
     */
    var onFirstConnect: ((T) -> Unit)? = null

    /**
     * Triggered when service is disconnected.
     *
     * To handle lost connection cases provide [onConnectionLost] callback.
     **/
    var onDisconnect: ((T) -> Unit)? = null

    /**
     * Triggered when service connection is lost due to [ServiceConnection.onServiceDisconnected].
     *
     * In most cases it should be impossible to trigger for services running in same process bound with
     * [Context.BIND_AUTO_CREATE].
     *
     * If this is `null` or returns `true` then [onDisconnect] will be called afterwards.
     **/
    var onConnectionLost: ((T) -> Boolean)? = null

    /**
     * Triggered when service binding is requested.
     * */
    var onBind: (() -> Unit)? = null

    /**
     * Triggered when service unbinding is requested.
     * */
    var onUnbind: (() -> Unit)? = null

    /**
     * Triggered when binding dies.
     *
     * If this is `null` or returns `true` then [onUnbind] and [onBind] are called while rebinding.
     */
    var onBindingDied: (() -> Boolean)? = null

    /**
     * Called when [ServiceConnection.onNullBinding] occurs.
     */
    var onNullBinding: (() -> Unit)? = null

    // satisfy LifecycleOwner
    override fun getLifecycle() = _lifecycle

    // core implementation

    /** Perform binding during specific triggering event. */
    protected open fun performBind(flags: Int) {
        if (!isBound) {
            isBound = true
            context.bindService(createBindingIntent(context), this, flags)
            onBind?.invoke()
        }
    }

    /** Perform unbinding after triggering event. */
    protected open fun performUnbind() {
        if (isBound) {
            isBound = false
            context.unbindService(this)
            value?.let { onDisconnect?.invoke(it) }
            onUnbind?.invoke()
            value = null
        }
    }

    /** Perform rebind after unexpected binding death. */
    protected open fun performRebind(doCallbacks: Boolean, flags: Int) {
        if (isBound) {
            context.unbindService(this)
            if (doCallbacks) onUnbind?.invoke()
            context.bindService(createBindingIntent(context), this, flags)
            if (doCallbacks) onBind?.invoke()
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        value?.let { service ->
            val callDisconnect = onConnectionLost?.let { it(service) }
            if(callDisconnect == true) onDisconnect?.invoke(service)
            _lifecycle.currentState = Lifecycle.State.CREATED
            value = null
        } ?: Log.e("BindServiceConn", "unexpected onServiceDisconnected: service object missing. " +
                "Connection: ${this::javaClass.name}, Service name: $name")
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        transformBinder(name, service).also {
            this.value = it
            onConnect?.invoke(it)
            if (previousValue?.get() != it) {
                onFirstConnect?.invoke(it)
                previousValue = WeakReference(it)
            }
            _lifecycle.currentState = Lifecycle.State.RESUMED
        }
    }

    override fun onBindingDied(name: ComponentName?) {
        val doCallbacks = onBindingDied?.invoke() ?: true
        if (autoRebindDeadBinding) {
            performRebind(doCallbacks, defaultBindFlags)
        } else if (isBound) {
            isBound = false
        }
    }

    override fun onNullBinding(name: ComponentName?) {
        onNullBinding?.invoke()
    }

    /** Base for connection factories that connect to service of type [T]. */
    abstract class ConnectionFactory<T> {
        /** Create [LifecycleBindServiceConnection] - this uses activity lifecycle to connect to service automatically. */
        fun lifecycle(
            activity: AppCompatActivity,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = lifecycle(activity.contextDelegate, activity.lifecycle, bindFlags, bindState)

        /** Create [LifecycleBindServiceConnection] - this uses fragment lifecycle to connect to service automatically. */
        fun lifecycle(
            fragment: Fragment,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = lifecycle(fragment.contextDelegate, fragment.lifecycle, bindFlags, bindState)

        /** Create [ObservableBindServiceConnection], it will be bound when there are active observers. */
        fun observable(context: Context, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            observable(context.contextDelegate, bindFlags)

        /** Create [ObservableBindServiceConnection], it will be bound when there are active observers. */
        fun observable(fragment: Fragment, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            observable(fragment.contextDelegate, bindFlags)

        /** Create [ManualBindServiceConnection]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(context: Context, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            manual(context.contextDelegate, bindFlags)

        /** Create [ManualBindServiceConnection]. Need to manually call [bind] and [unbind] to connect.*/
        fun manual(fragment: Fragment, bindFlags: Int = Context.BIND_AUTO_CREATE) =
            manual(fragment.contextDelegate, bindFlags)


        // implementations
        /** Create [LifecycleBindServiceConnection] - this uses given lifecycle to connect to service automatically. */
        abstract fun lifecycle(
            contextDelegate: ContextDelegate,
            lifecycle: Lifecycle,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ): LifecycleBindServiceConnection<T>

        /** Create [ObservableBindServiceConnection], it will be bound when there are active observers. */
        abstract fun observable(
            contextDelegate: ContextDelegate, bindFlags: Int = Context.BIND_AUTO_CREATE
        ): ObservableBindServiceConnection<T>

        /** Create [ManualBindServiceConnection]. Need to manually call [bind] and [unbind] to connect.*/
        abstract fun manual(
            contextDelegate: ContextDelegate, bindFlags: Int = Context.BIND_AUTO_CREATE
        ): ManualBindServiceConnection<T>
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
    fun bind(connectionFlags: Int = defaultBindFlags) {
        if(!isBound) currentBindFlags = connectionFlags
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
        performBind(defaultBindFlags)
    }

    override fun onInactive() {
        performUnbind()
    }
}

/** Connection to service that is a [LifecycleObserver] and aligns binding with [bindingLifecycleState]. */
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
                Lifecycle.Event.ON_START -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_STOP -> performUnbind()
                else -> Unit // ignored
            }
            Lifecycle.State.RESUMED -> when (event) {
                Lifecycle.Event.ON_RESUME -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_PAUSE -> performUnbind()
                else -> Unit // ignored
            }
            Lifecycle.State.CREATED -> when (event) {
                Lifecycle.Event.ON_CREATE -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_DESTROY -> performUnbind()
                else -> Unit // ignored
            }
            else -> throw IllegalStateException("invalid bindingLifecycleEvent")
        }
    }
}