package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment

// context starters
/**
 * Start activity of given class without any specific action or extras.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Context.startActivity(options: ActivityOptions? = null) =
    Intent(this, T::class.java).also { startActivity(it, options?.toBundle()) }


/**
 * Start activity of given class.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Context.startActivity(
    options: ActivityOptions? = null,
    editStartIntent: Intent.() -> Unit
) = Intent(this, T::class.java).also {
    it.editStartIntent()
    startActivity(it, options?.toBundle())
}

// activity starters
/**
 * Start activity of given class for result.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Activity.startActivityForResult(
    requestCode: Int,
    options: ActivityOptions? = null,
    editStartIntent: Intent.() -> Unit
) = Intent(this, T::class.java).also {
    it.editStartIntent()
    startActivityForResult(it, requestCode, options?.toBundle())
}

/**
 * Start activity of given class without any specific action or extras, for result.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Activity.startActivityForResult(
    requestCode: Int,
    options: ActivityOptions? = null
) = Intent(this, T::class.java).also {
    startActivityForResult(it, requestCode, options?.toBundle())
}

// fragment starters
/**
 * Start activity of given class without any specific action or extras.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Fragment.startActivity(options: ActivityOptions? = null) =
    Intent(requireContext(), T::class.java).also { startActivity(it, options?.toBundle()) }

/**
 * Start activity of given class.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Fragment.startActivity(
    options: ActivityOptions? = null,
    editStartIntent: Intent.() -> Unit
) = Intent(requireContext(), T::class.java).also {
    it.editStartIntent()
    startActivity(it, options?.toBundle())
}

/**
 * Start activity of given class without any specific action or extras for result to return into this fragment.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Fragment.startActivityForResult(
    requestCode: Int,
    options: ActivityOptions? = null
) = Intent(requireContext(), T::class.java).also {
    startActivityForResult(it, requestCode, options?.toBundle())
}

/**
 * Start activity of given class for result to return into this fragment.
 * @return intent that started the activity
 * */
inline fun <reified T : Activity> Fragment.startActivityForResult(
    requestCode: Int,
    options: ActivityOptions? = null,
    editStartIntent: Intent.() -> Unit
) = Intent(requireContext(), T::class.java).also {
    it.editStartIntent()
    startActivityForResult(it, requestCode, options?.toBundle())
}

// helpers for new result API
/** Infer class for [StartLocalActivityForResult]. */
inline fun <reified T : Activity> Activity.StartActivityForResult(
    action: String? = null,
    noinline editStartIntent: (Intent.() -> Unit)? = null)  =
    StartLocalActivityForResult(T::class.java, action, editStartIntent)

/** Infer class for [StartLocalActivityForResult]. */
inline fun <reified T : Activity> Fragment.StartActivityForResult(
    action: String? = null,
    noinline editStartIntent: (Intent.() -> Unit)? = null)  =
    StartLocalActivityForResult(T::class.java, action, editStartIntent)

/**
 * Contract to open an activity from this package (using class name).
 *
 * This consumes input bundle by merging it with intent extras.
 *
 * @param targetClass activity class
 * @param action action to include in start intent (default: null)
 * @param editStartIntent optional lambda to manipulate constructed intent
 */
class StartLocalActivityForResult(
    val targetClass: Class<out Activity>,
    val action: String? = null,
    val editStartIntent: (Intent.() -> Unit)? = null
) : ActivityResultContract<Bundle, ActivityResult>() {
    override fun createIntent(context: Context, input: Bundle?) =
        Intent(context, targetClass).also {
            input?.let { b -> it.putExtras(b) }
            it.action = action
            editStartIntent?.invoke(it)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult {
        return ActivityResult(resultCode, intent)
    }
}