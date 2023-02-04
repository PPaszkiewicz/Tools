@file:Suppress("Unused")

package com.github.ppaszkiewicz.tools.viewBinding

import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Note that View extensions require declaration of R.id.viewBinding in resources

/**
 * Viewbinding as tag on a view. Instantiates it with [bindingFactory].
 */
fun <T : ViewBinding> View.viewBinding(bindingFactory: (View) -> T): T {
    return lazyTagValue(R.id.viewBinding, bindingFactory)
}

// general extensions for view tag values
/**
 * [View.getTag] that will throw if tag is set but not of type [T].
 * */
fun <T> View.getTagValue(): T? = tag?.let { it as T }

/**
 * [View.getTag] that will throw if tag for [key] is set but not of type [T].
 * */
fun <T> View.getTagValue(key: Int): T? = getTag(key)?.let { it as T }

/**
 * [View.setTag] that returns [value].
 * */
fun <T> View.setTagValue(value: T): T = value.also { tag = it }

/**
 * [View.setTag] that returns [value].
 * */
fun <T> View.setTagValue(key: Int, value: T): T = value.also { setTag(key, it) }

/**
 * Lazy value stored within views tag. Returns it or uses [valueInit] to set it.
 */
inline fun <T> View.lazyTagValue(key: Int, valueInit: (View) -> T): T {
    return getTagValue(key) ?: setTagValue(key, valueInit(this))
}

/**
 * Lazy delegate for value that's stored as root views tag so it gets cleared alongside it without
 * establishing any lifecycle listeners.
 *
 * @param key unique resource ID to use when tagging
 * @param initValue use provided view to create the value
 */
@Suppress("Unused")
fun <T> Fragment.viewTagValue(key: Int, initValue: (View) -> T): ReadOnlyProperty<Fragment, T> =
    ViewTagValueDelegate(key, initValue)

/** Delegate that keeps its value as a tag on fragments view, so it's cleared without
 * using any lifecycle listener. */
private class ViewTagValueDelegate<T>(val key: Int, private val valueFactory: (View) -> T) :
    ReadOnlyProperty<Fragment, T> {
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T =
        thisRef.requireView().lazyTagValue(key, valueFactory)
}