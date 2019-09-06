package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlin.reflect.KProperty


/** Reflect static TAG value of this class. */
val Fragment.TAG
    get() = javaClass.getDeclaredField("TAG").get(null) as String

/** Reflect static TAG value of this class. */
val Class<*>.TAG
    get() = getDeclaredField("TAG").get(null) as String

/**
 * Get fragment of given type. It must contain static TAG field.
 *
 * If [allowCreate] is false this will only check fragment manager for existing one, if it's
 * true new instance of that fragment is created using default (empty) constructor.
 * */
inline fun <reified F : Fragment> FragmentManager.getFragment(allowCreate: Boolean): F? {
    val tag = F::class.java.TAG
    (findFragmentByTag(tag))?.let {
        Log.d("FragmentManager", "getFragment: found ${F::class.java.name} : tag = $tag, isAdded=${it.isAdded}, isDetached=${it.isDetached}")
        return it as F
    }
    Log.d("FragmentManager", "getFragment: creating ${F::class.java.name} : tag = $tag")
    if (!allowCreate) return null
    return F::class.java.newInstance()
}

/**
 * Obtain existing fragment of given type from this fragmentManager or return new fragment instance.
 * Fragments classes MUST have reflectable static TAG field and empty constructor.
 *
 * Inside fragments use [fragmentManager] or [childFragmentManager] delegates instead.
 * */
inline operator fun <reified F : Fragment> FragmentManager.getValue(
    parent: Any,
    prop: KProperty<*>
): F {
    return getFragment(true)!!
}

/** Helper for declaring delegates from [fragmentManager]. */
fun Fragment.fragmentManager() = LazyFragmentManager(this)

/** Helper for delegate for [fragmentManager]. */
class LazyFragmentManager(val host: Fragment){
    inline operator fun <reified F : Fragment> getValue(
        parent: Any,
        prop: KProperty<*>
    ): F {
        return host.fragmentManager!!.getFragment(true)!!
    }
}

/** Helper for declaring delegates from [childFragmentManager]. */
fun Fragment.childFragmentManager() = LazyChildFragmentManager(this)

/** Helper for delegate for [childFragmentManager]. */
class LazyChildFragmentManager(val host: Fragment){
    inline operator fun <reified F : Fragment> getValue(
        parent: Any,
        prop: KProperty<*>
    ): F {
        return host.childFragmentManager.getFragment(true)!!
    }
}