package com.github.ppaszkiewicz.tools.services

/**
 * Hosts factories for remote binding services.
 */
object RemoteBindService {
    /**
     * Connection factory that creates connections to services that are not [DirectBindService] and need
     * custom [BindServiceConnection.Adapter] implementation.
     */
    abstract class ConnectionFactory<T> : BindServiceConnection.ConnectionFactory.Remote<T>()

    /**
     * Connection factory that creates connections to services that are not [DirectBindService] and need
     * custom [adapter] implementation.
     */
    @Suppress("FunctionName")
    fun <T> ConnectionFactory(adapter: BindServiceConnection.Adapter<T>) =
        BindServiceConnection.ConnectionFactory.Default(adapter)
}