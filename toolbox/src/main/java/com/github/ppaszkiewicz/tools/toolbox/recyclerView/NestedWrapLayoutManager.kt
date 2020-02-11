package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.Size
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import androidx.core.util.set
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Layout manager that handles wrap height within nested scroll and recycles views based
 * on nested views scroll.
 *
 * Only supports single viewtype and items must have identical height unaffected by binding content.
 *
 * @param scrollParent scroll view this layoutmanager observes.
 */
class NestedWrapLayoutManager(val scrollParent: NestedScrollView) : RecyclerView.LayoutManager() {
    companion object {
        const val TAG = "NWLayoutM"
    }

    /**
     * Determines if anything will be recycled on scroll. If false it means entire RecyclerView content
     * fits within viewport of parent scroll view.
     *
     * Raised during measuring phase.
     * */
    var recyclesOnScroll = true
        private set

    /** If true only one item view type is supported. */
    private val singleItemType = true

    /**
     * Extra views bound and laid outside the visible area (default: 1).
     *
     * Increase this value if you encounter flickering edge layouts during fast scroll and flings.
     * */
    var outOfBoundsViews = 1
        set(value) {
            require(value >= 0)
            check(childCount == 0) { "outOfBoundsViews have to be set before layout" }
            field = value
        }

    /** Currently displayed list of items. */
    var currentlyVisibleItemRange: IntRange = IntRange.EMPTY
        private set

    // raised only if restore state was invoked
    private var mRangeWasRestored = false

    /** Measured item view type sizes (right now only 1 type is supported) */
    private val itemSizes = SparseArray<Size>()
    private val itemHeight: Int
        get() = itemSizes.valueAt(0).height
    private val itemWidth: Int
        get() = itemSizes.valueAt(0).width

    override fun isAutoMeasureEnabled() = false
    override fun canScrollVertically() = true
    override fun supportsPredictiveItemAnimations() = false

