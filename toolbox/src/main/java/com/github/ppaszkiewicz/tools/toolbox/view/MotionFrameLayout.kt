package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.NestedScrollingParent3

/** Frame layout that serves as a proxy between two motion layouts (ie. fragment container). */
class MotionFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    /** Modify progress of child motion layout. */
    var progress: Float
        @Keep get() = motionLayout?.progress ?: 0f
        @Keep set(value) {
            motionLayout?.progress = value
            //Log.d("MF", "motion is $v")
        }

    /** Child motion layout. */
    val motionLayout: MotionLayout?
        get() = getChildAt(0) as? MotionLayout

    private val nestedScrollParent : NestedScrollingParent3
        get() = parent as NestedScrollingParent3

    // delegate scrolls to scrolling parent

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return nestedScrollParent.onStartNestedScroll(child, target, axes, type)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        return nestedScrollParent.onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        return nestedScrollParent.onStopNestedScroll(target, type)
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int, consumed: IntArray) {
        return nestedScrollParent.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        return nestedScrollParent.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        return nestedScrollParent.onNestedPreScroll(target, dx, dy, consumed, type)
    }
}