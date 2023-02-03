@file:Suppress("Unused")

package com.github.ppaszkiewicz.tools.toolbox.viewBinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle.DelegateProvider
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.viewbinding.ViewBinding
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Core ViewBinding delegates

//*********** FRAGMENT ****************/

// extensions to keep values in fragments - not restricted to ViewBinding
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

//*********** ACTIVITY ****************/
/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated.
 * @param createBinding use provided [LayoutInflater] to create the binding (by using static `inflate` method).
 *
 * For example:
 *
 *     class MyActivity : AppCompatActivity() {
 *          val binding by viewBinding { MyActivityBinding.inflate(it) }
 *          // alternatively
 *          val binding2 by viewBinding(MyActivityBinding::inflate)
 *          ...
 *      }
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(createBinding: (LayoutInflater) -> T): PropertyDelegateProvider<AppCompatActivity, ReadOnlyProperty<AppCompatActivity, T>> =
    ActivityViewBindingDelegateProvider(createBinding)

/**
 * Delegate for ViewBinding that binds it lazily without actually performing any inflation.
 * @param bindingFactory use provided [View] to establish the binding (by using static `bind` method).
 *
 * For example:
 *
 *     class MyActivity : AppCompatActivity(R.layout.my_activity) {
 *          val binding by viewBinding { MyActivityBinding.bind(it) }
 *          // alternatively
 *          val binding2 by viewBinding(MyActivityBinding::bind)
 *          ...
 *      }
 */
fun <T : ViewBinding> AppCompatActivity.viewBindingLazy(bindingFactory: (View) -> T): Lazy<T> =
    lazy {
        val root = requireNotNull(findViewById<ViewGroup>(android.R.id.content).getChildAt(0)) {
            "viewBindingLazy requires manual layout inflation, ${this@viewBindingLazy.javaClass.name} has no view"
        }
        bindingFactory(root)
    }

// backing delegates

private class ViewBoundValueDelegate<T>(private val valueFactory: (View) -> T) :
    ReadOnlyProperty<Fragment, T>, Observer<LifecycleOwner> {
    private var value: T? = null
    private var ld: LiveData<LifecycleOwner>? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            synchronized(this) {
                value?.let { return it }
                check(ld == null) { "viewLifecycleOwner was not cleared since previous value initialization. Fragment state is: ${thisRef.lifecycle.currentState}" }
                ld = thisRef.viewLifecycleOwnerLiveData.also { it.observeForever(this) }
                value = valueFactory(thisRef.requireView())
            }
        }
        return value!!
    }

    override fun onChanged(viewLifecycleOwner: LifecycleOwner?) {
        if (viewLifecycleOwner == null) {
            synchronized(this) {
                value = null
                ld!!.removeObserver(this)
                ld = null
            }
        }
    }
}

@JvmInline
internal value class ActivityViewBindingDelegateProvider<T : ViewBinding>(private val createBindingImpl: (LayoutInflater) -> T) :
    PropertyDelegateProvider<AppCompatActivity, ReadOnlyProperty<AppCompatActivity, T>> {
    override fun provideDelegate(
        thisRef: AppCompatActivity,
        property: KProperty<*>
    ): ReadOnlyProperty<AppCompatActivity, T> {
        return ActivityViewBindingDelegate(thisRef, createBindingImpl)
    }
}

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
        val content = checkNotNull(activity.findViewById<ViewGroup>(android.R.id.content)) {
            "unexpected activity inflation: ${activity.javaClass.name} has no content frame."
        }
        check(content.childCount == 0) {
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