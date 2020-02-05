package com.github.ppaszkiewicz.tools.toolbox.extensions.fragmentManager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Find fragment that identifies itself by its class name as tag.
 */
inline fun <reified F : Fragment> FragmentManager.findFragmentByClass() =
    findFragmentByClass(F::class.java)

/**
 * Find fragment that identifies itself by it's class name as tag.
 */
fun <F : Fragment> FragmentManager.findFragmentByClass(clazz: Class<F>) =
    findFragmentByTag(clazz.name) as F?

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
fun FragmentManager.swap(fragment: Fragment?, containerId: Int) =
    if (fragment != null) swapImpl(fragment, containerId, fragment.javaClass.name, true) else false

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
fun FragmentManager.swap(fragment: Fragment?, containerId: Int, tag: String) =
    swapImpl(fragment, containerId, tag, false)

// implementation for swap
private fun FragmentManager.swapImpl(
    fragment: Fragment?,
    containerId: Int,
    tag: String,
    checkClassCollision: Boolean
): Boolean {
    return if (fragment != null) {
        val currentFragment = findFragmentById(containerId)
        // fragment tries to replace itself, ignore
        if (fragment == currentFragment) return false
        if (checkClassCollision && fragment.javaClass == currentFragment?.javaClass)
            throw IllegalStateException("swap(Fragment?, Int): duplicate fragment class encountered: ${fragment.javaClass.name}.")

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