package com.github.ppaszkiewicz.tools.toolbox.transition

import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.View

/*
*   Requires SinglePropertyTransition.kt
 */

/** Transitions view elevation. */
class ElevationTransition : SinglePropertyTransition.OfFloat() {
    override fun getPropertyKey() = "ElevationTransition.Elevation"
    override fun getPropertyValue(view: View?) = view?.elevation
    override fun setPropertyValue(view: View, value: Float, animatedFraction: Float) {
        view.elevation = value
    }
}

/** Auto transition with elevation transition running together. */
class AutoTransitionWithElevation : TransitionSet {
    constructor()
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    init {
        ordering = ORDERING_TOGETHER
        addTransition(ElevationTransition())
        addTransition(AutoTransition())
    }
}