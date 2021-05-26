package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.core.content.res.use
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ppaszkiewicz.tools.toolbox.view.orientation.RecyclerViewOrientationHelper

/* Requires view.orientation package. */
/**
 * Gridlayout manager managing span gravity.
 *
 * Usually it's easier to have a container (Frame or Constraint layouts) in ViewHolder, but this is for
 * special case where it's not possible or inefficient (ViewHolders shared between different LayoutManagers).
 *
 * If this is inflated through xml (using `app:layoutManager`) you can set gravity using `android:gravity`.
 * */
class GravityGridLayoutManager : GridLayoutManager {
    companion object{
        const val DEFAULT_GRAVITY = Gravity.CENTER
    }
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        if (attrs == null) return
        context.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.gravity),
            defStyleAttr,
            defStyleRes
        ).use {
            gravity = it.getInt(0, DEFAULT_GRAVITY)
        }
    }

    constructor(context: Context, spanCount : Int) : super(context, spanCount)

    constructor(context: Context, spanCount: Int, gravity: Int) : super(context, spanCount) {
        this.gravity = gravity
    }

    constructor(
        context: Context,
        spanCount: Int,
        orientation: Int,
        reverseLayout: Boolean,
        gravity: Int
    ) : super(context, spanCount, orientation, reverseLayout) {
        this.gravity = gravity
    }

    // internal variables
    private var childOffset = 0
    private var mAvailableSpace = 0
    private val mChildSize = Rect() // raw coordinates of child size

    private var _orientHelper: RecyclerViewOrientationHelper.LayoutManager? = null
        get() = field ?: when (orientation) {
            RecyclerView.HORIZONTAL -> RecyclerViewOrientationHelper.horizontal(this)
            RecyclerView.VERTICAL -> RecyclerViewOrientationHelper.vertical(this)
            else -> throw IllegalStateException()
        }.also { field = it }
    private val orientHelper: RecyclerViewOrientationHelper.LayoutManager
        get() = _orientHelper!!

    /**
     * Gravity of items.
     *
     * - [Gravity.CENTER] aligns each item to center of its span
     * - [Gravity.FILL] spreads items evenly towards the edges (so first span is aligned with start edge and last span with last edge)
     *
     * *Modifying this value after first layout is not supported and might lead to artifacts.*
     * */
    var gravity: Int = DEFAULT_GRAVITY

    override fun layoutDecoratedWithMargins(
        child: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        orientHelper.apply {
            // quick exit case (start gravity is default behavior)
            if (handler.isAltStart(gravity)) {
                super.layoutDecoratedWithMargins(child, left, top, right, bottom)
                return
            }
            val params = child.layoutParams as LayoutParams
            mChildSize.set(left, top, right, bottom)

            // size of one span (excluding recyclerview padding)
            mAvailableSpace = (altSize - paddingAltStart - paddingAltEnd) / spanCount
            // space leftover after laying out this child in amount of spans it required
            mAvailableSpace =
                mAvailableSpace * params.spanSize - handler.altSizeOf(mChildSize)

            childOffset = when {
                handler.isAltEnd(gravity) -> mAvailableSpace
                handler.hasAltFill(gravity) -> {
                    if (spanCount == 1) mAvailableSpace / 2
                    else mAvailableSpace / (spanCount - 1) * params.spanIndex
                }
                handler.hasAltCenter(gravity) -> mAvailableSpace / 2
                else -> 0
            }
            // inject orientation into
            handler.offset(mChildSize, altDir = childOffset)
            super.layoutDecoratedWithMargins(
                child,
                mChildSize.left,
                mChildSize.top,
                mChildSize.right,
                mChildSize.bottom
            )
        }
    }

    override fun setOrientation(orientation: Int) {
        _orientHelper = null
        super.setOrientation(orientation)
    }
}