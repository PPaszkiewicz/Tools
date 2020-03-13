package com.github.ppaszkiewicz.tools.toolbox.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.system.Os.bind
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.delegate.contextDelegate
import com.github.ppaszkiewicz.tools.toolbox.service.DirectServiceConnection.BindingMode.*

/* requires context delegates from delegate.Context.kt */

/*
    Base for direct binding services.
 */

/** Service binder giving direct reference. */
open class DirectBinder(val service: Service) : Binder()

/**
 * This is a marker interface for services handling [BIND_DIRECT_ACTION] and returning [DirectBinder].
 *
 * If possible default implementation can be extended: [DirectBindService.Impl].
 * */
interface DirectBindService {
    companion object {
        /** Action that will return a direct binder. */
        const val BIND_DIRECT_ACTION = "DirectBindService.BIND_DIRECT_ACTION"
    }

    /** Default [DirectBindService] implementation.*/
    abstract class Impl : Service(), DirectBindService {
        @Suppress("LeakingThis")
        private val binder = DirectBinder(this)

        override fun onBind(intent: Intent?): IBinder {
            if (intent?.action == BIND_DIRECT_ACTION)
                return binder
            throw IllegalArgumentException("BIND_DIRECT_ACTION required.")
        }
    }
}

/**
 * Binds to a DirectBindService and provides basic callbacks.
 *
 * Use companion builders for automatic binding handling.
 *
 * Provide either [onConnect] and [onDisconnect] listeners, or [observe] this as [LiveData] and react to changes.
 * */
open class DirectServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    /** Binding mode of this connection. Note that only in [LIFECYCLE] mode it can be used as lifecycle observer,
     * otherwise connection will force a crash. */
    val bindingMode: BindingMode
) : LiveData<T?>(), ServiceConnection, LifecycleObserver {
    /** Companion object contains quick factory methods. */
    companion object {
        /** Observe connection to the service - this uses activity lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> observe(
            activity: AppCompatActivity,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindingEvents : BindingEvents = BindingEvents.START_STOP
        ) = observe<T>(activity.contextDelegate, activity.lifecycle, bindFlags, bindingEvents)

        /** Observe connection to the service - this uses fragment lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> observe(
            fragment: Fragment,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindingEvents : BindingEvents = BindingEvents.START_STOP
        ) = observe<T>(fragment.contextDelegate, fragment.lifecycle, bindFlags, bindingEvents)

        /** Observe connection to the service - this uses given lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> observe(
            contextDelegate: ContextDelegate,
            lifecycle: Lifecycle,
            bindFlags: Int = Context.BIND_AUTO_CREATE,
            bindingEvents : BindingEvents = BindingEvents.START_STOP
        ) = DirectServiceConnection(contextDelegate, T::class.java, LIFECYCLE).apply {
            defaultBindFlags = bindFlags
            bindingLifecycleEvents = bindingEvents
            lifecycle.addObserver(this)
        }

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> create(context: Context) =
            DirectServiceConnection(context.contextDelegate, T::class.java, MANUAL)

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> create(fragment: Fragment) =
            DirectServiceConnection(fragment.contextDelegate, T::class.java, MANUAL)

        /** Create connection to service, it will be bound when there are active observers. */
        inline fun <reified T : DirectBindService> liveData(context: Context) =
            DirectServiceConnection(context.contextDelegate, T::class.java, LIVEDATA)

        /** Create connection to service, it will be bound when there are active observers. */
        inline fun <reified T : DirectBindService> liveData(fragment: Fragment) =
            DirectServiceConnection(fragment.contextDelegate, T::class.java, LIVEDATA)
    }

    /** Binding modes available for [DirectServiceConnection]. */
    enum class BindingMode {
        /** User has to manually call [bind] and [unbind]. */
        MANUAL,

        /** When this is observing a lifecycle and binding in line with it. */
        LIFECYCLE,

        /** When this is being observed as LiveData and should auto bind/unbind when there are
         * active observers. */
        LIVEDATA
    }

    /** Lifecycle events to observe when [LIFECYCLE] binding mode is used. */
    enum class BindingEvents {
        START_STOP,
        CREATE_DESTROY,
        RESUME_PAUSE
    }

    /** Context provided by delegate, workaround for fragment lazy context initialization. */
    val context by contextDelegate

    /**
     * Bind flags used as defaults during [bind].
     *
     * By default this is [Context.BIND_AUTO_CREATE].
     * */
    var defaultBindFlags = Context.BIND_AUTO_CREATE

    /**
     * Lifecycle event that will trigger the binding.
     *
     * By default this is [BindingEvents.START_STOP] or null if binding mode is not lifecycle.
     * */
    var bindingLifecycleEvents: BindingEvents? =
        if (bindingMode == LIFECYCLE) BindingEvents.START_STOP else null

    /** Raised if [bind] was called without matching [unbind]. */
    var isBound = false
        internal set

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

    /** True if service connected. */
    val isConnected
        get() = value != null

    /** Alias for [getValue]. Returns service object if this connection is connected. */
    val service: T?
        get() = value

    /** Perform binding after specific trigger based on [bindingMode]. */
    protected open fun performBind(flags: Int) {
        if (!isBound) {
            isBound = true
            context.bindService(
                Intent(context, serviceClass)
                    .setAction(DirectBindService.BIND_DIRECT_ACTION), this, flags
            )
            onBind?.invoke()
        }
    }

    /** Perform unbinding after specific trigger based on [bindingMode]. */
    protected open fun performUnbind() {
        if (isBound) {
            isBound = false
            context.unbindService(this)
            onUnbind?.invoke()
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        onDisconnect?.let { it(value!!) }
        value = null
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        @Suppress("UNCHECKED_CAST")
        this.value = ((service as DirectBinder).service as T)
        onConnect?.invoke(this.value!!)
    }

    // manual triggers
    /**
     * Bind to service using [connectionFlags] (by default [defaultBindFlags]).
     *
     * Requires [bindingMode] to be [MANUAL].
     * */
    fun bind(connectionFlags: Int = defaultBindFlags) {
        check(bindingMode == MANUAL)
        performBind(connectionFlags)
    }

    /**
     * Request unbind (disconnect) from service.
     *
     * Requires [bindingMode] to be [MANUAL].
     */
    fun unbind() {
        check(bindingMode == MANUAL)
        performUnbind()
    }

    // lifecycle triggers
    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onLifecycleEvent(source : LifecycleOwner, event: Lifecycle.Event) {
        check(bindingMode == LIFECYCLE)
        when(bindingLifecycleEvents){
            BindingEvents.START_STOP -> when(event){
                Lifecycle.Event.ON_START -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_STOP -> performUnbind()
                else -> Unit // ignored
            }
            BindingEvents.CREATE_DESTROY -> when(event){
                Lifecycle.Event.ON_CREATE -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_DESTROY -> performUnbind()
                else -> Unit // ignored
            }
            BindingEvents.RESUME_PAUSE -> when(event){
                Lifecycle.Event.ON_RESUME -> performBind(defaultBindFlags)
                Lifecycle.Event.ON_PAUSE -> performUnbind()
                else -> Unit // ignored
            }
            null -> throw IllegalStateException("missing bindingLifecycleEvent")
        }
    }

    // liveData triggers
    override fun onActive() {
        if (bindingMode == LIVEDATA) performBind(defaultBindFlags)
    }

    override fun onInactive() {
        if (bindingMode == LIVEDATA) performUnbind()
    }
}