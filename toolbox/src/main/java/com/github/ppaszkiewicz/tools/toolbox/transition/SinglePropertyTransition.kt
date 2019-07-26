package com.github.ppaszkiewicz.tools.toolbox.transition


import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Simple wrapper for default transition of single view property.
 *
 * Can be used instead of ObjectAnimator to prevent issues with minify and obfuscation.
 * */
sealed class SinglePropertyTransition<T : Any> : Transition {
    constructor()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var startValue : T? = null
    var endValue : T? = null

    /** Property key used for animated object in transitionValues. Must be unique. */
    abstract fun getPropertyKey(): String

    /** Obtain property from the [view]. Returning null will prevent transition. */
    abstract fun getPropertyValue(view: View?): T?

    /** Set property of [view] to [value]. This is called during animation. */
    abstract fun setPropertyValue(view: View, value: T, animatedFraction: Float)

    override fun captureStartValues(transitionValues: TransitionValues) = captureValues(transitionValues)
    override fun captureEndValues(transitionValues: TransitionValues) = captureValues(transitionValues)

    /**
     * Validates that both source and target view have the same transition name.
     *
     * This is a fallback to disable transition in some cases where view is included as transition target
     * (for example due to class target filtering) but it should not actually perform this transition unless
     * transition names match.
     *
     * default value: **false**
     **/
    open fun isTransitionNameValidated() : Boolean = false

    // capture value regardless of it being start or end
    private fun captureValues(transitionValues: TransitionValues) {
        transitionValues.values[getPropertyKey()] = getPropertyValue(transitionValues.view)
        captureTransitionName(transitionValues)
    }

    /** Capture transition names if [isTransitionNameValidated]. Called internally during [getPropertyValue]. */
    fun captureTransitionName(transitionValues: TransitionValues){
        if(isTransitionNameValidated()){
            transitionValues.values[PROP_TRANSITION_NAME] = transitionValues.view?.transitionName
        }
    }

    @Suppress("UNCHECKED_CAST")
    final override fun createAnimator(
        sceneRoot: ViewGroup?,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        if (startValues == null || endValues == null) return null
        val startValue = startValues.values[getPropertyKey()] as T?
        val endValue = endValues.values[getPropertyKey()] as T?
        // drop transition in case of missing transition values
        if (startValue == null || endValue == null || startValue == endValue)
            return null
        // drop transition in transition name validation case
        if(isTransitionNameValidated()){
            startValues.values[PROP_TRANSITION_NAME]?.let {
                if(it != endValues.values[PROP_TRANSITION_NAME]) return null
            }
        }
        return createAnimator2(startValue, endValue, startValues.view)
    }

    /**
     * [createAnimator] is a final override that validates properties.
     *
     * This method creates the animator instead (always non-null).
     * */
    abstract fun createAnimator2(startValue: T, endValue: T, view: View): Animator

    /** Lambda factory creating abstract class implementations. */
    companion object {
        // key for stored transition name
        private const val PROP_TRANSITION_NAME = "PROP_TRANSITION_NAME_VALIDATION"

        fun OfFloat(
            propertyKey: String,
            getProp: (View?) -> Float,
            setProp: (view: View, value: Float, animatedFraction: Float) -> Unit
        ) = object : OfFloat(){
            override fun getPropertyKey() = propertyKey
            override fun getPropertyValue(view: View?) = getProp(view)
            override fun setPropertyValue(view: View, value: Float, animatedFraction: Float) =
                setProp(view, value, animatedFraction)
        }

        fun OfInt(
            propertyKey: String,
            getProp: (View?) -> Int,
            setProp: (view: View, value: Int, animatedFraction: Float) -> Unit
        ) = object : OfInt(){
            override fun getPropertyKey() = propertyKey
            override fun getPropertyValue(view: View?) = getProp(view)
            override fun setPropertyValue(view: View, value: Int, animatedFraction: Float) =
                setProp(view, value, animatedFraction)
        }

        fun OfArgb(
            propertyKey: String,
            getProp: (View?) -> Int,
            setProp: (view: View, value: Int, animatedFraction: Float) -> Unit
        ) = object : OfArgb(){
            override fun getPropertyKey() = propertyKey
            override fun getPropertyValue(view: View?) = getProp(view)
            override fun setPropertyValue(view: View, value: Int, animatedFraction: Float) =
                setProp(view, value, animatedFraction)
        }
    }

    /** Implements default [createAnimator2]. */
    abstract class OfFloat : SinglePropertyTransition<Float> {
        constructor(): super()
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        override fun createAnimator2(startValue: Float, endValue: Float, view: View): Animator =
            ValueAnimator.ofFloat(startValue, endValue).apply {
                addUpdateListener {
                    setPropertyValue(view, animatedValue as Float, animatedFraction)
                }
            }
    }

    /** Implements default [createAnimator2]. */
    abstract class OfInt : SinglePropertyTransition<Int> {
        constructor(): super()
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        override fun createAnimator2(startValue: Int, endValue: Int, view: View): Animator =
            ValueAnimator.ofInt(startValue, endValue).apply {
                addUpdateListener {
                    setPropertyValue(view, animatedValue as Int, animatedFraction)
                }
            }
    }

    /** Implements default [createAnimator2]. */
    abstract class OfArgb : SinglePropertyTransition<Int> {
        constructor() : super()
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        override fun createAnimator2(startValue: Int, endValue: Int, view: View): Animator =
            ValueAnimator.ofArgb(startValue, endValue).apply {
                addUpdateListener {
                    setPropertyValue(view, animatedValue as Int, animatedFraction)
                }
            }
    }
}