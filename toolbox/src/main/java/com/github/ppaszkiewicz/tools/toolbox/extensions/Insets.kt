package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Compatibility for [View.setOnApplyWindowInsetsListener] that wraps received insets into compat version.
 **/
fun View.setOnApplyWindowInsetsListenerCompat(listener: OnApplyWindowInsetsListenerCompat) {
    setOnApplyWindowInsetsListener { v, insets ->
        listener.onApplyWindowInsets(v, WindowInsetsCompat.toWindowInsetsCompat(insets, v))
            .toWindowInsets()!!
    }
}

fun interface OnApplyWindowInsetsListenerCompat {
    fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat
}

/** Create [InsetsData] for [WindowInsetsCompat.Type.statusBars]. */
val WindowInsetsCompat.statusBar
    get() = get(WindowInsetsCompat.Type.systemBars())

/** Create [InsetsData] for [WindowInsetsCompat.Type.navigationBars]. */
val WindowInsetsCompat.navBar
    get() = get(WindowInsetsCompat.Type.navigationBars())

/** Create [InsetsData] for [WindowInsetsCompat.Type.systemBars]. */
val WindowInsetsCompat.system
    get() = getInsets(WindowInsetsCompat.Type.systemBars())

/** Create [InsetsData] for [WindowInsetsCompat.Type.ime]. */
val WindowInsetsCompat.ime
    get() = getInsets(WindowInsetsCompat.Type.ime())

/** Utility proxy that holds [typeMask] to perform argument-less gets. */
class InsetsData(val src: WindowInsetsCompat, val typeMask: Int) {
    val left
        get() = src.getInsets(typeMask).left
    val top
        get() = src.getInsets(typeMask).top
    val right
        get() = src.getInsets(typeMask).right
    val bottom
        get() = src.getInsets(typeMask).bottom

    /** Calls [WindowInsetsCompat.getInsets]. */
    val current
        get() = src.getInsets(typeMask)
    /** Calls [WindowInsetsCompat.getInsetsIgnoringVisibility]. */
    val stable
        get() = src.getInsetsIgnoringVisibility(typeMask)
    /** Calls [WindowInsetsCompat.isVisible]. */
    val isVisible
        get() = src.isVisible(typeMask)

    /** Hide this inset by using [WindowInsetsControllerCompat] retrieved from [view]. */
    fun hide(view: View) = hide(ViewCompat.getWindowInsetsController(view))

    /** Hide this inset using [insetsControllerCompat]. */
    fun hide(insetsControllerCompat: WindowInsetsControllerCompat?) {
        insetsControllerCompat?.hide(typeMask)
    }
}

operator fun WindowInsetsCompat.get(typeMask: Int) = InsetsData(this, typeMask)