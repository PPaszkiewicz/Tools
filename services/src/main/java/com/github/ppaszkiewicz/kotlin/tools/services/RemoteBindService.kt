package com.github.ppaszkiewicz.kotlin.tools.services

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate

/**
 * Hosts factory for remote binding services.
 */
object RemoteBindService {
    /**
     * Connection factory that creates connections to services that are not [DirectBindService] and need
     * custom binding intent and binder transformation.
     *
     * For convenience this can be inherited or created as a static helper object.
     */
    open class ConnectionFactory<T>(protected val connectionProxy: BindServiceConnectionProxy<T>) :
        BindServiceConnection.ConnectionFactory<T>() {
        override fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            bindFlags: Int,
            bindState: Lifecycle.State
        ) = RemoteLifecycleServiceConnection(contextDelegate, connectionProxy, bindState, bindFlags)

        override fun createObservableConnection(contextDelegate: ContextDelegate, bindFlags: Int) =
            RemoteObservableServiceConnection(contextDelegate, connectionProxy, bindFlags)

        override fun createManualConnection(contextDelegate: ContextDelegate, bindFlags: Int) =
            RemoteManualServiceConnection(contextDelegate, connectionProxy, bindFlags)
    }
}

/**
 * Binds to service when [bind] and [unbind] are called and provides basic callbacks.
 */
open class RemoteManualServiceConnection<T>(
    contextDelegate: ContextDelegate,
    proxy: BindServiceConnectionProxy<T>,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : ManualBindServiceConnection<T>(contextDelegate, bindFlags),
    BindServiceConnectionProxy<T> by proxy

/**
 * Binds to service when it has active [LiveData] observers and provides basic callbacks.
 */
open class RemoteObservableServiceConnection<T>(
    contextDelegate: ContextDelegate,
    proxy: BindServiceConnectionProxy<T>,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : ObservableBindServiceConnection<T>(contextDelegate, bindFlags),
    BindServiceConnectionProxy<T> by proxy

/**
 * Binds to service based on lifecycle and provides basic callbacks.
 */
open class RemoteLifecycleServiceConnection<T>(
    contextDelegate: ContextDelegate,
    proxy: BindServiceConnectionProxy<T>,
    bindingLifecycleState: Lifecycle.State,
    /** Default bind flags to use. */
    bindFlags: Int = Context.BIND_AUTO_CREATE
) : LifecycleBindServiceConnection<T>(contextDelegate, bindingLifecycleState, bindFlags),
    BindServiceConnectionProxy<T> by proxy