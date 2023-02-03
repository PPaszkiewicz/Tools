@file:Suppress("Unused")

package com.github.ppaszkiewicz.tools.toolbox.viewBinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.github.ppaszkiewicz.tools.toolbox.R
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

// reflection extensions for view bindings
// requires viewbinding methods to be kept by proguard
// use following declaration:

//-keep,allowoptimization,allowobfuscation class * implements androidx.viewbinding.ViewBinding {
//    public static *** bind(android.view.View);
//    public static *** inflate(android.view.LayoutInflater);
//    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
//}


//*********** VIEW ****************/
/**
 * Viewbinding as tag on a view. Instantiates it with reflection.
 */
inline fun <reified T : ViewBinding> View.viewBinding() = viewBinding(T::class.java)

/**
 * Viewbinding as tag on a view. Instantiates it with reflection.
 */
fun <T : ViewBinding> View.viewBinding(bindingClass: Class<T>): T {
    return lazyTagValue(R.id.viewBinding, bindingClass.getBindMethod())
}

//*********** FRAGMENT ****************/
/**
 * Lazy delegate for ViewBinding. Uses reflection
 * to get the static bind method.
 *
 * @param T view binding class to instantiate
 */
inline fun <reified T : ViewBinding> Fragment.viewBinding() = viewBinding(T::class.java)


/**
 * Lazy delegate for ViewBinding. Uses reflection
 * to get the static bind method.
 * @param bindingClass view binding class to instantiate
 */
fun <T : ViewBinding> Fragment.viewBinding(bindingClass: Class<T>): ReadOnlyProperty<Fragment, T> {
    return viewValue(bindingClass.getBindMethod())
}

//*********** ACTIVITY ****************/
/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated. Uses reflection
 * to get the static inflate method.
 *
 * @param T view binding class to instantiate
 */
inline fun <reified T : ViewBinding> AppCompatActivity.viewBinding() = viewBinding(T::class.java)

/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated. Uses reflection
 * to get the static inflate method.
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(bindingClass: Class<T>): PropertyDelegateProvider<AppCompatActivity, ReadOnlyProperty<AppCompatActivity, T>> {
    return ActivityViewBindingDelegateProvider(bindingClass.getInflateMethod())
}

/**
 * Delegate for ViewBinding that binds it lazily without actually performing any inflation. Uses
 * reflection to get the static inflate method.
 * */
fun <T : ViewBinding> AppCompatActivity.viewBindingLazy(bindingClass: Class<T>): Lazy<T>{
    return viewBindingLazy(bindingClass.getBindMethod())
}

/**
 * Delegate for ViewBinding that binds it lazily without actually performing any inflation. Uses
 * reflection to get the static inflate method.
 * */
inline fun <reified T : ViewBinding> AppCompatActivity.viewBindingLazy(): Lazy<T>{
    return viewBindingLazy(T::class.java)
}

// helpers for nameless class search - it's expected that viewbinding will be obfuscated but not shrunk

/** Get inflate method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getInflateMethod(): (inflater: LayoutInflater) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 1 && it[0] == LayoutInflater::class.java } }
    return { inflater: LayoutInflater -> src(null, inflater) as T }
}

/** Get bind method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getBindMethod(): (root: View) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 1 && it[0] == View::class.java } }
    return { inflater: View -> src(null, inflater) as T }
}

/** Get "inflate in parent" method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getInflateInParentMethod(): (inflater: LayoutInflater, parent: ViewGroup, attach: Boolean) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 3 && it[0] == LayoutInflater::class.java } }
    return { inflater: LayoutInflater, parent: ViewGroup, attach: Boolean ->
        src(null, inflater, parent, attach) as T
    }
}