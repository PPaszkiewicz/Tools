@file:Suppress("Unused")

package com.github.ppaszkiewicz.tools.toolbox.viewBinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Delegates for ViewBindings and values that should be auto-cleared
// alongside fragments view

//*********** FRAGMENT ****************/

// extensions to keep values in fragments - not restricted to viewbinding
/**
 * Lazy delegate for value that's bound to views lifecycle - released when view is destroyed.
 * @param initValue use provided view to create the value
 */

fun <T> Fragment.viewValue(initValue: (View) -> T): ReadOnlyProperty<Fragment, T> =
    ViewBoundValueDelegate(initValue)

/**
 * Lazy delegate for ViewBinding that's released when fragments view is destroyed.
 * @param bindingFactory factory to create the binding
 */
fun <T : ViewBinding> Fragment.viewBinding(bindingFactory: (View) -> T) = viewValue(bindingFactory)

/** Delegate that observes lifecycle.*/
private class ViewBoundValueDelegate<T>(private val valueFactory: (View) -> T) :
    ReadOnlyProperty<Fragment, T>, Observer<LifecycleOwner> {
    private var value: T? = null
    private var ld: LiveData<LifecycleOwner>? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            synchronized(this) {
                value?.let { return it }
                check(ld == null) { "viewLifecycleOwner was not cleared since previous value initialization" }
                ld = thisRef.viewLifecycleOwnerLiveData.also { it.observeForever(this) }
                value = valueFactory(thisRef.requireView())
            }
        }
        return value!!
    }

    override fun onChanged(viewLifecycleOwner: LifecycleOwner?) {
        if (viewLifecycleOwner == null) {
            value = null
            ld!!.removeObserver(this)
            ld = null
        }
    }
}

//*********** ACTIVITY ****************/
/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated.
 * @param createBinding use provided [LayoutInflater] to create the binding (by using static `inflate` method).
 *
 * For example:
 *
 *          val binding by viewBinding { MyActivityBinding.inflate(it) }
 *          // alternatively
 *          val binding2 by viewBinding(MyActivityBinding::inflate)
 */
@Suppress("Unused")
fun <T : ViewBinding> AppCompatActivity.viewBinding(createBinding: (LayoutInflater) -> T) =
    ActivityViewBindingDelegateProvider(createBinding)

/** Provider for [ActivityViewBindingDelegate]. */
@JvmInline
value class ActivityViewBindingDelegateProvider<T : ViewBinding>(private val createBindingImpl: (LayoutInflater) -> T) {
    operator fun provideDelegate(
        thisRef: AppCompatActivity,
        property: KProperty<*>
    ): ReadOnlyProperty<AppCompatActivity, T> {
        return ActivityViewBindingDelegate(thisRef, createBindingImpl)
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
        require(thisRef === activity) { "thisRef must be equal to activity this delegate was created for" }
        return get()
    }

    private fun get(): T {
        if (value == null) {
            synchronized(this) {
                value?.let { return it }
                checkContentIsEmpty()
                val v = createBindingImpl(activity.layoutInflater)
                activity.setContentView(v.root)
                value = v
                activity.lifecycle.removeObserver(this)
            }
        }
        return value!!
    }

    private fun checkContentIsEmpty() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        require(content != null) {
            "unexpected activity inflation: ${activity.javaClass.name} has no content frame."
        }
        require(content.childCount == 0) {
            "unexpected activity inflation: ${activity.javaClass.name} already has content view."
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    @Suppress("Unused")
    fun onCreated() {
        // this is a fallback to ensure viewbinding will be created even if not referenced during onCreate
        if (value == null) get()
        activity.lifecycle.removeObserver(this)
    }
}