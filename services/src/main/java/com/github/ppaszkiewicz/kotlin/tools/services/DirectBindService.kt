@file:Suppress("UNCHECKED_CAST")

package com.github.ppaszkiewicz.kotlin.tools.services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.os.Binder
import android.os.IBinder
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
 * If possible default implementations can be extended:
 * - [DirectBindService.Impl] - default implementation extending [Service]
 * - [DirectBindService.LifecycleImpl] - implementation extending [LifecycleService]
 *
 * Use [DirectBindService.ConnectionFactory] to create connections to those services.
 * */
interface DirectBindService {
    /** Default [DirectBindService] implementation.*/
    abstract class Impl : Service(), DirectBindService {
        @Suppress("LeakingThis")
        private val binder = DirectBinder(this)

        override fun onBind(intent: Intent): IBinder? = when (intent.action) {
            BIND_DIRECT_ACTION -> binder
            else -> null
        }
    }

    /**
     * Default [DirectBindService] implementation extending [LifecycleService].
     * */
    abstract class LifecycleImpl : LifecycleService(), DirectBindService {
        @Suppress("LeakingThis")
        private val binder = DirectBinder(this)

        override fun onBind(intent: Intent): IBinder? {
            super.onBind(intent)
            return when (intent.action) {
                BIND_DIRECT_ACTION -> binder
                else -> null
            }
        }
    }

    companion object {
        /** Action that will return a direct binder. */
        const val BIND_DIRECT_ACTION = "DirectBindService.BIND_DIRECT_ACTION"

        /** Valid intent to bind to [DirectBindService] with to receive [DirectBinder]. */
        fun <T : DirectBindService> createIntentFor(
            context: Context,
            serviceClass: Class<T>
        ): Intent = Intent(context, serviceClass).setAction(BIND_DIRECT_ACTION)

        /** Valid intent to bind to [DirectBindService] with to receive [DirectBinder]. */
        inline fun <reified T : DirectBindService> createIntentFor(context: Context) =
            createIntentFor(context, T::class.java)

        /** Create direct binding proxy implementation to use as delegate. */
        fun <T : DirectBindService> proxy(serviceClass: Class<T>) =
            object : BindServiceConnectionProxy<T> {
                override fun createBindingIntent(context: Context) =
                    createIntentFor(context, serviceClass)

                override fun transformBinder(name: ComponentName, binder: IBinder) =
                    (binder as DirectBinder).service as T
            }

        /** Create [ConnectionFactory] factory for [DirectBindService] of class [T]. */
        inline fun <reified T : DirectBindService> ConnectionFactory() =
            ConnectionFactory(T::class.java)
    }

    /**
     * Connection factory that creates default connections to [serviceClass].
     *
     * For convenience this can be inherited or created by that services companion object.
     */
    open class ConnectionFactory<T : DirectBindService>(protected val serviceClass: Class<T>) :
        BindServiceConnection.ConnectionFactory<T>() {
        override fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            bindFlags: Int,
            bindState: Lifecycle.State
        ) = DirectLifecycleServiceConnection(contextDelegate, serviceClass, bindState, bindFlags)

        override fun createObservableConnection(contextDelegate: ContextDelegate, bindFlags: Int) =
            DirectObservableServiceConnection(contextDelegate, serviceClass, bindFlags)

        override fun createManualConnection(contextDelegate: ContextDelegate, bindFlags: Int) =
            DirectManualServiceConnection(contextDelegate, serviceClass, bindFlags)
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
) : ManualBindServiceConnection<T>(contextDelegate, bindFlags),
    BindServiceConnectionProxy<T> by DirectBindService.proxy(serviceClass)

/**
 * Binds to a DirectBindService when it has active [LiveData] observers and provides basic callbacks.
 */
open class DirectObservableServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    /** Default bind flags to use. */
    bindFlags: Int = BIND_AUTO_CREATE
) : ObservableBindServiceConnection<T>(contextDelegate, bindFlags),
    BindServiceConnectionProxy<T> by DirectBindService.proxy(serviceClass)

/**
 * Binds to a DirectBindService based on lifecycle and provides basic callbacks.
 */
open class DirectLifecycleServiceConnection<T : DirectBindService>(
    contextDelegate: ContextDelegate,
    val serviceClass: Class<T>,
    bindingLifecycleState: Lifecycle.State,
    /** Default bind flags to use. */
    bindFlags: Int = BIND_AUTO_CREATE
) : LifecycleBindServiceConnection<T>(contextDelegate, bindingLifecycleState, bindFlags),
    BindServiceConnectionProxy<T> by DirectBindService.proxy(serviceClass)