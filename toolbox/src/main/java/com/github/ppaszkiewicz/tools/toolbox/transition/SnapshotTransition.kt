package com.github.ppaszkiewicz.tools.toolbox.transition

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.ViewGroup

/**
 * Transition that takes a snapshot of a ViewGroup and uses it as a background during transition. This is mostly
 * used to animate "collapse effect" of a list (like a RecyclerView) since children are not included in the transition.
 *
 * When animator starts, all child views are removed. For best effect view should have non-transparent background.
 */
class SnapshotTransition : Transition {
    companion object {
        private val PROPNAME_SNAPSHOT = " SnapshotTransition:snapshot"
        private val PROPNAME_TARGETBG = " SnapshotTransition:targetbg"
    }

    private var useScale = false

    constructor()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    /**
     * When used, we target transition name and scale animation is used (instead of crop)
     *
     * @param target transition name
     */
    constructor(target: String) {
        addTarget(target)
        useScale = true
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        val bmp = Bitmap.createBitmap(transitionValues.view.width, transitionValues.view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        transitionValues.view.draw(canvas)
        transitionValues.values[PROPNAME_SNAPSHOT] = bmp
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        transitionValues.values[PROPNAME_TARGETBG] = transitionValues.view.background
    }

    /** Scale animation will be used. */
    fun useScaleAnimation(){
        useScale = true
    }

    /** Crop animation will be used. */
    fun useCropAnimation(){
        useScale = false
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        if (startValues == null || endValues == null) {
            return null
        }

        val startVal = startValues.values[PROPNAME_SNAPSHOT] as Bitmap?
        val targetBackground = endValues.values[PROPNAME_TARGETBG] as Drawable?
        if (startVal == null || startVal.isRecycled) {
            return null
        }

        val view = endValues.view
        val startView = startValues.view
        // this is a dummy animator that is used only for start/end callbacks
        val a = ValueAnimator.ofInt(0, 1)
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                val bgDrawable = BitmapDrawable(view.resources, startVal)
                if (!useScale)
                    bgDrawable.tileModeY = Shader.TileMode.CLAMP
                view.background = bgDrawable
                if (startView is ViewGroup)
                    startView.removeAllViewsInLayout()
            }

            override fun onAnimationEnd(animation: Animator) {
                view.background = targetBackground
                startVal.recycle()
            }
        })

        return a
    }

}