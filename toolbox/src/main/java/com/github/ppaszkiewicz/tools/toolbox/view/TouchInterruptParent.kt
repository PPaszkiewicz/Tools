package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.graphics.PointF
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

    /** Action constants for [interruptOngoingTouchEvent]. */
    object Action {
        /** Don't perform extra actions, only use default behavior of [ViewGroup.onInterceptTouchEvent]. */
        const val NONE = 0

        /** Dispatch UP event to children. */
        const val DISPATCH_UP = 1

        /** Mock this event as DOWN and send it to own [View.onTouchEvent] to begin dragging. */
        const val BEGIN_DRAG = 2

        /** Option for [BEGIN_DRAG]: mocked DOWN event will happen where last DOWN event was recorded. */
        const val DRAG_AT_DOWN = 4

        /** [BEGIN_DRAG] with [DRAG_AT_DOWN]. */
        const val BEGIN_DRAG_AT_DOWN = BEGIN_DRAG + DRAG_AT_DOWN

        /** Perform both [BEGIN_DRAG] and [DISPATCH_UP]. */
        const val DISPATCH_UP_AND_BEGIN_DRAG = BEGIN_DRAG + DISPATCH_UP

        /** Use all available actions: [BEGIN_DRAG_AT_DOWN] and [DISPATCH_UP]. */
        const val ALL = BEGIN_DRAG_AT_DOWN + DISPATCH_UP
    }

    /**
     * Force ongoing touch [event] to be cancelled and forcefully taken by this ViewGroup.
     *
     * If [event] is `null` then last touch on this view hierarchy is interrupted. It's recommended to
     * disable [ViewGroup.isMotionEventSplittingEnabled] on this ViewGroup before doing so.
     *
     * @param actions one of [Action] constants.
     * */
    fun interruptOngoingTouchEvent(event: MotionEvent?, actions: Int)

    /** [interruptOngoingTouchEvent] is performing interrupt and sending mocked touch events now. */
    fun isInterruptingTouchEventNow(): Boolean

    /** Helper with default implementation. */
    class Helper(val view: View) : TouchInterruptParent {
        private var lastTouchEvent: MotionEvent? = null
        private var interruptTouch: Boolean = false
        private var isInterrupting = false

        private val lastDownEventLocation = PointF(-1f, -1f)

        override fun isInterruptingTouchEventNow() = isInterrupting

        override fun interruptOngoingTouchEvent(event: MotionEvent?, actions: Int) {
            interruptTouch = true
            (event ?: lastTouchEvent)?.let {
                isInterrupting = true
                val a = it.action
                // dispatch mocked up event to send cancel to currently touched views
                if (actions and Action.DISPATCH_UP == Action.DISPATCH_UP) {
                    it.action = MotionEvent.ACTION_UP
                    view.dispatchTouchEvent(it)
                }
                // now self consume mocked DOWN event to begin self drag
                if (actions and Action.BEGIN_DRAG == Action.BEGIN_DRAG) {
                    mockDownEvent(it, actions and Action.DRAG_AT_DOWN == Action.DRAG_AT_DOWN)
                }
                it.action = a
                isInterrupting = false
            }
        }

        private fun mockDownEvent(event: MotionEvent, dragAtDown: Boolean) {
            event.action = MotionEvent.ACTION_DOWN
            if (!dragAtDown || lastDownEventLocation.x < 0f) { // no known target to relocate down event
                view.onTouchEvent(event)
            } else {
                val srcX = event.x
                val srcY = event.y
                event.setLocation(lastDownEventLocation.x, lastDownEventLocation.y)
                view.onTouchEvent(event)
                event.setLocation(srcX, srcY)
                // consumed
                lastDownEventLocation.x = -1f
                lastDownEventLocation.y = -1f
            }
        }

        /** If this returns false proceed to call super.onInterceptTouchEvent. */
        fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                lastDownEventLocation.x = ev.x
                lastDownEventLocation.y = ev.y
            }
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

/** Extension that constructs action. */
fun TouchInterruptParent.interruptOngoingTouchEvent(
    event: MotionEvent? = null, beginDrag: Boolean = true, dispatchUp: Boolean = true, dragAtDown: Boolean = true
) {
    with(TouchInterruptParent.Action) {
        interruptOngoingTouchEvent(
            event,
            NONE + beginDrag.toInt(BEGIN_DRAG) + dispatchUp.toInt(DISPATCH_UP) + dragAtDown.toInt(DRAG_AT_DOWN))
    }
}

private fun Boolean.toInt(trueValue: Int): Int = if (this) trueValue else 0

/** [MotionLayout] implementing [TouchInterruptParent]. */
class MotionLayoutTouchInterrupt @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MotionLayout(context, attrs, defStyleAttr), TouchInterruptParent {
    private val mInterruptHelper = TouchInterruptParent.Helper(this)

    /** Intercept for [onTouchEvent] - if this returns false touch event is not accepted and super is not called. */
    var acceptTouchEventListener: ((MotionEvent) -> Boolean)? = null

    init {
        // isMotionEventSplittingEnabled = false
    }

    override fun interruptOngoingTouchEvent(event: MotionEvent?, actions: Int) {
        mInterruptHelper.interruptOngoingTouchEvent(event, actions)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // always run both intercepts
        val interrupt = mInterruptHelper.onInterceptTouchEvent(event)
        val superIntercept = super.onInterceptTouchEvent(event)
        return interrupt || superIntercept
    }

    override fun isInterruptingTouchEventNow() = mInterruptHelper.isInterruptingTouchEventNow()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (acceptTouchEventListener?.invoke(event) != false)
            super.onTouchEvent(event)
        else false
    }
}

/** [ConstraintLayout] implementing [TouchInterruptParent]. */
class ConstraintLayoutTouchInterrupt @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), TouchInterruptParent {
    private val mInterruptHelper = TouchInterruptParent.Helper(this)

    /** Intercept for [onTouchEvent] - if this returns false touch event is not accepted and super is not called. */
    var acceptTouchEventListener: ((MotionEvent) -> Boolean)? = null

    override fun interruptOngoingTouchEvent(event: MotionEvent?, actions: Int) {
        mInterruptHelper.interruptOngoingTouchEvent(event, actions)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return mInterruptHelper.onInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)
    }

    override fun isInterruptingTouchEventNow() = mInterruptHelper.isInterruptingTouchEventNow()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (acceptTouchEventListener?.invoke(event) != false)
            super.onTouchEvent(event)
        else false
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
    /** [TouchInterruptParent.Action] to perform. */
    var interruptAction: Int = TouchInterruptParent.Action.ALL,
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
                if (isInTouch() || isActive?.invoke() == false) return false
                startX = event.x
                startY = event.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isInTouch()) return false
                if (checkSlope(view, event)) {
                    interruptParent.interruptOngoingTouchEvent(event, interruptAction)
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

    /** See if there's an active touch event being monitored. */
    fun isInTouch() = startX != null

    /** Builder to create multiple identical instances. */
    class Builder(
        /** View to force interrupt in. */
        var interruptParent: TouchInterruptParent,
        /** In which direction drag is detected. */
        var orientation: Orientation = Orientation.BOTH,
        /** Touch slope after which interrupt triggers. */
        var slope: Int = -1,
        /** [TouchInterruptParent.Action] to perform. */
        var interruptAction: Int = TouchInterruptParent.Action.ALL,
        /** Called during DOWN event to see if it should be taken. If null it always is. */
        var isActive: (() -> Boolean)? = null
    ) {
        fun build() =
            OnSlopeDragInterrupt(interruptParent, orientation, slope, interruptAction, isActive)
    }
}