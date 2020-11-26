package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Gridlayout manager managing span gravity.
 *
 * Usually it's easier to have a container (Frame or Constraint layouts) in ViewHolder, but this is for
 * special case where it's not possible or inefficient (ViewHolders shared between different LayoutManagers).
 *
 * You can use `android:gravity` to declare it in layout xml.
 * */
open class GravityGridLayoutManager : GridLayoutManager {
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        if (attrs == null) return
        val ta = context.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.gravity),
            defStyleAttr,
            defStyleRes
        )
        gravity = ta.getInt(0, Gravity.START or Gravity.TOP)
        ta.recycle()
    }

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

    /** Gravity of items. Use [Gravity.FILL] to spread items out evenly. */
    var gravity: Int = Gravity.START or Gravity.TOP

    // internal variables
    private var verticalChildMargin = 0
    private var horizontalChildMargin = 0
    private var mAvailableSpace = 0
    private lateinit var mChildLayoutParams: LayoutParams

    override fun layoutDecoratedWithMargins(
        child: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        // quick exit cases
        if (orientation == RecyclerView.VERTICAL && gravity has Gravity.START) {
            super.layoutDecoratedWithMargins(child, left, top, right, bottom)
            return
        }
        if (orientation == RecyclerView.HORIZONTAL && gravity has Gravity.TOP) {
            super.layoutDecoratedWithMargins(child, left, top, right, bottom)
            return
        }
        mChildLayoutParams = child.layoutParams as LayoutParams
        // alter based on gravity
        horizontalChildMargin = if (orientation == RecyclerView.VERTICAL) {
            mAvailableSpace = spanWidth() * mChildLayoutParams.spanSize - (right - left)
            when (gravity) {
                Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> mAvailableSpace / 2
                Gravity.END -> mAvailableSpace
                Gravity.FILL, Gravity.FILL_HORIZONTAL -> {
                    if (spanCount == 1) mAvailableSpace / 2
                    else mAvailableSpace / (spanCount - 1) * mChildLayoutParams.spanIndex
                }
                else -> 0
            }
        } else 0

        verticalChildMargin = if (orientation == RecyclerView.HORIZONTAL) {
            mAvailableSpace = spanHeight() * mChildLayoutParams.spanSize - (bottom - top)
            when (gravity) {
                Gravity.CENTER, Gravity.CENTER_VERTICAL -> mAvailableSpace / 2
                Gravity.BOTTOM -> mAvailableSpace
                Gravity.FILL, Gravity.FILL_VERTICAL -> {
                    if (spanCount == 1) mAvailableSpace / 2
                    else mAvailableSpace / (spanCount - 1) * mChildLayoutParams.spanIndex
                }
                else -> 0
            }
        } else 0

        super.layoutDecoratedWithMargins(
            child,
            left + horizontalChildMargin,
            top + verticalChildMargin,
            right + horizontalChildMargin,
            bottom + verticalChildMargin
        )
    }

    // width of single span - only valid if orientation == VERTICAL
    private fun spanWidth() = (width - paddingLeft - paddingRight) / spanCount

    // height of single span - only valid if orientation == HORIZONTAL
    private fun spanHeight() = (height - paddingTop - paddingBottom) / spanCount

    /** check if flag is raised within this int **/
    private infix fun Int.has(flag: Int) = this and flag == flag
}