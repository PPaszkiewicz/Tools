package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Start service of given class without any specific extras.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startService(action: String? = null) =
    Intent(this, T::class.java).also {
        it.action = action
        startService(it)
    }

/**
 * Start service of given class with [action] and manually edit extras bundle.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startService(
    action: String,
    editExtras: Bundle.() -> Unit
): Intent {
    return Intent(this, T::class.java).also {
        it.action = action
        val extras = Bundle().also(editExtras)
        it.putExtras(extras)
        startService(it)
    }
}

/**
 * Start service of given class and manipulate start intent.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startService(
    editStartIntent: Intent.() -> Unit
): Intent {
    return Intent(this, T::class.java).also {
        it.editStartIntent()
        startService(it)
    }
}

/**
 * Start foreground service of given class without any specific extras.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startForegroundService(action: String? = null) =
    Intent(this, T::class.java).also {
        it.action = action
        ContextCompat.startForegroundService(this, it)
    }

/**
 * Start foreground service of given class with [action] and manually edit extras bundle.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startForegroundService(
    action: String,
    editExtras: Bundle.() -> Unit
): Intent {
    return Intent(this, T::class.java).also {
        it.action = action
        val extras = Bundle().also(editExtras)
        it.putExtras(extras)
        ContextCompat.startForegroundService(this, it)
    }
}

/**
 * Start foreground service of given class and manipulate start intent.
 * @return intent that started the service
 * */
inline fun <reified T : Service> Context.startForegroundService(
    editStartIntent: Intent.() -> Unit
): Intent {
    return Intent(this, T::class.java).also {
        it.editStartIntent()
        ContextCompat.startForegroundService(this, it)
    }
}