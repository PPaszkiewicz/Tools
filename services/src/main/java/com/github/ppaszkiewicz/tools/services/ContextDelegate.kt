package com.github.ppaszkiewicz.tools.services

import android.app.Application
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import kotlin.properties.ReadOnlyProperty

// copy from tools but used only internally
typealias ContextDelegate = ReadOnlyProperty<Any, Context>

/** Delegate that returns context. */
internal val Context.contextDelegate
    get() = ContextDelegate{_, _ -> this }

/** Delegate that returns context. */
internal val Fragment.contextDelegate
    get() = ContextDelegate { fragment, _ -> (fragment as Fragment).requireContext() }

/** Delegate that returns context. */
internal val AndroidViewModel.contextDelegate
    get() = getApplication<Application>().contextDelegate

/** Delegate that returns context. */
internal val View.contextDelegate
    get() = context.contextDelegate