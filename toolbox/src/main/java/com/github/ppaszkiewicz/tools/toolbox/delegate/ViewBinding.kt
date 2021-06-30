package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// View binding delegate (requires AS 3.6 and view binding generation enabled)
// requires viewbinding to be excluded by proguard:

//-keep class * implements androidx.viewbinding.ViewBinding {
//    public static *** bind(android.view.View);
//    public static *** inflate(android.view.LayoutInflater);
//    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
//}

//*********** FRAGMENT ****************/

// note this delegate is not restricted to viewbinding - it can be used for anything that needs
// to live alongside view
/**
 * Lazy delegate for value that's bound to views lifecycle - released when view is destroyed.
 * @param initValue use provided view to create the value
 */
@Suppress("Unused")
fun <T> Fragment.viewValue(initValue: (View) -> T): ReadOnlyProperty<Fragment, T> =
    ViewBoundValueDelegate(initValue)

// backing delegate implementations
private class ViewBoundValueDelegate<T>(private val valueFactory: (View) -> T) :
    ReadOnlyProperty<Fragment, T>, Observer<LifecycleOwner> {
    private var value: T? = null
    private var ld: LiveData<LifecycleOwner>? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (value == null) {
            check(ld == null) { "viewLifecycleOwner was not cleared since previous value initialization" }
            ld = thisRef.viewLifecycleOwnerLiveData.also { it.observeForever(this) }
            value = valueFactory(thisRef.requireView())
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
    return viewValue { bindMethod(null, it) as T }
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
 *
 * @param bindingClass view binding class to instantiate
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(bindingClass: Class<T>): ActivityViewBindingDelegateProvider<T> {
    val inflate = bindingClass.getDeclaredMethod("inflate", LayoutInflater::class.java)
    return ActivityViewBindingDelegateProvider { inflate(null, it) as T }
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
}