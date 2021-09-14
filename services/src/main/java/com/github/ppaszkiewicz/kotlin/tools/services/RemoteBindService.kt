package com.github.ppaszkiewicz.kotlin.tools.services

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
        override fun createManualConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.Config.Builder?
        ) = BindServiceConnection.Manual(contextDelegate, connectionProxy, configBuilder)

        override fun createObservableConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.Config.Builder?
        ) = BindServiceConnection.Observable(contextDelegate, connectionProxy, configBuilder)

        override fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.LifecycleAware.ConfigBuilder?
        ) = BindServiceConnection.LifecycleAware(contextDelegate, connectionProxy, configBuilder)
    }
}