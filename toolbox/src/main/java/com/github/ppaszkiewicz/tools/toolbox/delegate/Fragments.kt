package com.github.ppaszkiewicz.tools.toolbox.delegate

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/*
  Those delegates can be used to lazily create or find fragments in the FragmentManager.
  They do not add the fragment itself to the FragmentManager, it's up to user to do so.
  They're intended for fragments that are "hard" and never get destroyed as they will hold reference,
        to handle destructible fragments use temporary() or managed() otherwise you might end up
        adding fragments that were previously destroyed.

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
) = FragmentManagerProvider.Parent(this).createDelegate(tag, fragmentFactory)

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
) = FragmentManagerProvider.Parent(this).createDelegate(usePropName, fragmentFactory)

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
) = FragmentManagerProvider.Activity(this).createDelegate(tag, fragmentFactory)

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
) = FragmentManagerProvider.Activity(this).createDelegate(usePropName, fragmentFactory)

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
) = FragmentManagerProvider.Child(this).createDelegate(tag, fragmentFactory)

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

    // returns "self"
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
    ) = FragmentDelegateProvider(
        this,
        if (usePropName) null else T::class.java.name,
        buildImpl ?: NewInstanceFragmentFactory()
    )

    // builder for delegate
    inline fun <reified T : Fragment> createDelegate(
        tag: String?,
        noinline buildImpl: (() -> T)?
    ) = FragmentDelegateProvider(
        this,
        tag ?: T::class.java.name,
        buildImpl ?: NewInstanceFragmentFactory()
    )
}

/**
 * Provides [FragmentDelegate] or spawns delegates with altered behavior.
 */
class FragmentDelegateProvider<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?,
    val buildImpl: () -> T
) {
    /** Default: provide delegate that finds fragment in fragment manager or uses [buildImpl] to create it. */
    operator fun provideDelegate(
        thisRef: Any,
        property: KProperty<*>
    ) = FragmentDelegate(manager, tag, buildImpl)

    /** Assumes fragment is initialized elsewhere, throws exception if it's is missing instead of instantiating it. */
    fun required(): FragmentDelegate<T> = FragmentDelegate(manager, tag, null)

    /** Discard destroyed fragments and recreate them instead. */
    fun temporary() = Temporary(manager, tag, buildImpl)

    /** Assume fragment might not exist, return null if it's is missing instead of instantiating it. */
    fun nullable() = FragmentDelegateNullable<T>(manager, tag)

    /** Similar to [nullable] but will not return destroyed fragments. */
    fun managed() = FragmentDelegateNullable.Managed<T>(manager, tag)

    /** Considers destroyed fragments invalid for return. */
    class Temporary<T : Fragment>(
        val manager: FragmentManagerProvider,
        val tag: String?,
        val buildImpl: () -> T
    ) {
        /** Discard destroyed fragments and recreate them instead. */
        operator fun provideDelegate(
            thisRef: Any,
            property: KProperty<*>
        ): FragmentDelegate<T> = FragmentDelegate.Temporary(manager, tag, buildImpl)

        /** Assumes fragment is initialized elsewhere, throws exception if it's is missing instead of instantiating it. */
        fun required(): FragmentDelegate<T> = FragmentDelegate.Temporary(manager, tag, null)
    }
}

/** Lazy fragment delegate object. If [tag] is null then property name is used. */
open class FragmentDelegate<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?,
    protected var buildImpl: (() -> T)?
) : ReadOnlyProperty<Any, T> {
    protected var value: T? = null

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        getCurrentValue(thisRef, property)?.also { return it }
        val f = restoreValue(thisRef, property) ?: createValue(thisRef, property)
        value = f
        if (discardBuilder()) buildImpl = null
        return f
    }

    protected open fun getCurrentValue(thisRef: Any, property: KProperty<*>): T? {
        return value
    }

    protected open fun restoreValue(thisRef: Any, property: KProperty<*>): T? {
        val f = manager.get().findFragmentByTag(tag ?: (property.name)) as T?
        if (f != null) {
            value = f
            return f
        }
        return null
    }

    protected open fun createValue(thisRef: Any, property: KProperty<*>): T {
        check(buildImpl != null) { "Fragment not found and creation in this delegate is disabled." }
        return buildImpl!!()
    }

    protected open fun discardBuilder() = true

    /** Discards referenced fragment when it's destroyed and creates new instance (keeps builder reference). */
    class Temporary<T : Fragment>(
        manager: FragmentManagerProvider,
        tag: String?,
        buildImpl: (() -> T)?
    ) : FragmentDelegate<T>(manager, tag, buildImpl) {
        override fun getCurrentValue(thisRef: Any, property: KProperty<*>): T? {
            return value?.takeIf { it.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) }
        }

        override fun discardBuilder() = false
    }
}

/** Fragment delegate that returns null if target fragment is not found. */
open class FragmentDelegateNullable<T : Fragment>(
    val manager: FragmentManagerProvider,
    val tag: String?
) : ReadOnlyProperty<Any, T?> {
    protected var value: T? = null

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        getCurrentValue(thisRef, property)?.also { return it }
        val f = restoreValue(thisRef, property)
        value = f
        return f
    }

    protected open fun getCurrentValue(thisRef: Any, property: KProperty<*>): T? {
        return value
    }

    protected open fun restoreValue(thisRef: Any, property: KProperty<*>): T? {
        val f = manager.get().findFragmentByTag(tag ?: (property.name)) as T?
        if (f != null) {
            value = f
            return f
        }
        return null
    }

    /** Nullable delegate that considers destroyed fragments invalid. */
    class Managed<T : Fragment>(manager: FragmentManagerProvider, tag: String?) :
        FragmentDelegateNullable<T>(manager, tag) {
        override fun getCurrentValue(thisRef: Any, property: KProperty<*>): T? {
            return value?.takeIf { it.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) }
        }
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
    fClass::newInstance