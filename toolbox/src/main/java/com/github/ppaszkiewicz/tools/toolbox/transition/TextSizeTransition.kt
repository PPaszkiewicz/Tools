package com.github.ppaszkiewicz.tools.toolbox.transition

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import com.github.ppaszkiewicz.tools.toolbox.R
import kotlin.math.max

/**
 * "Smooth" transition for text size using scale. (It might still stutter/jitter due to padding issues).
 *
 * For cross activity transition must have size provided in constructor or xml attributes.
 */
class TextSizeTransition : Transition {
    companion object {
        private const val TRANS_KEY = "TextSizeTransition:TextSize"
    }

    // sizes initialized in constructor
    private val sizeFrom: Float
    private val sizeTo: Float

    /** Pivot X used in transition. */
    var pivotX = 0f
    /** Pivot Y used in transition. */
    var pivotY = 0f

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        context.obtainStyledAttributes(attrs, R.styleable.TextSizeTransition).run {
            sizeFrom = getDimension(R.styleable.TextSizeTransition_sizeFrom, -1f)
            sizeTo = getDimension(R.styleable.TextSizeTransition_sizeTo, -1f)
            recycle()
        }
    }

    /** Provide font size in PIXELS. */
    constructor(sizeFrom: Float, sizeTo: Float) {
        this.sizeFrom = sizeFrom
        this.sizeTo = sizeTo
    }

    // try to read values off of the view
    constructor() {
        sizeFrom = -1f
        sizeTo = -1f
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        if (sizeFrom == -1f) {
            captureValues(transitionValues)
        } else {
            transitionValues.values[TRANS_KEY] = sizeFrom
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        if (sizeTo == -1f) {
            captureValues(transitionValues)
        } else {
            transitionValues.values[TRANS_KEY] = sizeTo
        }
    }

    private fun captureValues(transitionValues: TransitionValues) {
        (transitionValues.view as TextView).let {
            transitionValues.values[TRANS_KEY] = it.textSize
        }
    }

    override fun createAnimator(
        sceneRoot: ViewGroup?,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        // no values
        if (startValues == null || endValues == null)
            return null
        val sizeFrom = (startValues.values[TRANS_KEY] as? Float)?.takeUnless { it == -1f } ?: return null
        val sizeTo = (endValues.values[TRANS_KEY] as? Float)?.takeUnless { it == -1f } ?: return null
        // no resize needed
        if (sizeFrom == sizeTo)
            return null
        val view = endValues.view as TextView
        //setup font sizes
        view.pivotX = pivotX//scale view at the beginning
        view.pivotY = pivotY
        //change font to bigger one and downscale
        val biggerSize = max(sizeFrom, sizeTo)
        val scaleTo = sizeTo / biggerSize
        val scaleFrom = sizeFrom / biggerSize
        // Log.d("FONT", "createAnimator: " + scaleFrom + " _ " + scaleTo + " _ " + biggerSize);
        val scaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", scaleFrom, scaleTo),
            PropertyValuesHolder.ofFloat("scaleY", scaleFrom, scaleTo)
        )
//        scaleAnimator.addUpdateListener {
//            view.pivotY = (view.height / 2).toFloat()
//        }
        scaleAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, biggerSize)
            }
        })
        if (sizeTo != biggerSize) {
            val finalSmallSize = sizeFrom
            scaleAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, finalSmallSize)
                }
            })
        }
        return scaleAnimator
    }
}