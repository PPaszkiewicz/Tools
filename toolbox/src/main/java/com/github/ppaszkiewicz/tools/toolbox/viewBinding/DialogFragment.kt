@file:Suppress("UNUSED", "DeprecatedCallableAddReplaceWith")

package com.github.ppaszkiewicz.tools.toolbox.viewBinding

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// ViewBinding for DialogFragments that use custom content view

// extension to keep values in dialog - not restricted to ViewBinding

/**
 * Lazy delegate for value that's released when dialog is dismissed.
 */
fun <T> DialogFragment.dialogValue(initValue: () -> T): ReadOnlyProperty<DialogFragment, T> =
    object : ViewBoundValueDelegate<T>() {
        override fun createValue(thisRef: Fragment, property: KProperty<*>): T {
            check(thisRef.isAdded) { "Cannot initialize ${property.name} when ${thisRef::class.java.simpleName} is not added to fragment manager" }
            check(!thisRef.isDetached) { "Cannot initialize ${property.name} after ${thisRef::class.java.simpleName} was detached from UI" }
            return initValue()
        }
    }

/**
 * Lazy delegate for ViewBinding that's released when dialog is dismissed.
 *
 * Example:
 * ```
 * class MyDialogFragment : DialogFragment() {
 *     val binding by dialogViewBinding { MyDialogFragmentBinding.inflate(it) }
 *     // alternatively
 *     val binding2 by dialogViewBinding(MyDialogFragmentBinding::inflate)
 *
 *     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
 *         return AlertDialog.Builder(requireContext(), theme)
 *             .setTitle("Dialog title")
 *             .setPositiveButton(android.R.string.ok) { _, _ -> }
 *             .setView(binding) {
 *                 textView1.text = "Hello World!"
 *             }.create()
 *     }
 * }
 */
fun <T : ViewBinding> DialogFragment.dialogViewBinding(createBinding: (LayoutInflater) -> T) =
    dialogValue { createBinding(layoutInflater) }

// alert dialog builder extensions

/** Set content view of this alert dialog to root of [binding]. */
fun <T : ViewBinding> AlertDialog.Builder.setView(binding: T): AlertDialog.Builder {
    return setView(binding.root)
}

/** Set content view of this alert dialog to root of [binding] and apply customization block to it. */
inline fun <T : ViewBinding> AlertDialog.Builder.setView(
    binding: T,
    initBinding: T.() -> Unit
): AlertDialog.Builder {
    return setView(binding.apply(initBinding).root)
}

// lint checks to hint that dialog fragment needs custom handling
@Deprecated(
    "DialogFragment with custom layout does not have a view so this call will fail. " +
            "Use dialogValue instead to create a value that will last until dialog is dismissed. If you're not using " +
            "custom dialog container you can suppress this warning."
)
fun <T> DialogFragment.viewValue(initValue: (View) -> T): ReadOnlyProperty<Fragment, T> =
    (this as Fragment).viewValue(initValue)

@Deprecated(
    "DialogFragment with custom layout does not have a view so this call will fail. " +
            "Use dialogViewBinding instead to inflate binding that will last until dialog is dismissed. If you're not using " +
            "custom dialog container you can suppress this warning."
)
fun <T : ViewBinding> DialogFragment.viewBinding(bindingFactory: (View) -> T): ReadOnlyProperty<Fragment, T> =
    (this as Fragment).viewBinding(bindingFactory)

// lint checks for reflection
@Deprecated(
    "DialogFragment with custom layout does not have a view so this call will fail. " +
            "Use dialogViewBinding instead to inflate binding that will last until dialog is dismissed. If you're not using " +
            "custom dialog container you can suppress this warning."
)
inline fun <reified T : ViewBinding> DialogFragment.viewBinding(): ReadOnlyProperty<Fragment, T> =
    (this as Fragment).viewBinding()

@Deprecated(
    "DialogFragment with custom layout does not have a view so this call will fail. " +
            "Use dialogViewBinding instead to inflate binding that will last until dialog is dismissed. If you're not using " +
            "custom dialog container you can suppress this warning."
)
fun <T : ViewBinding> DialogFragment.viewBinding(clazz: Class<T>): ReadOnlyProperty<Fragment, T> =
    (this as Fragment).viewBinding(clazz)