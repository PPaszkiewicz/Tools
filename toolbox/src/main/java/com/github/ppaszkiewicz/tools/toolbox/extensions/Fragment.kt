package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Build and [Fragment.setArguments] of this fragment. Use only when constructing
 * fragment from within new instance.
 */
inline fun <T: Fragment>T.withArguments(argBuilder : Bundle.() -> Unit) = apply {
    arguments = Bundle().apply(argBuilder)
}

// View binding delegates (requires AS 3.6 and view binding generation enabled)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed. Uses reflection
 * to get the static factory method.
 */
inline fun<reified T: ViewBinding> Fragment.viewBinding() : ReadOnlyProperty<Fragment, T> = viewBinding{
    val bindMethod = T::class.java.getDeclaredMethod("bind", View::class.java)
    bindMethod(null, view) as T
}

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed.
 * @param createBinding use provided view to create the binding (by using static *bind* method).
 *
 * For example: _MainActivityBinding.bind(it)_.
 */
@Suppress("Unused")
fun <T: ViewBinding> Fragment.viewBinding(createBinding: (View) -> T) : ReadOnlyProperty<Fragment, T> =
    LazyViewBinding(createBinding)

// backing viewbinding delegate implementation
private class LazyViewBinding<T : ViewBinding>(private val createBinding: (View) -> T) : ReadOnlyProperty<Fragment, T>,
    LifecycleObserver {
    private var value : T? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if(value == null){
            value = createBinding(thisRef.requireView())
            thisRef.viewLifecycleOwner.lifecycle.addObserver(this)
        }
        return value!!
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    @Suppress("Unused")
    fun onDestroyView(){
        value = null
        Log.d("delegate", "view destroyed")
    }
}

// PLACEHOLDER ALIAS - used only to prevent compilation errors when viewbinding is disabled
// don't copy this - androidx.viewbinding.ViewBinding should be imported instead
typealias ViewBinding = Any