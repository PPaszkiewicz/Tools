package com.github.ppaszkiewicz.tools.coroutines

import android.util.Log
import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** Delegate that stores WeakReference, but recreates it using [block] whenever it's lost.*/
class WeakDelegate<T>(initValue : T? = null, val block: () -> T) : ReadOnlyProperty<Any, T> {
    private var field : WeakReference<T>? = null

    init {
        initValue?.let {
            field = WeakReference(it)
        }
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        val f = field
        val fg = f?.get()
        // init the field or return value
        return when{
            f == null -> initRef()
            fg == null -> initRef()
            else -> fg
        }
    }

    private fun initRef() : T{
        val v = block()
        field = WeakReference(v)
        Log.d("WeakDelegate", "created new $v")
        return v
    }
}