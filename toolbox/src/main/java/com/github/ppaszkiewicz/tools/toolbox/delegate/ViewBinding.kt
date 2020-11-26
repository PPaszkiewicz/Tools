package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// View binding delegate (requires AS 3.6 and view binding generation enabled)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed. Uses reflection
 * to get the static bind method.
 *
 * @param T view binding class to instantiate
 */
inline fun <reified T : ViewBinding> Fragment.viewBinding() = viewBinding(T::class.java)

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed. Uses reflection
 * to get the static bind method.
 * @param bindingClass view binding class to instantiate
 */
fun <T : ViewBinding> Fragment.viewBinding(bindingClass: Class<T>): ReadOnlyProperty<Fragment, T> {
    val bindMethod = bindingClass.getDeclaredMethod("bind", View::class.java)
    return viewBinding { bindMethod(null, it) as T }
}

/**
 * Lazy delegate for ViewBinding that automatically releases it when view is destroyed.
 * @param createBinding use provided view to create the binding (by using static *bind* method).
 *
 * For example:
 *
 *          val binding by viewBinding { MyFragmentBinding.bind(it) }.
 */
@Suppress("Unused")
fun <T : ViewBinding> Fragment.viewBinding(createBinding: (View) -> T): ReadOnlyProperty<Fragment, T> =
    FragmentViewBindingDelegate(createBinding)

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
 *
 * @param bindingClass view binding class to instantiate
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(bindingClass: Class<T>): ActivityViewBindingProvider<T>{
    val inflate = bindingClass.getDeclaredMethod("inflate", LayoutInflater::class.java)
    return ActivityViewBindingDelegate.Provider{ inflate(null, it) as T }
}


/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated.
 * @param createBinding use provided [LayoutInflater] to create the binding (by using static `inflate` method).
 *
 * For example:
 *
 *          val binding by viewBinding { MyActivityBinding.inflate(it) }.
 */
@Suppress("Unused")
fun <T : ViewBinding> AppCompatActivity.viewBinding(createBinding: (LayoutInflater) -> T): ActivityViewBindingProvider<T> =
    ActivityViewBindingDelegate.Provider(createBinding)

/** Provides delegates for activity view bindings. */
interface ActivityViewBindingProvider<T : ViewBinding> {
    operator fun provideDelegate(
        thisRef: AppCompatActivity,
        property: KProperty<*>
    ): ReadOnlyProperty<AppCompatActivity, T>
}

// backing viewbinding delegate implementations

private class FragmentViewBindingDelegate<T : ViewBinding>(private val createBindingImpl: (View) -> T) :
    ReadOnlyProperty<Fragment, T>,
    LifecycleObserver {
    private var value: T? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            value = createBindingImpl(thisRef.requireView())
            thisRef.viewLifecycleOwner.lifecycle.addObserver(this)
        }
        return value!!
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    @Suppress("Unused")
    fun onDestroyView() {
        value = null
    }
}


// backing delegate
private class ActivityViewBindingDelegate<T : ViewBinding>(
    private val activity: AppCompatActivity,
    private val createBindingImpl: (LayoutInflater) -> T
) : ReadOnlyProperty<AppCompatActivity, T>, LifecycleObserver {
    private var value: T? = null

    init {
        @Suppress("LeakingThis")
        activity.lifecycle.addObserver(this)
    }

    override fun getValue(thisRef: AppCompatActivity, property: KProperty<*>): T {
        require(thisRef === activity)
        return get()
    }

    private fun get(): T {
        if (value == null) {
            checkContentIsEmpty()
            val v = createBindingImpl(activity.layoutInflater)
            activity.setContentView(v.root)
            value = v
            activity.lifecycle.removeObserver(this)
        }
        return value!!
    }

    private fun checkContentIsEmpty() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content != null && content.childCount > 0)
            Log.w(
                "ViewBindingDelegate",
                "unexpected activity inflation: ${activity.javaClass.name} already has content view. "
            )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    @Suppress("Unused")
    fun onCreated() {
        // this is a fallback to ensure viewbinding will be created even if not referenced during onCreate
        if (value == null) get()
    }

    class Provider<T : ViewBinding>(private val createBindingImpl: (LayoutInflater) -> T) :
        ActivityViewBindingProvider<T> {
        override operator fun provideDelegate(
            thisRef: AppCompatActivity,
            property: KProperty<*>
        ): ReadOnlyProperty<AppCompatActivity, T> {
            return ActivityViewBindingDelegate(thisRef, createBindingImpl)
        }
    }
}