package com.github.ppaszkiewicz.tools.toolbox.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.system.Os.bind
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate

/* requires context delegates from delegate.Context.kt */

/**
 * Base for bind service connection implementations.
 *
 * Implementing classes need to call [performBind] and [performUnbind] as they see fit.
 * */
abstract class BindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : LiveData<T?>(), ServiceConnection {
    /** Intent that is used to bind to the service. */
    abstract fun createBindingIntent(context: Context): Intent

    /** Transform [binder] object into valid [LiveData] value of this object. */
    abstract fun transformBinder(binder: IBinder): T

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
     * Triggered when service disconnects. This usually doesn't trigger unless service
     * is interrupted while bound.
     **/
    var onDisconnect: ((T) -> Unit)? = null

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
     * If this is null or returns true then [onUnbind] and [onBind] are called while rebinding. Returning
     * false prevents those callbacks from being invoked.
     */
    var onBindingDied: (() -> Boolean)? = null

    /**
     * Called when null binding occurs.
     */
    var onNullBinding: (() -> Unit)? = null

    // core implementation

    /** Perform binding after relevant event. */
    protected open fun performBind(flags: Int) {
        if (!isBound) {
            isBound = true
            context.bindService(createBindingIntent(context), this, flags)
            onBind?.invoke()
        }
    }

    /** Perform unbinding after relevant event. */
    protected open fun performUnbind() {
        if (isBound) {
            isBound = false
            context.unbindService(this)
            onUnbind?.invoke()
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
        onDisconnect?.let { it(value!!) }
        value = null
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        @Suppress("UNCHECKED_CAST")
        this.value = transformBinder(service)
        onConnect?.invoke(this.value!!)
    }

    override fun onBindingDied(name: ComponentName?) {
        val doCallbacks = onBindingDied?.invoke() ?: true
        if (autoRebindDeadBinding) {
            performRebind(doCallbacks, defaultBindFlags)
        }
    }

    override fun onNullBinding(name: ComponentName?) {
        onNullBinding?.invoke()
    }
}

/** Binding service that requires manual [bind] and [unbind] calls. */
abstract class ManualBindServiceConnection<T>(
    contextDelegate: ContextDelegate,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : BindServiceConnection<T>(contextDelegate, bindFlags) {
    /**
     * Bind to service using [connectionFlags] (by default [defaultBindFlags]).
     * */
    fun bind(connectionFlags: Int = defaultBindFlags) {
        performBind(connectionFlags)
    }

    /**
     * Request unbind (disconnect) from service.
     */
    fun unbind() {
        performUnbind()
    }
}

/**
 * Binding service that relies on this object also being a [LiveData]: binds as long as there's any
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

/** Binding service that is a [LifecycleObserver] and aligns binding with [bindingLifecycleState]. */
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
        require(bindingLifecycleState == Lifecycle.State.STARTED ||
                bindingLifecycleState == Lifecycle.State.RESUMED ||
                bindingLifecycleState == Lifecycle.State.CREATED)
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