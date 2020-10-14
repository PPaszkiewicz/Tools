package com.github.ppaszkiewicz.kotlin.tools.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LiveData

/** Methods required to proxy between service and its connection. */
interface BindServiceConnectionProxy<T> {
    /** Intent that is used to bind to the service. */
    fun createBindingIntent(context: Context): Intent

    /** Transform [binder] object into valid [LiveData] value of service connection. */
    fun transformBinder(name: ComponentName, binder: IBinder): T
}