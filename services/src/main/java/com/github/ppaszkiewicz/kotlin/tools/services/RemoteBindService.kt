package com.github.ppaszkiewicz.kotlin.tools.services

import com.github.ppaszkiewicz.tools.toolbox.delegate.ContextDelegate

/**
 * Hosts factory for remote binding services.
 */
object RemoteBindService {
    /**
     * Connection factory that creates connections to services that are not [DirectBindService] and need
     * custom [adapter].
     *
     * For convenience this can be inherited or created as a static helper object.
     */
    open class ConnectionFactory<T>(protected val adapter: BindServiceConnection.Adapter<T>) :
        BindServiceConnection.ConnectionFactory<T>() {
        override fun createManualConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.Config.Builder?
        ) = BindServiceConnection.Manual(contextDelegate, adapter, configBuilder)

        override fun createObservableConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.Config.Builder?
        ) = BindServiceConnection.Observable(contextDelegate, adapter, configBuilder)

        override fun createLifecycleConnection(
            contextDelegate: ContextDelegate,
            configBuilder: BindServiceConnection.LifecycleAware.Config.Builder?
        ) = BindServiceConnection.LifecycleAware(contextDelegate, adapter, configBuilder)
    }
}