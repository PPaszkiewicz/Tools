package com.github.ppaszkiewicz.tools.toolbox.service

/**
 * Helper that has all available callbacks of [BindServiceConnection].
 *
 * Use [injectInto] to attach it.
 * */
open class BindServiceConnectionCallbacks<T> {
    fun injectInto(connection: BindServiceConnection<T>) {
        connection.onFirstConnect = ::onFirstConnect
        connection.onConnect = ::onConnect
        connection.onDisconnect = ::onDisconnect
        connection.onConnectionLost = ::onConnectionLost
        connection.onBind = ::onBind
        connection.onUnbind = ::onUnbind
        connection.onBindingDied = ::onBindingDied
        connection.onNullBinding = ::onNullBinding
    }

    open fun onFirstConnect(service: T) {}
    open fun onConnect(service: T) {}
    open fun onDisconnect(service: T) {}
    open fun onConnectionLost(service: T): Boolean {
        return false
    }

    open fun onBind() {}
    open fun onUnbind() {}
    open fun onBindingDied(): Boolean {
        return false
    }

    open fun onNullBinding() {}
}