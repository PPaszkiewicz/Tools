package com.github.ppaszkiewicz.tools.toolbox.fragment

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment

/**
 * Fragment that can use different theme than default.
 *
 * Set [themeId] before fragment is attached to setup the theme.
 */
open class ThemedFragment : Fragment {
    constructor() : super()
    constructor(@LayoutRes contentLayoutId : Int) : super(contentLayoutId)

    protected var themedContext : Context? = null

    /**
     * Fragments theme; changing this value while fragment is attached has no effect.
     *
     * This id is stored in saved instance stace.
     * */
    @StyleRes
    var themeId :  Int = 0
        set(value) {
            field = value
            themedContext = null
        }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        return if(themeId != 0) {
            if(themedContext == null)
                themedContext = ContextThemeWrapper(super.getContext(), themeId)
            LayoutInflater.from(themedContext)
        } else super.onGetLayoutInflater(savedInstanceState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeId = savedInstanceState?.getInt("themeId", 0) ?: 0
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("themeId", themeId)
    }

    override fun onDetach() {
        super.onDetach()
        themedContext = null
    }
}