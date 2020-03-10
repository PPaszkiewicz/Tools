package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Build and [Fragment.setArguments] of this fragment. Use only when constructing
 * fragment from within new instance.
 */
inline fun <T: Fragment>T.withArguments(argBuilder : Bundle.() -> Unit) = apply {
    arguments = Bundle().apply(argBuilder)
}

