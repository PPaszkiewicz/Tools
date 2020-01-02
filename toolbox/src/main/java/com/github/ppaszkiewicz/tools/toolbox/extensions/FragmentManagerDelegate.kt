package com.github.ppaszkiewicz.tools.toolbox.extensions

/*
    Requires FragmentManager.kt
 */
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
  Methods to use.
 */

/**
 * Obtain fragment of this type from fragment manager.
 * This fragment needs to have unique static TAG field.
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragmentManager() =
    supportFragmentManager.provider.createDelegate<T>()

/**
 * Obtain fragment of this type from fragment manager.
 * This fragment needs to have unique static TAG field.
 *
 * If fragment is not found  [fragmentFactory] is invoked to create it.
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragmentManager(noinline fragmentFactory: () -> T) =
    supportFragmentManager.provider.createDelegate(fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 * This fragment needs to have unique static TAG field.
 * */
inline fun <reified T : Fragment> Fragment.parentFragmentManager() =
    FragmentManagerProvider.Parent(this).createDelegate<T>()

/**
 * Obtain fragment of this type from host activity fragment manager.
 * This fragment needs to have unique static TAG field.
 *
 * If fragment is not found  [fragmentFactory] is invoked to create it.
 * */
inline fun <reified T : Fragment> Fragment.parentFragmentManager(noinline fragmentFactory: () -> T) =
    FragmentManagerProvider.Parent(this).createDelegate(fragmentFactory)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 * This fragment needs to have unique static TAG field.
 * */
inline fun <reified T : Fragment> Fragment.childFragmentManager() =
    FragmentManagerProvider.Child(this).createDelegate<T>()

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 * This fragment needs to have unique static TAG field.
 *
 * If fragment is not found  [fragmentFactory] is invoked to create it.
 * */
inline fun <reified T : Fragment> Fragment.childFragmentManager(noinline fragmentFactory: () -> T) =
    FragmentManagerProvider.Child(this).createDelegate(fragmentFactory)

/*  Internal - backing delegates. */

/** Provider of fragment manager and delegates. */
sealed class FragmentManagerProvider {
    abstract fun get(): FragmentManager
    // returns provide value
    class Direct(private val fm: FragmentManager) : FragmentManagerProvider() {
        override fun get() = fm
    }

    class Parent(private val f: Fragment) : FragmentManagerProvider() {
        override fun get() = f.parentFragmentManager
    }

    // returns child fragment manager when possible
    class Child(private val f: Fragment) : FragmentManagerProvider() {
        override fun get() = f.childFragmentManager
    }

    inline fun <reified T : Fragment> createDelegate(): ReadOnlyProperty<Any, T> {
        return FragmentDelegate(this, T::class.java.TAG, NewInstanceFragmentFactory())
    }

    inline fun <reified T : Fragment> createDelegate(noinline buildImpl: () -> T): ReadOnlyProperty<Any, T> {
        return FragmentDelegate(this, T::class.java.TAG, buildImpl)
    }
}

/** Lazy fragment delegate object. */
class FragmentDelegate<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String,
    val buildImpl: () -> T
) : ReadOnlyProperty<Any, T> {
    private var value: T? = null
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (value != null) value!!
        val f = manager.get().findFragmentByTag(tag) as T?
        if (f != null) {
            value = f
            return f
        }
        val f2 = buildImpl()
        value = f2
        return f2
    }
}

/*
    Internal - helper extensions.
 */

/** Get object that returns this fragment manager. */
@PublishedApi
internal val FragmentManager.provider: FragmentManagerProvider
    get() = FragmentManagerProvider.Direct(this)

/** Default fragment factory. */
@PublishedApi
internal inline fun <reified T : Fragment> NewInstanceFragmentFactory() =
    NewInstanceFragmentFactory(T::class.java)

/** Default fragment factory. */
@PublishedApi
internal fun <T : Fragment> NewInstanceFragmentFactory(fClass: Class<T>): () -> T =
    { fClass.newInstance() as T }