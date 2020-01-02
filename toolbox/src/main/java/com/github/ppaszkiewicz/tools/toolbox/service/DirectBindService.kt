package com.github.ppaszkiewicz.tools.toolbox.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.system.Os.bind
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.github.ppaszkiewicz.tools.toolbox.extensions.ContextDelegate
import com.github.ppaszkiewicz.tools.toolbox.extensions.contextDelegate
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
    Base for direct binding services.
 */

/** Service binder giving direct reference. */
open class DirectBinder(val service: Service) : Binder()

/** This base has only one purpose: to work with [DirectServiceConnection]. */
abstract class DirectBindService : Service() {
    companion object {
        @JvmStatic
        val BIND_DIRECT_ACTION = "DirectBindService.BIND_DIRECT_ACTION"
    }

    @Suppress("LeakingThis")
    private val binder = DirectBinder(this)

    override fun onBind(intent: Intent?): IBinder {
        if (intent?.action == BIND_DIRECT_ACTION)
            return binder
        throw IllegalArgumentException("BIND_DIRECT_ACTION required.")
    }
}

/**
 * Binds to a DirectBindService and provides basic callbacks.
 *
 * Implements lifecycleObserve so it's aware of activity start/stop when binding.
 *
 * Provide either [onConnect] and [onDisconnect] listeners, or [observe] this as [LiveData] and react to changes.
 * */
open class DirectServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>
) : LiveData<T>(), ServiceConnection, LifecycleObserver {
    /** Companion object contains quick factory methods. */
    companion object {
        /** Observe connection to the service - this uses lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> observe(
            activity: AppCompatActivity,
            bindFlags: Int = Context.BIND_AUTO_CREATE
        ) = DirectServiceConnection(activity.contextDelegate, T::class.java).apply {
            defaultBindFlags = bindFlags
            activity.lifecycle.addObserver(this)
        }

        /** Observe connection to the service - this uses lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> observe(
            fragment: Fragment,
            bindFlags: Int = Context.BIND_AUTO_CREATE
        ) = DirectServiceConnection(fragment.contextDelegate, T::class.java).apply {
            defaultBindFlags = bindFlags
            fragment.lifecycle.addObserver(this)
        }

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> create(context: Context) =
            DirectServiceConnection(context.contextDelegate, T::class.java)

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> create(fragment: Fragment) =
            DirectServiceConnection(fragment.contextDelegate, T::class.java)
    }

    /** Context provided by delegate, workaround for fragment lazy context initialization. */
    val context by contextDelegate

    /**
     * Bind flags used as defaults during [bind].
     *
     * By default this is [Context.BIND_AUTO_CREATE].
     * */
    var defaultBindFlags = Context.BIND_AUTO_CREATE

    /** Raised if [bind] was called.*/
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

    /** True if service connected. */
    val isConnected
        get() = value != null

    /** Alias for [getValue]. Returns service object if this connection is connected. */
    val service: T?
        get() = value

    /** Bind to service. By default uses [defaultBindFlags]. */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun bind() = bind(defaultBindFlags)

    /** Bind to service. By default uses [defaultBindFlags]. */
    fun bind(flags: Int) {
        if (!isBound) {
            isBound = true
            context.bindService(
                Intent(context, serviceClass)
                    .setAction(DirectBindService.BIND_DIRECT_ACTION), this, flags
            )
        }
    }

    /**
     *  Disconnect/unbind from service.
     * */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    open fun unbind() {
        if (isBound) {
            isBound = false
            context.unbindService(this)
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
}

//// helper class needed to lazily obtain a context
// either uncomment this or copy extensions.ContextDelegate
///** Context delegate for classes that can return a [Context]. */
//sealed class ContextDelegate : ReadOnlyProperty<Any, Context> {
//    /** Returns self. */
//    class OfContext(private val context: Context) : ContextDelegate() {
//        override fun getValue(thisRef: Any, property: KProperty<*>) = context
//    }
//
//    /** Returns fragments context. Fragment might not have context attached when this wrapper is created. */
//    class OfFragment(private val fragment: Fragment) : ContextDelegate() {
//        override fun getValue(thisRef: Any, property: KProperty<*>) = fragment.requireContext()
//    }
//
//    /** Returns views context. */
//    class OfView(private val view: View) : ContextDelegate() {
//        override fun getValue(thisRef: Any, property: KProperty<*>) = view.context
//    }
//}
//
///** Delegate that returns context. */
//val Context.contextDelegate
//    get() = ContextDelegate.OfContext(this)
///** Delegate that returns context. */
//val Fragment.contextDelegate
//    get() = ContextDelegate.OfFragment(this)
///** Delegate that returns context. */
//val View.contextDelegate
//    get() = ContextDelegate.OfView(this)