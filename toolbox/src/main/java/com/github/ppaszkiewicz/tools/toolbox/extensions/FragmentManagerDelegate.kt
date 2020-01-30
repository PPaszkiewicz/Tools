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
 * @param reflectTag if true then reflected static TAG is used to identify this fragment
 * in the fragment manager. If false property name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> AppCompatActivity.fragments(
    reflectTag: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = supportFragmentManager.provider.createDelegate(reflectTag, fragmentFactory)

/**
 * Obtain fragment of this type from parent fragment manager (one this fragment is in).
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.parentFragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Activity(this).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from parent fragment manager (one this fragment is in).
 *
 * @param reflectTag if true then reflected static TAG is used to identify this fragment
 * in the fragment manager. If false property name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.parentFragments(
    reflectTag: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Activity(this).createDelegate(reflectTag, fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
 * will be used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.activityFragments(
    tag: String? = null,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Parent(this).createDelegate(tag, fragmentFactory)

/**
 * Obtain fragment of this type from host activity fragment manager.
 *
 * @param reflectTag if true then reflected static TAG is used to identify this fragment
 * in the fragment manager. If false property name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.activityFragments(
    reflectTag: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Parent(this).createDelegate(reflectTag, fragmentFactory)

/**
 * Obtain fragment of this type from this fragments child fragment manager.
 *
 * @param tag Tag used to identify this fragment in the fragment manager. If null this property name
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
 * @param reflectTag if true then reflected static TAG is used to identify this fragment
 * in the fragment manager. If false property name is used
 * @param fragmentFactory factory used if fragment is not found in the fragment manager. If null
 * then no-arg constructor is invoked
 * */
inline fun <reified T : Fragment> Fragment.fragments(
    reflectTag: Boolean,
    noinline fragmentFactory: (() -> T)? = null
) = FragmentManagerProvider.Child(this).createDelegate(reflectTag, fragmentFactory)

/*  Internal - backing delegates. */

/** Provider of fragment manager and delegates. */
sealed class FragmentManagerProvider {
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

    inline fun <reified T : Fragment> createDelegate(
        useTag: Boolean,
        noinline buildImpl: (() -> T)?
    ): FragmentManagerDelegatePrimary<T> {
        return FragmentManagerDelegatePrimary(
            this,
            if(useTag) T::class.java.classTAG else null,
            buildImpl ?: NewInstanceFragmentFactory()
        )
    }

    inline fun <reified T : Fragment> createDelegate(
        tag: String? = null,
        noinline buildImpl: (() -> T)?
    ): FragmentManagerDelegatePrimary<T> {
        return FragmentManagerDelegatePrimary(
            this,
            tag,
            buildImpl ?: NewInstanceFragmentFactory()
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
    /** Throws exception if fragment is missing instead of instantiating it. */
    fun findOnly(): FragmentDelegate<T> = FragmentManagerDelegateSecondary(manager, tag, null)

    /** Return null if fragment is missing instead of instantiating it. */
    fun findNullable() = FragmentDelegateNullable<T>(manager, tag)
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
    get() = FragmentManagerProvider.Direct(this)

/** Default fragment factory. */
@PublishedApi
internal inline fun <reified T : Fragment> NewInstanceFragmentFactory() =
    NewInstanceFragmentFactory(T::class.java)

/** Default fragment factory. */
@PublishedApi
internal fun <T : Fragment> NewInstanceFragmentFactory(fClass: Class<T>): () -> T =
    fClass::newInstance