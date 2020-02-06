package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
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