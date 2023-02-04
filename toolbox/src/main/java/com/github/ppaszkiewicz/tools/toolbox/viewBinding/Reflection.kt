@file:Suppress("Unused")

package com.github.ppaszkiewicz.tools.toolbox.viewBinding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.github.ppaszkiewicz.tools.toolbox.R
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

// reflection extensions for view bindings
// requires viewbinding methods to be kept by proguard
// use following declaration:

//-keep,allowoptimization,allowobfuscation class * implements androidx.viewbinding.ViewBinding {
//    public static *** bind(android.view.View);
//    public static *** inflate(android.view.LayoutInflater);
//    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
//}


//*********** VIEW ****************/
/**
 * ViewBinding as tag on a view. Instantiates it with reflection.
 */
inline fun <reified T : ViewBinding> View.viewBinding() = viewBinding(T::class.java)

/**
 * ViewBinding as tag on a view. Instantiates it with reflection.
 */
fun <T : ViewBinding> View.viewBinding(bindingClass: Class<T>): T {
    return lazyTagValue(R.id.viewBinding, bindingClass.getBindMethod())
}

//*********** FRAGMENT ****************/
/**
 * Lazy delegate for ViewBinding. Uses reflection to get the static bind method.
 *
 * Example:
 * ```
 * class MyFragment : Fragment(R.layout.my_fragment) {
 *     val binding by viewBinding<MyFragmentBinding>()
 *     //...
 * }
 */
inline fun <reified T : ViewBinding> Fragment.viewBinding() = viewBinding(T::class.java)


/**
 * Lazy delegate for ViewBinding. Uses reflection to get the static bind method.
 * ```
 * Example:
 *
 * class MyFragment : Fragment(R.layout.my_fragment) {
 *     val binding by viewBinding(MyFragmentBinding::class.java)()
 *     //...
 * }
 */
fun <T : ViewBinding> Fragment.viewBinding(bindingClass: Class<T>): ReadOnlyProperty<Fragment, T> =
    viewValue(bindingClass.getBindMethod())

//*********** DIALOG FRAGMENTS ****************/

/**
 * Lazy delegate for ViewBinding that's released when dialog is dismissed. Uses reflection
 * to get the static inflate method.
 *
 * Example:
 * ```
 * class MyDialogFragment : DialogFragment() {
 *     val binding by dialogViewBinding<MyDialogFragmentBinding>()
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
inline fun <reified T : ViewBinding> DialogFragment.dialogViewBinding() =
    dialogViewBinding(T::class.java)

/**
 * Lazy delegate for ViewBinding that's released when dialog is dismissed. Uses reflection
 * to get the static inflate method.
 *
 * Example:
 * ```
 * class MyDialogFragment : DialogFragment() {
 *     val binding by dialogViewBinding(MyDialogFragmentBinding::class.java)
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
fun <T : ViewBinding> DialogFragment.dialogViewBinding(bindingClass: Class<T>) =
    dialogViewBinding(bindingClass.getInflateMethod())

//*********** ACTIVITY ****************/
/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated. Uses reflection
 * to get the static inflate method.
 *
 * Example:
 *```
 * class MyActivity : AppCompatActivity() {
 *     val binding by viewBinding<MyActivityBinding>()
 *     //...
 * }
 */
inline fun <reified T : ViewBinding> AppCompatActivity.viewBinding() = viewBinding(T::class.java)

/**
 * Delegate for ViewBinding that's lazy but with a fallback that ensures binding will be inflated. Uses reflection
 * to get the static inflate method.
 *
 * Example:
 * ```
 * class MyActivity : AppCompatActivity() {
 *     val binding by viewBinding(MyActivityBinding::class.java)
 *     //...
 * }
 */
fun <T : ViewBinding> AppCompatActivity.viewBinding(bindingClass: Class<T>): PropertyDelegateProvider<AppCompatActivity, ReadOnlyProperty<AppCompatActivity, T>> {
    return ActivityViewBindingDelegateProvider(bindingClass.getInflateMethod())
}

/**
 * Delegate for ViewBinding that binds it lazily without actually performing any inflation. Uses
 * reflection to get the static bind method.
 *
 * Example:
 * ```
 * class MyActivity : AppCompatActivity(R.layout.my_activity) {
 *     val binding by viewBindingLazy<MyActivityBinding>()
 *     //...
 * }
 * */
inline fun <reified T : ViewBinding> AppCompatActivity.viewBindingLazy(): Lazy<T> {
    return viewBindingLazy(T::class.java)
}

/**
 * Delegate for ViewBinding that binds it lazily without actually performing any inflation. Uses
 * reflection to get the static bind method.
 *
 * Example:
 * ```
 * class MyActivity : AppCompatActivity(R.layout.my_activity) {
 *     val binding by viewBindingLazy(MyActivityBinding::bind)
 *     //...
 * }
 * */
fun <T : ViewBinding> AppCompatActivity.viewBindingLazy(bindingClass: Class<T>): Lazy<T> {
    return viewBindingLazy(bindingClass.getBindMethod())
}

// helpers for nameless class search - it's expected that viewbinding will be obfuscated but not shrunk

/** Get inflate method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getInflateMethod(): (inflater: LayoutInflater) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 1 && it[0] == LayoutInflater::class.java } }
    return { inflater: LayoutInflater -> src(null, inflater) as T }
}

/** Get bind method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getBindMethod(): (root: View) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 1 && it[0] == View::class.java } }
    return { inflater: View -> src(null, inflater) as T }
}

/** Get "inflate in parent" method of this ViewBinding class. */
fun <T : ViewBinding> Class<T>.getInflateInParentMethod(): (inflater: LayoutInflater, parent: ViewGroup, attach: Boolean) -> T {
    val src =
        declaredMethods.first { method -> method.parameterTypes.let { it.size == 3 && it[0] == LayoutInflater::class.java } }
    return { inflater: LayoutInflater, parent: ViewGroup, attach: Boolean ->
        src(null, inflater, parent, attach) as T
    }
}