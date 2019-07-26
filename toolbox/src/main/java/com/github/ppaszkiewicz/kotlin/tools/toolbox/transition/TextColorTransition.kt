package com.github.ppaszkiewicz.kotlin.tools.toolbox.transition

import android.content.Context
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import com.github.ppaszkiewicz.kotlin.tools.toolbox.R
import com.github.ppaszkiewicz.kotlin.tools.toolbox.view.TextViewColorable

/*
* Requires TextViewColorable.kt (or modify the target).
* */

/**
 * Transition for text color.
 *
 * For cross activity transition must have colors provided in constructor or xml attributes.
 */
class TextColorTransition : SinglePropertyTransition.OfArgb {
    companion object {
        private const val TRANS_KEY = "TextSizeTransition:TextColor"
    }

    // colors initialized in constructor
    private val colorFrom : Int
    private val colorTo : Int

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        context.obtainStyledAttributes(attrs, R.styleable.CustomColorTransition).run {
            colorFrom = getColor(R.styleable.CustomColorTransition_colorFrom, -1)
            colorTo = getColor(R.styleable.CustomColorTransition_colorTo, -1)
            recycle()
        }
    }

    constructor(colorFrom: Int, colorTo: Int) {
        this.colorFrom = colorFrom
        this.colorTo = colorTo
    }

    // try to obtain color
    constructor(){
        colorFrom = -1
        colorTo = -1
    }

    override fun getPropertyKey() = TRANS_KEY
    // modify this method if TextViewColorable is not desired
    override fun getPropertyValue(view: View?) = (view as? TextViewColorable)?.getTextColor()

    override fun setPropertyValue(view: View, value: Int, animatedFraction: Float) {
        (view as TextViewColorable).setTextColor(value)
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        if (colorFrom == -1) {
            super.captureStartValues(transitionValues)
        } else {
            transitionValues.values[TRANS_KEY] = colorFrom
            captureTransitionName(transitionValues)
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        if (colorTo == -1) {
            super.captureEndValues(transitionValues)
        } else {
            transitionValues.values[TRANS_KEY] = colorTo
            captureTransitionName(transitionValues)
        }
    }
}