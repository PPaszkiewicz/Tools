package com.github.ppaszkiewicz.tools.toolbox.delegate

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
 * @param tag Tag used to identify this fragment in the fragment manager.If null fragments class name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = supportFragmentManager.provider.createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from fragment manager.
 *
 * @param usePropName if if true then property name is used to identify this fragment
 * in the fragment manager. If false class name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragments(
    usePropName: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = supportFragmentManager.provider.createDelegate(usePropName, fragmentFactory)

/**
 * Obtain fragment of this type from parent fragment manager (one this fragment is in).
 *
 * @param tag Tag used to identify this fragment in the fragment manager.If null fragments class name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.parentFragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Activity(
    this
).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from parent fragment manager (one this fragment is in).
 *
 * @param usePropName if if true then property name is used to identify this fragment
 * in the fragment manager. If false class name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.parentFragments(
    usePropName: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Activity(
    this
).createDelegate(usePropName, fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager.If null fragments class name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.activityFragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Parent(
    this
).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param usePropName if if true then property name is used to identify this fragment
 * in the fragment manager. If false class name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.activityFragments(
    usePropName: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Parent(
    this
).createDelegate(usePropName, fragmentFactory)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager.If null fragments class name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.fragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Child(
    this
).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 *
 * @param usePropName if if true then property name is used to identify this fragment
 * in the fragment manager. If false class name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.fragments(
    usePropName: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Child(
    this
).createDelegate(usePropName, fragmentFactory)

/*  Internal - backing delegates. */

/** Provider of fragment manager and delegates. */
sealed class FragmentManagerProvider : ReadOnlyProperty<Any, FragmentManager> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = get()
    abstract fun get(): FragmentManager
    // returns provide value
    class Direct(private val fm: FragmentManager) : FragmentManagerProvider() {
        override fun get() = fm
    }

    class Activity(private val f: Fragment) : FragmentManagerProvider() {
        override fun get() = f.requireActivity().supportFragmentManager
    }

    class Parent(private val f: Fragment) : FragmentManagerProvider() {
        override fun get() = f.parentFragmentManager
    }

    // returns child fragment manager when possible
    class Child(private val f: Fragment) : FragmentManagerProvider() {
        override fun get() = f.childFragmentManager
    }

    // builder for delegate
    inline fun <reified T : Fragment> createDelegate(
        usePropName: Boolean,
        noinline buildImpl: (() -> T)?
    ): FragmentManagerDelegatePrimary<T> {
        return FragmentManagerDelegatePrimary(
            this,
            if (usePropName) null else T::class.java.name,
            buildImpl
                ?: NewInstanceFragmentFactory()
        )
    }

    // builder for delegate
    inline fun <reified T : Fragment> createDelegate(
        tag: String?,
        noinline buildImpl: (() -> T)?
    ): FragmentManagerDelegatePrimary<T> {
        return FragmentManagerDelegatePrimary(
            this,
            tag ?: T::class.java.name,
            buildImpl
                ?: NewInstanceFragmentFactory()
        )
    }
}

/** Lazy fragment delegate object. If [tag] is null then property name is used. */
sealed class FragmentDelegate<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?,
    private var buildImpl: (() -> T)?
) : ReadOnlyProperty<Any, T> {
    protected var value: T? = null

    @Suppress("UNCHECKED_CAST")
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
        check(buildImpl != null) { "Fragment not found and creation in this delegate is disabled." }
        val f2 = buildImpl!!()
        buildImpl = null
        value = f2
        return f2
    }
}

/**
 * Fragment delegate object that uses provided tag or property name if tag is null.
 *
 * Also provides factory methods to alter how it works by altering self parameters.
 * */
class FragmentManagerDelegatePrimary<T : Fragment>(
    manager: FragmentManagerProvider,
    tag: String?,
    buildImpl: () -> T
) : FragmentDelegate<T>(manager, tag, buildImpl) {
    /** Assumes fragment is initialized elsewhere, throws exception if it's is missing instead of instantiating it. */
    fun required(): FragmentDelegate<T> =
        FragmentManagerDelegateSecondary(
            manager,
            tag,
            null
        )

    /** Assume fragment might not exist, return null if it's is missing instead of instantiating it. */
    fun nullable() =
        FragmentDelegateNullable<T>(
            manager,
            tag
        )
}

/** Fragment delegate spawned by [FragmentManagerDelegatePrimary]. */
class FragmentManagerDelegateSecondary<T : Fragment>(
    manager: FragmentManagerProvider,
    tag: String?,
    buildImpl: (() -> T)?
) : FragmentDelegate<T>(manager, tag, buildImpl)

/** Fragment delegate that returns null if target fragment is not found. */
class FragmentDelegateNullable<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?
) : ReadOnlyProperty<Any, T?> {
    private var value: T? = null

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        if (value != null) return value
        val f = manager.get().findFragmentByTag(tag ?: (property.name)) as T?
        if (f != null) {
            value = f
        }
        return f
    }
}

/*
    Internal - helper extensions.
 */

/** Get object that returns this fragment manager. */
@PublishedApi
internal val FragmentManager.provider: FragmentManagerProvider
    get() = FragmentManagerProvider.Direct(
        this
    )

/** Default fragment factory. */
@PublishedApi
internal inline fun <reified T : Fragment> NewInstanceFragmentFactory() =
    NewInstanceFragmentFactory(
        T::class.java
    )

/** Default fragment factory. */
@PublishedApi
internal fun <T : Fragment> NewInstanceFragmentFactory(fClass: Class<T>): () -> T =
    fClass::newInstance