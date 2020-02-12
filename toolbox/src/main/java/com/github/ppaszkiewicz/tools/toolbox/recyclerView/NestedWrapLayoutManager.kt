package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.os.Build
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
 * If you're using custom scroll listener on [scrollParent] already and don't want it overridden,
 * manually dispatch all scroll events to [onScrollChange] of this layout manager.
 *
 * @param scrollParent scroll view this layoutmanager observes.
 * @param forceListener attach this as listener to [scrollParent] so scrolls performed
 *              outside recyclerView work
 */
class NestedWrapLayoutManager @JvmOverloads constructor(
    val scrollParent: NestedScrollView,
    val forceListener: Boolean = true
) : RecyclerView.LayoutManager(), NestedScrollView.OnScrollChangeListener {
    companion object {
        const val TAG = "NWLayoutM"
    }

    /**
     * Determines if anything will be recycled on scroll. If false it means entire RecyclerView content
     * fits within viewport of parent scroll view.
     *
     * Raised during layout phase.
     * */
    var isEntireContentInViewPort = false
        private set

    /** If true only one item view type is supported. */
    private val singleItemType = true

    /**
     * Extra views bound and laid outside the visible area (default: 0).
     *
     * Increase this value if you encounter flickering edge layouts during fast scroll and flings.
     * */
    var outOfBoundsViews = 0
        set(value) {
            require(value >= 0)
            check(childCount == 0) { "outOfBoundsViews have to be set before layout" }
            field = value
        }

    /** Currently displayed list of items. */
    var currentlyVisibleItemRange: IntRange = IntRange.EMPTY
        private set

    /** Raised only if restore state was invoked, that means [currentlyVisibleItemRange] should be valid. */
    private var mRangeWasRestored = false

    /** Raised if this is currently in nested scroll. In that case ignore scroll listener on scroll parent. */
    private var isInNestedScroll = false

    /** RecyclerView this layoutmanager is attached to. Use [recyclerView] for null fallback. */
    private var mRecyclerView: RecyclerView? = null

    /** Currently managed recyclerView. */
    private val recyclerView: RecyclerView
        get() = mRecyclerView ?: getChildAt(0)!!.parent as RecyclerView

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
            //Log.d(TAG, "onMeasure done (recycled), $itemWidth:$itemHeight!")
        } else {
            val v0 = getChildAt(0)!!
            measureWith(v0, state.itemCount, widthSpec)
            //Log.d(TAG, "onMeasure done (existed) $itemWidth:$itemHeight!")
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
        layoutChildrenInLayout(recycler, state, null, 0)
    }

    lateinit var debugView: View
    //
    private fun layoutChildrenInLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
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
            var range = currentlyVisibleItemRange.first..currentlyVisibleItemRange.last
            if (range.last >= state?.itemCount ?: itemCount) {
                // item count was reduced, clamp to max current possible size
                range = max(itemCount - 1 - viewCountToFitViewPort() - outOfBoundsViews, 0) until itemCount
            }
            range
        } else {
            getVisibleItemRange(dy, state)
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
        // optimization flag that will prevent
        isEntireContentInViewPort = visibleItemRange.first == 0 && visibleItemRange.last == (state?.itemCount ?: itemCount) - 1
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
    private fun addView(recycler: RecyclerView.Recycler, state: RecyclerView.State?, viewCache: SparseArray<View>, position: Int): View? {
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
            if (state?.isPreLayout == true) {
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
        // if this was dispatched from nested scroll listener fallback ignore dy
        val dyToUse = if(isInNestedScroll) dy else 0
        val recyclerTop = recyclerView.nestedTop() + paddingTop - dyToUse
        val firstItem = max((-recyclerTop) / itemHeight, 0)
        val visibleItems = viewCountToFitViewPort()
        return if (firstItem <= 0) {
            // top of recycler is visible
            0..visibleItems + outOfBoundsViews
        } else if (firstItem + visibleItems + outOfBoundsViews >= itemCount) {
            // bottom of recycler is visible
            itemCount - 1 - visibleItems - outOfBoundsViews until itemCount
        } else {
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


    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State?): Int {
        if (isEntireContentInViewPort) return 0    // no recycling will happen
        layoutChildrenInLayout(recycler, state, null, dy)
        return 0
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mRecyclerView = view
        if (forceListener) scrollParent.setOnScrollChangeListener(this)
    }

    override fun onScrollChange(v: NestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        if(!isInNestedScroll) recyclerView.scrollBy(0, (scrollY - oldScrollY))
    }

    override fun onScrollStateChanged(state: Int) {
        isInNestedScroll = when (state) {
            RecyclerView.SCROLL_STATE_DRAGGING -> true
            RecyclerView.SCROLL_STATE_IDLE, RecyclerView.SCROLL_STATE_SETTLING -> {
                false
            }
            else -> false
        }
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        itemSizes.clear()
        currentlyVisibleItemRange = IntRange.EMPTY
        removeAllViews()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // this layout manager is not dynamic, relayout everything
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // this layout manager is not dynamic, relayout everything
    }

    private fun View.params() = layoutParams as RecyclerView.LayoutParams

    /** TOP position relative to nested scroll view parent */
    private tailrec fun View.nestedTop(current: Int = 0): Int = when (val p = parent) {
        scrollParent -> {
            top + current - scrollParent.scrollY - parentTopPadding()
        }
        is ViewGroup -> p.nestedTop(top + current)
        else -> throw IllegalStateException("Invalid scrollParent provided!!")
    }

    /** content width (without padding) */
    private fun widthNoPadding() = width - paddingLeft - paddingRight

    /** content height (without padding) */
    private fun heightNoPadding() = height - paddingTop - paddingBottom

    /** height of parent scroll views content (without padding unless clipping is disabled)**/
    private fun parentContentHeight() = scrollParent.run {
        if (Build.VERSION.SDK_INT >= 21 && !clipToPadding) {
            height
        } else {
            height - paddingTop - paddingBottom
        }
    }

    /** relevant top padding of parent scroll view - value depends if it clips. */
    private fun parentTopPadding() = scrollParent.run {
        if (Build.VERSION.SDK_INT >= 21 && !clipToPadding) {
            0
        } else {
            scrollParent.paddingTop
        }
    }

    /** amount of views that can need to be laid out to completely fill 1 parent viewport */
    private fun viewCountToFitViewPort() = min(parentContentHeight() / itemHeight + 1, itemCount - 1)

    // use default layout params
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT)
    }
}