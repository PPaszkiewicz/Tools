package com.github.ppaszkiewicz.tools.toolbox.view

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.absoluteValue

/** Simple touch listener that detects drag above touch slope. */
open class OnTouchSlopeDetector(
    /** In which direction drag is detected. */
    val orientation: Orientation = Orientation.BOTH,
    /** Touch slope after which [onSlopeDetected] triggers. */
    var slope: Int = SLOPE_DEFAULT,
    /** Optional filter for touch down event. */
    val acceptTouchDown: ((view: View, event: MotionEvent) -> Boolean)? = null,
    /** Triggered when drag above slope is detected. */
    val onSlopeDetected: (view: View, startX: Float, startY: Float, event: MotionEvent) -> Unit
) : View.OnTouchListener {
    enum class Orientation {
        VERTICAL, HORIZONTAL, BOTH
    }

    companion object {
        const val SLOPE_DEFAULT = -1
    }

    private var startX: Float? = null
    private var startY: Float? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isInTouch() || acceptTouchDown?.invoke(view, event) == false) return false
                startX = event.x
                startY = event.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isInTouch()) return false
                if (checkSlope(view, event)) {
                    onSlopeDetected(view, startX!!, startY!!, event)
                }
                true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                clearEvent()
                false
            }
            else -> false
        }
    }

    private fun checkSlope(view: View, event: MotionEvent): Boolean {
        if (orientation == Orientation.VERTICAL || orientation == Orientation.BOTH) {
            if ((event.y - startY!!).absoluteValue > getSlope(view)) return true
        }
        if ((orientation == Orientation.HORIZONTAL || orientation == Orientation.BOTH)) {
            if ((event.x - startX!!).absoluteValue > getSlope(view)) return true
        }
        return false
    }


    private fun getSlope(view: View): Int {
        if (slope == -1) slope = ViewConfiguration.get(view.context).scaledTouchSlop
        return slope
    }

    private fun clearEvent() {
        startX = null
        startY = null
    }

    /** See if there's an active touch event being monitored. */
    fun isInTouch() = startX != null
}