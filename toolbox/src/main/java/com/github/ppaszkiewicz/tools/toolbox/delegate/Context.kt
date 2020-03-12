package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.app.Application
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// helper class needed to lazily obtain a context to safely provide a lazy delegate
// in fragments etc
/** Context delegate for classes that can return a [Context]. */
sealed class ContextDelegate : ReadOnlyProperty<Any, Context> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = get()
    abstract fun get() : Context

    /** Returns self. */
    class OfContext(private val context: Context) : ContextDelegate() {
        override fun get() = context
    }

    /** Returns fragments context. Fragment might not have context attached when this wrapper is created. */
    class OfFragment(private val fragment: Fragment) : ContextDelegate() {
        override fun get() = fragment.requireContext()
    }
}

/** Delegate that returns context. */
val Context.contextDelegate
    get() = ContextDelegate.OfContext(
        this
    )
/** Delegate that returns context. */
val Fragment.contextDelegate
    get() = ContextDelegate.OfFragment(
        this
    )
/** Delegate that returns context. */
val AndroidViewModel.contextDelegate
    get() = getApplication<Application>().contextDelegate
/** Delegate that returns context. */
val View.contextDelegate
    get() = context.contextDelegate