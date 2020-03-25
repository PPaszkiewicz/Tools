package com.github.ppaszkiewicz.tools.toolbox.extensions

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Build and [Fragment.setArguments] of this fragment. Use only when constructing
 * fragment from within new instance.
 */
inline fun <T: Fragment>T.withArguments(argBuilder : Bundle.() -> Unit) = apply {
    arguments = Bundle().apply(argBuilder)
}