package com.github.ppaszkiewicz.tools.toolbox.extensions

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/**
 * Find fragment that identifies itself by its class name as tag.
 */
inline fun <reified F : Fragment> FragmentManager.findFragmentByClass() =
    findFragmentByClass(F::class.java)

/**
 * Find fragment that identifies itself by its class name as tag.
 */
fun <F : Fragment> FragmentManager.findFragmentByClass(clazz: Class<F>) =
    findFragmentByTag(clazz.name) as F?

/**
 * Add fragment into container using its class name as a tag.
 * */
fun FragmentTransaction.addWithClassTag(@IdRes containerViewId: Int, fragment: Fragment) =
    add(containerViewId, fragment, fragment::class.java.name)

/**
 * Load [fragment] into view with [containerId]. This will detach current fragment and reattach or add new one.
 *
 * If [fragment] is already attached or null this does nothing.
 *
 * This runs a fragment transaction internally, and unlike [FragmentTransaction.replace] this does not
 * destroy previous fragment (only its view hierarchy).
 *
 * Uses [fragment] class name as tag, so it must be unique in this fragment manager scope, otherwise
 * [IllegalStateException] will be thrown.
 * */
fun FragmentManager.swap(@IdRes containerId: Int, fragment: Fragment?, commitNow: Boolean = false) =
    if (fragment != null) swapImpl(
        fragment,
        containerId,
        fragment::class.java.name,
        true,
        commitNow
    ) else false

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
fun FragmentManager.swap(
    @IdRes containerId: Int,
    fragment: Fragment?,
    tag: String,
    commitNow: Boolean = false
) = swapImpl(fragment, containerId, tag, false, commitNow)

/**
 * Load [newFragment] into view with [containerId].
 *
 * This will detach [oldFragment] and attach [newFragment] (if it's detached) or add it using [tag].
 *
 * *This is exposed inner transaction of [FragmentManager.swap] and does not perform any error checks.*
 * */
fun FragmentTransaction.swap(
    oldFragment: Fragment?,
    newFragment: Fragment,
    containerId: Int,
    tag: String
) {
    if (oldFragment != null) {
        // detach current fragment - only destroys its view hierarchy
        detach(oldFragment)
    }
    if (newFragment.isDetached) {
        // re-attach previously detached fragment, recreating view hierarchy
        attach(newFragment)
    } else {
        // add new fragment triggering its onCreate etc
        add(containerId, newFragment, tag)
    }
}

// implementation for swap
private fun FragmentManager.swapImpl(
    fragment: Fragment?,
    containerId: Int,
    tag: String,
    checkClassCollision: Boolean,
    commitNow: Boolean
): Boolean {
    return if (fragment != null) {
        val currentFragment = findFragmentById(containerId)
        // fragment tries to replace itself, ignore
        if (fragment == currentFragment) return false
        if (checkClassCollision && fragment.javaClass == currentFragment?.javaClass)
            throw IllegalStateException("swap(Fragment?, Int): duplicate fragment class encountered: ${fragment.javaClass.name}.")

        beginTransaction().apply {
            swap(currentFragment, fragment, containerId, tag)
            if(commitNow) commitNow() else commit()
        }
        true
    } else {
        // no fragment provided, ignore and return false
        false
    }
}