    override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
        if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.AT_MOST) {
            Log.e(TAG, "only wrap content height is supported!! - behavior unspecified")
        }
        if (childCount == 0) {
            val v0 = recycler.getViewForPosition(0)
            measureWith(v0, state.itemCount, widthSpec)
            recycler.recycleView(v0)
        } else {
            val v0 = getChildAt(0)!!
            measureWith(v0, state.itemCount, widthSpec)
        }
    }

    private fun measureWith(v0: View, itemCount: Int, widthSpec: Int) {
        measureChildWithMargins(v0, 0, 0)
        val w = chooseSize(widthSpec, v0.measuredWidth + paddingLeft + paddingRight, paddingLeft + paddingRight)
        val h = v0.measuredHeight * itemCount + paddingTop + paddingBottom
        setMeasuredDimension(w, h)
        itemSizes[getItemViewType(v0)] = Size(v0.measuredWidth, v0.measuredHeight)
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        // Nothing to show for an empty data set but clear any existing views
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }
        // Nothing to do during prelayout when empty
        if (childCount == 0 && state.isPreLayout) {
            return
        }
        recyclesOnScroll = height - (outOfBoundsViews + 1) * itemHeight > scrollParent.height
        layoutChildrenInLayout(recycler, state, null, 0)
    }

    lateinit var debugView: View
    //
    private fun layoutChildrenInLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        removedViews: SparseIntArray?,
        dy: Int) {
        val viewCache = SparseArray<View>(childCount)
        val visibleItemRange = if (childCount != 0) {
            val range = getVisibleItemRange(dy, state)
            // soft detach all views from recycler
            // If same range of items is visible do nothing
            if (range == currentlyVisibleItemRange) return
            for (i in 0 until childCount) {
                val viewId: Int = getChildAt(i)!!.params().viewAdapterPosition
                viewCache.put(viewId, getChildAt(i))
            }
            for (i in 0 until viewCache.size()) {
                detachView(viewCache.valueAt(i))
            }
            range
        } else if (mRangeWasRestored) {
            // restoring the item range
            mRangeWasRestored = false
            // clamp size of restored range for safety
            currentlyVisibleItemRange.first..min(currentlyVisibleItemRange.last, itemCount - 1)
        } else {
            // no children so add a view that will be used to determine recyclerviews scroll
            val view0 = addView(recycler, state, viewCache, 0)!!
            val range = getVisibleItemRange(dy, state)
            viewCache.put(0, view0)
            detachView(view0)
            range
        }

        // layout visible items
        visibleItemRange.forEach {
            addView(recycler, state, viewCache, it)
        }
        //scrap all unused views
        for (i in 0 until viewCache.size()) {
            val removingView = viewCache.valueAt(i)
            recycler.recycleView(removingView)
        }
        currentlyVisibleItemRange = visibleItemRange
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.getIntArray("range")?.let {
            currentlyVisibleItemRange = it[0]..it[1]
            mRangeWasRestored = true
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        return Bundle().apply {
            putIntArray("range", intArrayOf(currentlyVisibleItemRange.first, currentlyVisibleItemRange.last))
        }
    }

    // add new view from recycler or reattach view from viewCache
    private fun addView(recycler: RecyclerView.Recycler, state: RecyclerView.State, viewCache: SparseArray<View>, position: Int): View? {
        var view = viewCache[position]
        if (view == null) {
            // this try catch is copied because this internally crashes?
            try {
                view = recycler.getViewForPosition(position)
                addView(view)
            } catch (npe: NullPointerException) {
                Log.e(TAG, "failed to unscrap $position: ${npe.message}")
                try {
                    view = recycler.getViewForPosition(position)
                    addView(view)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "failed to unscrap $position twice, view not added: ${npe.message}")
                    return null
                }
            }
            if (state.isPreLayout) {
                // not supported?
            }
            measureChildWithMargins(view, 0, 0)
            layoutViewForPosition(view, position)
        } else {
            attachView(view)
            viewCache.remove(position)
        }
        return view
    }

    // lay out a single view at position
    private fun layoutViewForPosition(view: View, position: Int) {
        val viewTop = paddingTop + (position * itemHeight)
        val viewLeft = paddingLeft
        val itemWidth = this.itemWidth.takeIf { it > 0 } ?: widthNoPadding()
        // layout the view
        layoutDecoratedWithMargins(view,
            viewLeft, viewTop,
            viewLeft + itemWidth,
            viewTop + itemHeight
        )
    }

    /**
     * Determine currently visible item range.
     *
     * This is the core function determining what is actually shown.
     *
     * @param dy vertical offset to apply, this can be used when view is being scrolled
     * @param state recyclerState to use if available
     * */
    fun getVisibleItemRange(dy: Int = 0, state: RecyclerView.State? = null): IntRange {
        val itemCount = state?.itemCount ?: itemCount
        if (!recyclesOnScroll) return 0 until itemCount
        check(childCount > 0) { "Cannot determine range without any children" }
        val child = getChildAt(0)!!
        val recyclerTop = (child.parent as View).nestedTop() - paddingTop - dy
        val firstItem = max((-recyclerTop) / itemHeight, 0)
        val visibleItems = min(scrollParent.height / itemHeight + 1, itemCount-1)
        return if (firstItem <= 0) {
            // top of recycler is visible
            0..visibleItems + outOfBoundsViews
        } else if (firstItem + visibleItems + outOfBoundsViews >= itemCount) {
            // bottom of recycler is visible
            itemCount - 1 - visibleItems - outOfBoundsViews until itemCount
        } else {
            // middle of recycler is visible
            val r = firstItem..firstItem + visibleItems
            // clamp the range to valid values
            max(r.first - outOfBoundsViews / 2, 0)..min(r.last + outOfBoundsViews / 2 + outOfBoundsViews % 2, itemCount - 1)
        }
    }

    override fun requestLayout() {
        currentlyVisibleItemRange = IntRange.EMPTY
        super.requestLayout()
    }

    /** Request layout without invalidating current visible item range **/
    fun softRequestLayout() {
        val range = currentlyVisibleItemRange
        requestLayout()
        currentlyVisibleItemRange = range
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (!recyclesOnScroll) return 0    // no recycling will happen
        layoutChildrenInLayout(recycler, state, null, dy)
        return 0
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        itemSizes.clear()
        removeAllViews()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // this layout manager is not dynamic, relayout everything
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // this layout manager is not dynamic, relayout everything
    }

    private fun View.params() = layoutParams as RecyclerView.LayoutParams

    // TOP position relative to nested scroll view parent
    private tailrec fun View.nestedTop(current: Int = 0): Int = when (val p = parent) {
        scrollParent -> {
            top + current - scrollParent.scrollY
        }
        is ViewGroup -> p.nestedTop(top + current)
        else -> throw IllegalStateException("Invalid scrollParent provided!!")
    }

    // content width (without padding)
    private fun widthNoPadding() = width - paddingLeft - paddingRight

    // use default layout params
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT)
    }
}