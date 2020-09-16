package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import android.view.ViewGroup
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.absoluteValue

/** ViewGroup that can take ongoing touch event from its children on command. */
interface TouchInterruptParent {
    companion object {
        /**
         * Find suitable instance of [TouchInterruptParent] by crawling up in view hierarchy.
         * @throws IllegalArgumentException if there's no valid parent
         * */
        tailrec fun findFrom(parent: ViewParent): TouchInterruptParent {
            return when (parent) {
                is TouchInterruptParent -> parent
                else -> findFrom(
                    parent.parent
                        ?: throw IllegalArgumentException("TouchInterruptParent not found in view hierarchy")
                )
            }
        }

        /**
         * Find suitable instance of [TouchInterruptParent] by crawling up in view hierarchy.
         * @throws IllegalArgumentException if there's no valid parent
         * */
        fun findFrom(view: View) = findFrom(view.parent)
    }

    /**
     * Force ongoing touch [event] to be cancelled and forcefully taken by this ViewGroup. If [beginDrag]
     * then DOWN event is mocked to begin drag motion in itself.
     *
     * If [event] is `null` then last touch on this view hierarchy is interrupted. It's recommended to
     * disable [ViewGroup.isMotionEventSplittingEnabled] on this ViewGroup before doing so.
     * */
    fun interruptOngoingTouchEvent(event: MotionEvent?, beginDrag: Boolean)

    /** Helper with default implementation. */
    class Helper(val view: View) : TouchInterruptParent {
        private var lastTouchEvent: MotionEvent? = null
        private var interruptTouch: Boolean = false

        override fun interruptOngoingTouchEvent(event: MotionEvent?, beginDrag: Boolean) {
            interruptTouch = true
            (event ?: lastTouchEvent)?.let {
                // dispatch mocked up event to send cancel to currently touched views
                it.action = MotionEvent.ACTION_UP
                view.dispatchTouchEvent(it)
                if (beginDrag) {
                    // now self consume mocked DOWN event to begin self drag
                    it.action = MotionEvent.ACTION_DOWN
                    view.onTouchEvent(it)
                }
            }
        }

        /** If this returns false proceed to call super.onInterceptTouchEvent. */
        fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (interruptTouch) {
                interruptTouch = false
                lastTouchEvent = null
                return true
            }
            lastTouchEvent = ev
            return false
        }
    }
}

/** [MotionLayout] implementing [TouchInterruptParent]. */
class MotionLayoutTouchInterrupt @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MotionLayout(context, attrs, defStyleAttr), TouchInterruptParent {
    private val mInterruptHelper = TouchInterruptParent.Helper(this)

    init {
        // isMotionEventSplittingEnabled = false
    }

    override fun interruptOngoingTouchEvent(event: MotionEvent?, beginDrag: Boolean) {
        mInterruptHelper.interruptOngoingTouchEvent(event, beginDrag)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return mInterruptHelper.onInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)
    }
}

/** [ConstraintLayout] implementing [TouchInterruptParent]. */
class ConstraintLayoutTouchInterrupt @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), TouchInterruptParent {
    private val mInterruptHelper = TouchInterruptParent.Helper(this)

    override fun interruptOngoingTouchEvent(event: MotionEvent?, beginDrag: Boolean) {
        mInterruptHelper.interruptOngoingTouchEvent(event, beginDrag)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return mInterruptHelper.onInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)
    }
}


/** Listener for children of [TouchInterruptParent] that want to discard their touch event after being dragged. */
class OnSlopeDragInterrupt(
    /** View to force interrupt in. */
    val interruptParent: TouchInterruptParent,
    /** In which direction drag is detected. */
    val orientation: Orientation = Orientation.BOTH,
    /** Touch slope after which interrupt triggers. */
    var slope: Int = -1,
    /** Called during DOWN event to see if it should be taken. If null it always is. */
    var isActive: (() -> Boolean)? = null
) : View.OnTouchListener {
    enum class Orientation {
        VERTICAL, HORIZONTAL, BOTH
    }

    private var startX: Float? = null
    private var startY: Float? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isActive?.invoke() == false) return false
                startX = event.x
                startY = event.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (checkSlope(view, event)) {
                    interruptParent.interruptOngoingTouchEvent(event, true)
                    true
                } else true
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

    private fun clearEvent() {
        startX = null
        startY = null
    }

    private fun getSlope(view: View): Int {
        if (slope == -1) slope = ViewConfiguration.get(view.context).scaledTouchSlop
        return slope
    }
}