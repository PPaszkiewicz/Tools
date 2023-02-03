package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle

/**
 * Build and [Fragment.setArguments] of this fragment. Use only when constructing
 * fragment from within new instance.
 */
inline fun <T : Fragment> T.withArguments(argBuilder: Bundle.() -> Unit) = apply {
    check(lifecycle.currentState == Lifecycle.State.INITIALIZED)
    arguments = Bundle().apply(argBuilder)
}

/**
 * Show this dialog fragment using its class name as a tag.
 * */
fun DialogFragment.show(fragmentManager: FragmentManager) {
    show(fragmentManager, this::class.java.name)
}

/**
 * Show this dialog fragment immediately using its class name as a tag.
 * */
fun DialogFragment.showNow(fragmentManager: FragmentManager) {
    showNow(fragmentManager, this::class.java.name)
}

/**
 * Show this dialog fragment using its class name as a tag while ensuring only one instance
 * of it exists.
 * */
fun DialogFragment.showSingle(fragmentManager: FragmentManager, now: Boolean = false) {
    fragmentManager.executePendingTransactions()
    if (fragmentManager.findFragmentByClass(this::class.java) == null) {
        if (now) showNow(fragmentManager)
        else show(fragmentManager)
    }
}