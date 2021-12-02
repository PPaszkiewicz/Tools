package com.github.ppaszkiewicz.tools.toolbox.delegate

import android.app.Application
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// helper typealias needed to lazily obtain a context to safely provide a lazy delegate
// in fragments etc
/** Context delegate for classes that can return a [Context]. */
typealias ContextDelegate = ReadOnlyProperty<Any, Context>

// dummy prop fed into get
private val nullProp = Unit

/** Get the context. */
fun ContextDelegate.get() = getValue(this, ::nullProp)

/** Delegate that returns context. */
val Context.contextDelegate
    get() = ContextDelegate{_, _ -> this }

/** Delegate that returns context. */
val Fragment.contextDelegate
    get() = ContextDelegate { fragment, _ -> (fragment as Fragment).requireContext() }

/** Delegate that returns context. */
val AndroidViewModel.contextDelegate
    get() = getApplication<Application>().contextDelegate

/** Delegate that returns context. */
val View.contextDelegate
    get() = context.contextDelegate