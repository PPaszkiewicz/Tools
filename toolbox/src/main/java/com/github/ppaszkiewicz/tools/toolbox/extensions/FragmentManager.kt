package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager


/** Reflect static TAG value of this class. */
val Fragment.classTAG
    get() = javaClass.getDeclaredField("TAG").get(null) as String

/** Reflect static TAG value of this class. */
val Class<*>.classTAG
    get() = getDeclaredField("TAG").get(null) as String

/**
 * Get fragment of given type. It must contain static TAG field.
 *
 * If [allowCreate] is false this will only check fragment manager for existing one, if it's
 * true new instance of that fragment is created using default (empty) constructor.
 * */
inline fun <reified F : Fragment> FragmentManager.getFragment(allowCreate: Boolean): F? {
    val tag = F::class.java.classTAG
    (findFragmentByTag(tag))?.let {
        Log.d(
            "FragmentManager",
            "getFragment: found ${F::class.java.name} : tag = $tag, isAdded=${it.isAdded}, isDetached=${it.isDetached}"
        )
        return it as F
    }
    Log.d("FragmentManager", "getFragment: creating ${F::class.java.name} : tag = $tag")
    if (!allowCreate) return null
    return F::class.java.newInstance()
}

/**
 * Load [fragment] into view with [containerId]. This will detach current fragment and reattach or add new one.
 *
 * If [fragment] is already attached or null this does nothing.
 *
 * This runs a fragment transaction internally, and unlike [FragmentTransaction.replace] this does not
 * destroy previous fragment (only its view hierarchy).
 *
 * [fragment] must have unique static reflectable TAG field.
 * */
fun FragmentManager.swapByStaticTag(fragment: Fragment?, containerId: Int) =
    if (fragment != null) swap(fragment, containerId, fragment.classTAG) else false

/**
 * Load [fragment] into view with [containerId]. This will detach current fragment and reattach or add new one.
 *
 * If [fragment] is already attached or null this does nothing.
 *
 * This runs a fragment transaction internally, and unlike [FragmentTransaction.replace] this does not
 * destroy previous fragment (only its view hierarchy).
 *
 * If fragment is added for the first time then [tag] is used in transaction.
 * */
fun FragmentManager.swap(fragment: Fragment?, containerId: Int, tag: String): Boolean {
    return if (fragment != null) {
        val currentFragment = findFragmentById(containerId)
        // fragment tries to replace itself, ignore
        if (fragment == currentFragment) return false

        beginTransaction().apply {
            if (currentFragment != null) {
                // detach current fragment - only destroys its view hierarchy
                detach(currentFragment)
            }
            if (fragment.isDetached) {
                // re-attach previously detached fragment, recreating view hierarchy
                attach(fragment)
            } else {
                // add new fragment triggering its onCreate etc
                add(containerId, fragment, tag)
            }
            commit()
        }
        true
    } else {
        // no fragment provided, ignore and return false
        false
    }
}