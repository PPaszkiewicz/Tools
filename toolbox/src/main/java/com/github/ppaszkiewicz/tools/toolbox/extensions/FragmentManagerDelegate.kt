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
  Those delegates can be used to lazily create or find fragments in the FragmentManager.
  They do not add the fragment itself to the FragmentManager, it's up to user to do so.
  Delegate factory functions below.
 */

/**
 * Obtain fragment of this type from fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragmentManager(tag: String? = null) =
    supportFragmentManager.provider.createDelegate<T>(tag)

/**
 * Obtain fragment of this type from fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragmentManager(tag: String? = null, noinline fragmentFactory: () -> T) =
    supportFragmentManager.provider.createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * */
inline fun <reified T : Fragment> Fragment.parentFragmentManager(tag: String? = null) =
    FragmentManagerProvider.Parent(this).createDelegate<T>(tag)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager
 * */
inline fun <reified T : Fragment> Fragment.parentFragmentManager(tag: String? = null, noinline fragmentFactory: () -> T) =
    FragmentManagerProvider.Parent(this).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * */
inline fun <reified T : Fragment> Fragment.childFragmentManager(tag: String? = null) =
    FragmentManagerProvider.Child(this).createDelegate<T>(tag)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager
 * */
inline fun <reified T : Fragment> Fragment.childFragmentManager(tag: String? = null, noinline fragmentFactory: () -> T) =
    FragmentManagerProvider.Child(this).createDelegate(tag, fragmentFactory)

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

    inline fun <reified T : Fragment> createDelegate(tag: String? = null) =
        createDelegate<T>(tag, NewInstanceFragmentFactory())

    inline fun <reified T : Fragment> createDelegate(
        tag: String? = null,
        noinline buildImpl: () -> T
    ): FragmentManagerDelegateByPropName<T> {
        return FragmentManagerDelegateByPropName(this, tag, buildImpl, T::class.java)
    }
}

/** Lazy fragment delegate object. If [tag] is null then property name is used. */
sealed class FragmentDelegate<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?,
    buildImpl: () -> T
) : ReadOnlyProperty<Any, T> {
    protected var buildImpl: (() -> T)? = buildImpl
    protected var value: T? = null

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (value != null) {
            buildImpl = null
            return value!!
        }
        val f = manager.get().findFragmentByTag(tag ?: (property.name)) as T?
        if (f != null) {
            value = f
            return f
        }
        val f2 = buildImpl!!()
        buildImpl = null
        value = f2
        return f2
    }
}

/** Lazy fragment delegate object that uses provided tag or property name if tag is null. */
class FragmentManagerDelegateByPropName<T : Fragment>(
    manager: FragmentManagerProvider,
    tag: String?,
    buildImpl: () -> T,
    // class that can be optionally consumed by useTag()
    var fClass: Class<T>?
) : FragmentDelegate<T>(manager, tag, buildImpl){
    /** Use fragments static TAG field instead of property name. */
    fun useTag() = FragmentManagerDelegateByTag(manager, fClass!!.TAG, getBuildImpl)

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        fClass = null
        return super.getValue(thisRef, property)
    }

    @PublishedApi
    internal val getBuildImpl
        get() = buildImpl!!
}

/** Lazy fragment delegate object that uses [Fragment.TAG]. */
class FragmentManagerDelegateByTag<T : Fragment>(
    manager: FragmentManagerProvider,
    tag: String?,
    buildImpl: () -> T
) : FragmentDelegate<T>(manager, tag, buildImpl)

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