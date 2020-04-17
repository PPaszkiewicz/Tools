package com.github.ppaszkiewicz.tools.toolbox.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
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

/* requires BindServiceConnection.kt and context delegates from delegate.Context.kt */

/*
    Base for direct binding services.
 */

/** Service binder giving direct reference. */
open class DirectBinder(val service: Service) : Binder()

/**
 * This is a marker interface for services handling [BIND_DIRECT_ACTION] and returning [DirectBinder].
 *
 * If possible default implementation can be extended: [DirectBindService.Impl].
 *
 * Use [DirectServiceConnection] to connect to those services.
 * */
interface DirectBindService {
    /** Default [DirectBindService] implementation.*/
    abstract class Impl : Service(), DirectBindService {
        @Suppress("LeakingThis")
        private val binder = DirectBinder(this)

        override fun onBind(intent: Intent): IBinder {
            if (intent.action == BIND_DIRECT_ACTION)
                return binder
            throw IllegalArgumentException("BIND_DIRECT_ACTION required.")
        }
    }

    /** Default [DirectBindService] implementation extending [LifecycleService].*/
    abstract class LifecycleImpl : LifecycleService(), DirectBindService {
        @Suppress("LeakingThis")
        private val binder = DirectBinder(this)

        override fun onBind(intent: Intent): IBinder {
            super.onBind(intent)
            if (intent.action == BIND_DIRECT_ACTION)
                return binder
            throw IllegalArgumentException("BIND_DIRECT_ACTION required.")
        }
    }

    companion object {
        /** Action that will return a direct binder. */
        const val BIND_DIRECT_ACTION = "DirectBindService.BIND_DIRECT_ACTION"

        /** Valid intent to bind to [DirectBindService] with to receive [DirectBinder]. */
        fun <T : DirectBindService> bindIntentFor(
            context: Context,
            serviceClass: Class<T>
        ): Intent = Intent(context, serviceClass).setAction(BIND_DIRECT_ACTION)

        /** Valid intent to bind to [DirectBindService] with to receive [DirectBinder]. */
        inline fun <reified T : DirectBindService> bindIntentFor(context: Context) =
            bindIntentFor(context, T::class.java)


        // FACTORY methods

        /** Observe connection to the service - this uses activity lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> lifecycleConnection(
            activity: AppCompatActivity,
            bindFlags: Int = BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = lifecycleConnection(
            T::class.java,
            activity.contextDelegate,
            activity.lifecycle,
            bindFlags,
            bindState
        )

        /** Observe connection to the service - this uses fragment lifecycle to connect to service automatically. */
        inline fun <reified T : DirectBindService> lifecycleConnection(
            fragment: Fragment,
            bindFlags: Int = BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = lifecycleConnection(
            T::class.java,
            fragment.contextDelegate,
            fragment.lifecycle,
            bindFlags,
            bindState
        )

        /** Observe connection to the service - this uses given lifecycle to connect to service automatically. */
        fun <T : DirectBindService> lifecycleConnection(
            serviceClass: Class<T>,
            contextDelegate: ContextDelegate,
            lifecycle: Lifecycle,
            bindFlags: Int = BIND_AUTO_CREATE,
            bindState: Lifecycle.State = Lifecycle.State.STARTED
        ) = DirectLifecycleServiceConnection(contextDelegate, serviceClass, bindState, bindFlags).apply {
            lifecycle.addObserver(this)
        }

        /** Create connection to service, it will be bound when there are active observers. */
        inline fun <reified T : DirectBindService> observableConnection(
            context: Context,
            bindFlags: Int = BIND_AUTO_CREATE
        ) = DirectObservableServiceConnection(context.contextDelegate, T::class.java, bindFlags)

        /** Create connection to service, it will be bound when there are active observers. */
        inline fun <reified T : DirectBindService> observableConnection(
            fragment: Fragment,
            bindFlags: Int = BIND_AUTO_CREATE
        ) = DirectObservableServiceConnection(fragment.contextDelegate, T::class.java, bindFlags)

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> manualConnection(
            context: Context,
            bindFlags: Int = BIND_AUTO_CREATE
        ) = DirectManualServiceConnection(context.contextDelegate, T::class.java, bindFlags)

        /** Create connection to service. Need to manually call [bind] and [unbind] to connect.*/
        inline fun <reified T : DirectBindService> manualConnection(
            fragment: Fragment,
            bindFlags: Int = BIND_AUTO_CREATE
        ) = DirectManualServiceConnection(fragment.contextDelegate, T::class.java, bindFlags)
    }
}

/**
 * Binds to a DirectBindService when [bind] and [unbind] are called and provides basic callbacks.
 */
open class DirectManualServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    /** Default bind flags to use. */
    bindFlags: Int = BIND_AUTO_CREATE
) : ManualBindServiceConnection<T>(contextDelegate, bindFlags) {
    override fun createBindingIntent(context: Context) =
        DirectBindService.bindIntentFor(context, serviceClass)

    override fun transformBinder(binder: IBinder) = (binder as DirectBinder).service as T
}

/**
 * Binds to a DirectBindService when it has active [LiveData] observers and provides basic callbacks.
 */
open class DirectObservableServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    /** Default bind flags to use. */
    bindFlags: Int = BIND_AUTO_CREATE
) : ObservableBindServiceConnection<T>(contextDelegate, bindFlags) {
    override fun createBindingIntent(context: Context) =
        DirectBindService.bindIntentFor(context, serviceClass)

    override fun transformBinder(binder: IBinder) = (binder as DirectBinder).service as T
}

/**
 * Binds to a DirectBindService based on lifecycle and provides basic callbacks.
 */
open class DirectLifecycleServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    bindingLifecycleState: Lifecycle.State,
    /** Default bind flags to use. */
    bindFlags: Int = BIND_AUTO_CREATE
) : LifecycleBindServiceConnection<T>(contextDelegate, bindingLifecycleState, bindFlags) {
    override fun createBindingIntent(context: Context) =
        DirectBindService.bindIntentFor(context, serviceClass)

    override fun transformBinder(binder: IBinder) = (binder as DirectBinder).service as T
}