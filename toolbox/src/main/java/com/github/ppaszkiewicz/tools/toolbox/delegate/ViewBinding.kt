package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// View binding delegate (requires AS 3.6 and view binding generation enabled)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed. Uses reflection
 * to get the static bind method.
 *
 * @param T view binding class to instantiate
 */
inline fun<reified T: ViewBinding> Fragment.viewBinding() = viewBinding(T::class.java)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed. Uses reflection
 * to get the static bind method.
 * @param bindingClass view binding class to instantiate
 */
fun <T: ViewBinding> Fragment.viewBinding(bindingClass: Class<T>) : ReadOnlyProperty<Fragment, T> =
    ViewBindingDelegate.ReflectViewBindingDelegate(bindingClass)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed.
 * @param createBinding use provided view to create the binding (by using static *bind* method).
 *
 * For example:
 *
 *          val binding by viewBinding { MyFragmentBinding.bind(it) }.
 */
@Suppress("Unused")
fun <T: ViewBinding> Fragment.viewBinding(createBinding: (View) -> T) : ReadOnlyProperty<Fragment, T> =
    ViewBindingDelegate.CustomViewBindingDelegate(createBinding)

// backing viewbinding delegate implementation
private sealed class ViewBindingDelegate<T : ViewBinding>() : ReadOnlyProperty<Fragment, T>,
    LifecycleObserver {
    private var value : T? = null

    abstract fun createBinding(thisRef: Fragment) : T

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if(value == null){
            value = createBinding(thisRef)
            thisRef.viewLifecycleOwner.lifecycle.addObserver(this)
        }
        return value!!
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    @Suppress("Unused")
    fun onDestroyView(){
        value = null
    }

    // uses provided binding factory
    internal class CustomViewBindingDelegate<T : ViewBinding>(private val createBindingImpl: (View) -> T) : ViewBindingDelegate<T>(){
        override fun createBinding(thisRef: Fragment) = createBindingImpl(thisRef.requireView())
    }

    // uses reflected bind method
    internal class ReflectViewBindingDelegate<T : ViewBinding>(bindingClass: Class<T>) : ViewBindingDelegate<T>(){
        private val bindMethod = bindingClass.getDeclaredMethod("bind", View::class.java)
        override fun createBinding(thisRef: Fragment) = bindMethod(null, thisRef.requireView()) as T
    }
}

// PLACEHOLDER ALIAS - used only to prevent compilation errors when viewbinding is disabled
// don't copy this - androidx.viewbinding.ViewBinding should be imported instead
typealias ViewBinding = Any