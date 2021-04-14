package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.view.View
import androidx.core.view.WindowInsetsCompat

/**
 * Compatibility for [View.setOnApplyWindowInsetsListener] that wraps received insets into compat version.
 **/
fun View.setOnApplyWindowInsetsListenerCompat(listener: OnApplyWindowInsetsListenerCompat) {
    setOnApplyWindowInsetsListener { v, insets ->
        listener.onApplyWindowInsets(v, WindowInsetsCompat.toWindowInsetsCompat(insets, v))
            .toWindowInsets()
    }
}

/** [WindowInsetsCompat.getInsets] of [WindowInsetsCompat.Type.statusBars]. */
val WindowInsetsCompat.statusBar
    get() = getInsets(WindowInsetsCompat.Type.systemBars())

/** [WindowInsetsCompat.getInsetsIgnoringVisibility] of [WindowInsetsCompat.Type.statusBars]. */
val WindowInsetsCompat.stableStatusBar
    get() = getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

/** [WindowInsetsCompat.getInsets] of [WindowInsetsCompat.Type.navigationBars]. */
val WindowInsetsCompat.navBar
    get() = getInsets(WindowInsetsCompat.Type.navigationBars())

/** [WindowInsetsCompat.getInsetsIgnoringVisibility] of [WindowInsetsCompat.Type.navigationBars]. */
val WindowInsetsCompat.stableNavBar
    get() = getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())

/** [WindowInsetsCompat.getInsets] of [WindowInsetsCompat.Type.systemBars]. */
val WindowInsetsCompat.system
    get() = getInsets(WindowInsetsCompat.Type.systemBars())

/** [WindowInsetsCompat.getInsetsIgnoringVisibility] of [WindowInsetsCompat.Type.systemBars]. */
val WindowInsetsCompat.stableSystem
    get() = getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())


fun interface OnApplyWindowInsetsListenerCompat {
    fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat
}