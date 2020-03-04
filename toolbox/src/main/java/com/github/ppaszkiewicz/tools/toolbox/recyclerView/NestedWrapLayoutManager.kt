package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.math.MathUtils
import androidx.core.util.set
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min


/**
 * Layout manager that handles wrap height (or width in horizontal mode) within a scroll view and
 * recycles views based on [scrollParent] scroll position.
 *
 * Only supports single viewtype and items must have identical size unaffected by binding content.
 *
 * If you're using custom scroll listener on [scrollParent] already and don't want it overridden,
 * set [forceListener] as false and manually call [onScrollTick] of this layout manager
 * during each scroll event.
 *
 * @param scrollParent scroll view this layoutmanager observes. This supports nested scrolling
 * with [NestedScrollView] or injects scroll listener into any other view.
 * @param orientation [VERTICAL] (default) or [HORIZONTAL]
 * @param forceListener internally attach this as listener to [scrollParent] so scrolls performed
 *              outside recyclerView work (default: true)
 */
class NestedWrapLayoutManager @JvmOverloads constructor(
    val scrollParent: ViewGroup,
    val orientation: Int = VERTICAL,
    val forceListener: Boolean = true
) : RecyclerView.LayoutManager(), NestedScrollView.OnScrollChangeListener {
    companion object {
        const val TAG = "NWLayoutM"
        /** Vertical orientation. */
        const val VERTICAL = 1
        /** Horizontal orientation. */
        const val HORIZONTAL = 0

        /** Always lay out enough views to fill parent scroll viewport. This activates extra scroll
         * optimization. */
        const val LAYOUT_FIXED = 0
        /** Lay out enough views to fill parent scroll viewport if any item is visible. */
        const val LAYOUT_LAZY = 1
        /** Lay out just enough views to fill parent scroll viewport as scrolling happens. */
        const val LAYOUT_DYNAMIC = 2
    }


    // ---- configuration variables ----------------------------------------------------------------
    /** If true only one item view type is supported. */
    private val singleItemType = true

    /**
     * Extra views bound and laid outside the visible area.
     * Increase this value if you encounter flickering edge layouts during fast scroll and flings.
     *
     * **Default: 0**
     * */
    var outOfBoundsViews = 0
        set(value) {
            require(value >= 0)
            check(childCount == 0) { "outOfBoundsViews have to be set before layout" }
            field = value
        }

    /**
     * Maximum amount of viewholders prefetched while scrolling/flinging (default: 5).
     *
     * Note: this only works if RecyclerView is within nested scroll view, and RecyclerView itself
     * has initiated the scroll event.
     * */
    var prefetchItemCount = 5

    /**
     * Layout strategy to use.
     *
     * Valid values are [LAYOUT_FIXED] _(default)_, [LAYOUT_LAZY] and [LAYOUT_DYNAMIC].
     */
    var layoutStrategy = LAYOUT_FIXED

    /// ---- end -----------------------------------------------------------------------------------

    /** Currently displayed list of items. */
    var currentlyVisibleItemRange: IntRange = IntRange.EMPTY
        private set


    /**
     * Determines if anything will be recycled on scroll. If false it means entire RecyclerView content
     * fits within viewport of parent scroll view.
     *
     * Raised during layout phase.
     * */
    var isEntireContentInViewPort = false
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

    /** Attached scroll listener - used only for unspecified scroll views on low apis. */
    private var mScrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    /** Measured item view type sizes (right now only 1 type is supported) */
    private val itemSizes = SparseArray<Point>(1)
    private val itemHeight: Int
        get() = itemSizes.valueAt(0).y
    private val itemWidth: Int
        get() = itemSizes.valueAt(0).x


    init {
        require(orientation == HORIZONTAL || orientation == VERTICAL)
    }

    // this is mock-modified during measurement to trick super implementations
    private var scrollsVertically = scrollParent is NestedScrollView && orientation == VERTICAL
    // always false because there's no nested horizontal layout
    private var scrollsHorizontally = false

    /** Orientation helper that switches methods called in different configuration. */
    private val orientationHelper = if (orientation == HORIZONTAL) HorizontalOrientationHelper() else VerticalOrientationHelper()

    override fun isAutoMeasureEnabled() = false
    override fun canScrollVertically() = scrollsVertically
    override fun canScrollHorizontally() = scrollsHorizontally
    override fun supportsPredictiveItemAnimations() = false

    override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
        if (childCount == 0) {
            val v0 = recycler.getViewForPosition(0)
            orientationHelper.measure(v0, state.itemCount, widthSpec, heightSpec)
            recycler.recycleView(v0)
            //Log.d(TAG, "onMeasure done (recycled), $itemWidth:$itemHeight!")
        } else {
            val v0 = getChildAt(0)!!
            orientationHelper.measure(v0, state.itemCount, widthSpec, heightSpec)
            //Log.d(TAG, "onMeasure done (existed) $itemWidth:$itemHeight!")
        }
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

    private fun layoutChildrenInLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
        removedViews: SparseIntArray?,
        dScroll: Int) {
        val itemCount = state?.itemCount ?: itemCount
        val viewCache = SparseArray<View>(childCount)
        val visibleItemRange = if (childCount != 0) {
            val range = getVisibleItemRange(dScroll, state)
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
            if (range.last >= itemCount) {
                // item count was reduced, clamp to max current possible size
                range = max(itemCount - 1 - viewCountToFit(orientationHelper.parentContentSize()) - outOfBoundsViews, 0) until itemCount
            }
            range
        } else {
            getVisibleItemRange(dScroll, state)
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
        // optimization flag that will prevent view recycling during scroll
        isEntireContentInViewPort = layoutStrategy == LAYOUT_FIXED && visibleItemRange.first == 0 && visibleItemRange.last == (itemCount) - 1
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
            //measureChildWithMargins(view, 0, 0)
            orientationHelper.layoutViewForPosition(view, position)
        } else {
            attachView(view)
            viewCache.remove(position)
        }
        return view
    }

    /**
     * Determine currently visible item range.
     *
     * This is the core function determining what is actually shown.
     *
     * @param dScroll scroll offset to apply, this can be used when view is being scrolled
     * @param state recyclerState to use if available
     * */
    fun getVisibleItemRange(dScroll: Int = 0, state: RecyclerView.State? = null): IntRange {
        val itemCount = state?.itemCount ?: itemCount
        // if this was dispatched from nested scroll listener fallback ignore dy
        val dToUse = if (isInNestedScroll) dScroll else 0
        val recyclerLocation = orientationHelper.recyclerScrollLocation() - dToUse
        val parentContentSize = orientationHelper.parentContentSize()

        // see if layout manager is configured to skip laying out if recycler view is not within
        // visible bounds
        if (layoutStrategy != LAYOUT_FIXED && !isRecyclerViewVisible(recyclerLocation, parentContentSize)) {
            return IntRange.EMPTY
        }
        // recycler view is within bounds, calculate visible items
        val visibleItems = getVisibleItemCount(recyclerLocation, parentContentSize)
        val firstItem = max((-recyclerLocation) / orientationHelper.itemSize, 0)
        val r = if (firstItem <= 0) {
            // top of recyclerview is visible
            0..visibleItems + outOfBoundsViews
        } else if (firstItem + visibleItems + outOfBoundsViews >= itemCount) {
            // bottom of recyclerview is visible
            itemCount - 1 - visibleItems - outOfBoundsViews until itemCount
        } else {
            // middle of recyclerview is visible
            firstItem - outOfBoundsViews / 2..firstItem + visibleItems + outOfBoundsViews / 2 + outOfBoundsViews % 2
        }
        // clamp the range to valid values
        return max(0, r.first)..min(r.last, itemCount - 1)
    }

    override fun collectAdjacentPrefetchPositions(dx: Int, dy: Int, state: RecyclerView.State, layoutPrefetchRegistry: LayoutPrefetchRegistry) {
        // prefetch items only is there's any momentum
        val scroll = state.remainingScroll
        if(scroll == 0) return
        val itemsToScroll = absClamp(state.remainingScroll / orientationHelper.itemSize,prefetchItemCount)
        if(itemsToScroll == 0) return
        val prefetchList = when{
            itemsToScroll > 0 -> {
                currentlyVisibleItemRange.last..min(currentlyVisibleItemRange.last+itemsToScroll, itemCount-1)
            }
            else -> {
                (max(currentlyVisibleItemRange.first+itemsToScroll, 0)..currentlyVisibleItemRange.first).reversed()
            }
        }
        var prefetchDist = 0
        prefetchList.forEach {
            prefetchDist+=orientationHelper.itemSize
            layoutPrefetchRegistry.addPosition(it, prefetchDist)
        }
    }

    override fun requestLayout() {
        currentlyVisibleItemRange = IntRange.EMPTY
        super.requestLayout()
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State?): Int {
        if (isEntireContentInViewPort) return 0    // no recycling will happen
        layoutChildrenInLayout(recycler, state, null, dx)
        return 0
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State?): Int {
        if (isEntireContentInViewPort) return 0    // no recycling will happen
        layoutChildrenInLayout(recycler, state, null, dy)
        return 0
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mRecyclerView = view
        if (forceListener) when {
            scrollParent is NestedScrollView -> scrollParent.setOnScrollChangeListener(this)
            Build.VERSION.SDK_INT >= 23 -> scrollParent.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                onScrollTick()
            }
            else -> {
                // low api fallback: flickering might happen because observer is less precise
                val scrollObs = ViewTreeObserver.OnScrollChangedListener {
                    onScrollTick()
                }
                scrollParent.viewTreeObserver.addOnScrollChangedListener(scrollObs)
                mScrollListener = scrollObs
            }
        }
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        mRecyclerView = null
        if (forceListener) when {
            scrollParent is NestedScrollView -> scrollParent.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
            Build.VERSION.SDK_INT >= 23 -> scrollParent.setOnScrollChangeListener(null)
            else -> {
                scrollParent.viewTreeObserver?.removeOnScrollChangedListener(mScrollListener)
                mScrollListener = null
            }
        }
    }

    override fun onScrollChange(v: NestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        if (!isInNestedScroll) recyclerView.scrollBy(scrollX - oldScrollX, scrollY - oldScrollY)
    }

    /** Call this method during [scrollParent] onScrolled events if you opted out of [forceListener]. */
    fun onScrollTick() {
        if (!isEntireContentInViewPort)
            layoutChildrenInLayout(reflectRecycler, null, null, 0)
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

    // use default layout params
    override fun generateDefaultLayoutParams() = orientationHelper.generateDefaultLayoutParams()

    /** content width (without padding) */
    private fun widthNoPadding() = width - paddingLeft - paddingRight

    /** content height (without padding) */
    private fun heightNoPadding() = height - paddingTop - paddingBottom

    /** Helper method to determine strategy for calculating [viewCountToFit]. */
    private fun getVisibleItemCount(recyclerLocation: Int, parentContentSize: Int): Int {
        // default common case
        if (layoutStrategy != LAYOUT_DYNAMIC) return viewCountToFit(parentContentSize)

        val size = if (recyclerLocation >= 0) {
            parentContentSize - recyclerLocation
        } else
            orientationHelper.recyclerViewContentSize + recyclerLocation
        return viewCountToFit(min(size, parentContentSize))
    }

    /** Amount of views that need to be laid out to completely fill 1 viewport of [size]. */
    private fun viewCountToFit(size: Int) = min(size / orientationHelper.itemSize + 1, itemCount - 1)

    /** Check if recycler is visible at all. Pass current [recyclerScrollLocation], optionally altered by
     * scroll diff, and freshly calculated [parentContentSize]. */
    private fun isRecyclerViewVisible(recyclerScrollLocation: Int, parentContentSize: Int): Boolean {
        return recyclerScrollLocation < parentContentSize &&
                recyclerScrollLocation > -orientationHelper.recyclerViewContentSize
    }

    // reflect recycler, used when listening to non-nested scroll
    private val reflectRecycler: RecyclerView.Recycler
        get() {
            val f = RecyclerView::class.java.getDeclaredField("mRecycler")
            f.isAccessible = true
            return f.get(recyclerView) as RecyclerView.Recycler
        }

    /** Method implementation that differ depending on selected orientation. */
    private abstract class OrientationHelper {
        /** Relevant item size - height vertically, width horizontally */
        abstract val itemSize: Int

        /** Relevant visible recycler position - y vertically, x horizontally */
        abstract fun recyclerScrollLocation(): Int

        /** How many pixels of content are visible in scrollable direction */
        abstract fun parentContentSize(): Int

        /** Measure the size of recyclerView. */
        abstract fun measure(v0: View, itemCount: Int, widthSpec: Int, heightSpec: Int)

        /** Layout a view at given position. */
        abstract fun layoutViewForPosition(v: View, position: Int)

        /** Unspecified layout params.*/
        abstract fun generateDefaultLayoutParams(): RecyclerView.LayoutParams

        /** Relevant content size (without paddings). */
        abstract val recyclerViewContentSize: Int
    }

    private inner class VerticalOrientationHelper : OrientationHelper() {
        override val itemSize
            get() = itemHeight

        override fun recyclerScrollLocation() = recyclerView.nestedTop() + paddingTop
        override fun parentContentSize() = scrollParent.run {
            height - takeIfPaddingClips { paddingTop + paddingBottom }
        }

        override fun measure(v0: View, itemCount: Int, widthSpec: Int, heightSpec: Int) {
            withMockedScroll { measureChildWithMargins(v0, 0, 0) }
            val itemW = v0.measuredWidthWithMargins()
            val itemH = v0.measuredHeightWithMargins()
            val w = chooseSize(
                widthSpec,
                itemW + paddingLeft + paddingRight,
                paddingLeft + paddingRight
            )
            val h = itemH * itemCount + paddingTop + paddingBottom
            require(v0.measuredHeight > 0) { "item height of match_parent not supported in vertical orientation" }
            setMeasuredDimension(w, h)
            itemSizes[getItemViewType(v0)] = Point(itemW, itemH)
        }

        override fun layoutViewForPosition(v: View, position: Int) {
            withMockedScroll { measureChildWithMargins(v, 0, 0) }
            val viewTop = paddingTop + (position * itemHeight)
            val viewLeft = paddingLeft
            val itemWidth = this@NestedWrapLayoutManager.itemWidth.takeIf { it > 0 }
                ?: widthNoPadding()
            // layout the view
            layoutDecoratedWithMargins(
                v, viewLeft, viewTop,
                viewLeft + itemWidth,
                viewTop + itemHeight
            )
        }

        override fun generateDefaultLayoutParams() = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

        // top padding is already removed when calculating first item position
        override val recyclerViewContentSize: Int
            get() = recyclerView.height - recyclerView.paddingBottom - recyclerView.paddingTop

        /** TOP position relative to nested scroll view parent */
        private tailrec fun View.nestedTop(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> {
                top + current - scrollParent.scrollY - scrollParent.takeIfPaddingClips { paddingTop }
            }
            is ViewGroup -> p.nestedTop(top + current)
            else -> throw IllegalStateException("Invalid scrollParent provided!!")
        }

        // mock isScrollingVertically value to trick super measurement
        private inline fun withMockedScroll(f: () -> Unit) = scrollsVertically.let {
            scrollsVertically = true
            f()
            scrollsVertically = it
        }
    }

    private inner class HorizontalOrientationHelper : OrientationHelper() {
        override val itemSize
            get() = itemWidth

        override fun recyclerScrollLocation() = recyclerView.nestedLeft() + paddingLeft
        override fun parentContentSize() = scrollParent.run {
            width - takeIfPaddingClips { paddingLeft + paddingRight }
        }

        override fun measure(v0: View, itemCount: Int, widthSpec: Int, heightSpec: Int) {
            withMockedScroll { measureChildWithMargins(v0, 0, 0) }
            val itemW = v0.measuredWidthWithMargins()
            val itemH = v0.measuredHeightWithMargins()
            val w = itemW * itemCount + paddingLeft + paddingRight
            val h = chooseSize(
                heightSpec,
                itemH + paddingTop + paddingBottom,
                paddingTop + paddingBottom
            )
            require(v0.measuredWidth > 0) { "item width of match_parent not supported in horizontal orientation" }
            setMeasuredDimension(w, h)
            itemSizes[getItemViewType(v0)] = Point(itemW, itemH)
        }

        override fun layoutViewForPosition(v: View, position: Int) {
            withMockedScroll { measureChildWithMargins(v, 0, 0) }
            val viewTop = paddingTop
            val viewLeft = paddingLeft + position * itemWidth
            val itemHeight = this@NestedWrapLayoutManager.itemHeight.takeIf { it > 0 }
                ?: heightNoPadding()
            // layout the view
            layoutDecoratedWithMargins(
                v, viewLeft, viewTop,
                viewLeft + itemWidth,
                viewTop + itemHeight
            )
        }

        override fun generateDefaultLayoutParams() = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.MATCH_PARENT
        )

        // left padding is already removed when calculating first item position
        override val recyclerViewContentSize: Int
            get() = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight

        /** LEFT position relative to nested scroll view parent */
        private tailrec fun View.nestedLeft(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> {
                left + current - scrollParent.scrollX - scrollParent.takeIfPaddingClips { paddingLeft }
            }
            is ViewGroup -> p.nestedLeft(left + current)
            else -> throw IllegalStateException("Invalid scrollParent provided!!")
        }

        // mock isScrollingHorizontally value to trick super measurement
        private inline fun withMockedScroll(f: () -> Unit) = scrollsHorizontally.let {
            scrollsHorizontally = true
            f()
            scrollsHorizontally = it
        }
    }

    private fun View.params() = layoutParams as RecyclerView.LayoutParams
    /** Return result of [padding] if clip to padding is true, otherwise 0. */
    private inline fun ViewGroup.takeIfPaddingClips(padding: View.() -> Int) = run {
        if(!clipToPadding) 0 else padding()
    }
    /** Remaining scroll valid for current orientation. */
    private val RecyclerView.State.remainingScroll
        get() = if(orientation == VERTICAL) remainingScrollVertical else remainingScrollHorizontal

    /** Clamp [value] between -minMax and minMax. */
    private fun absClamp(value: Int, minMax : Int) = when{
        value < -minMax -> -minMax
        value > minMax -> minMax
        else -> value
    }
    /** measured width with margins - if width is 0 then margins are ignored and 0 is returned. */
    private fun View.measuredWidthWithMargins() = when(measuredWidth){
        0 -> 0
        else -> measuredWidth + marginLeft + marginRight
    }
    /** measured height with margins - if height is 0 then margins are ignored and 0 is returned. */
    private fun View.measuredHeightWithMargins() = when(measuredHeight){
        0 -> 0
        else -> measuredHeight + marginTop + marginBottom
    }

}