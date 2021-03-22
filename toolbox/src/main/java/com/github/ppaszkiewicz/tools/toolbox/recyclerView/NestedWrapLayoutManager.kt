package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.util.*
import androidx.core.view.*
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.NestedWrapLayoutManager.Companion.HORIZONTAL
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.NestedWrapLayoutManager.Companion.VERTICAL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// requires AdapterMutationTracker.kt
// alpha version - predictive animations not ready
//todo: add behavior that will scroll parent scroll to maintain current anchor position upon item
// removal or addition
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
) : RecyclerView.LayoutManager(), NestedScrollView.OnScrollChangeListener,
    RecyclerView.SmoothScroller.ScrollVectorProvider {
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

    /** Object used to call methods from [scrollParent], mostly to handle compatibility
     * cases. */
    var scrollParentHandler = ScrollParentHandler()

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

    /** Measured item view type sizes (right now only 1 type is supported) */
    private val itemSizes = SparseArray<Point>(1)
    private val itemHeight: Int
        get() = itemSizes.valueAt(0).y
    private val itemWidth: Int
        get() = itemSizes.valueAt(0).x

    init {
        require(orientation == HORIZONTAL || orientation == VERTICAL)
    }

    // scrollability is mock-modified during measurement to trick super implementations
    private var scrollsVertically = scrollParent is NestedScrollView && orientation == VERTICAL

    // scrollability is mock-modified during measurement to trick super implementations
    private var scrollsHorizontally = false

    /**
     * Position of views that will exit viewport during predictive animation.
     * Calculated during pre-layout and carried over to post-layout.
     */
    private val exitingViews = mutableSetOf<Int>()

//    /**
//     * Moved items that will potentially enter the layout.
//     *
//     * Key - target position, value - source position.
//     * */
//    private val pendingMoves = SparseIntArray()

    /** Orientation helper that switches methods called in different configuration. */
    private val orientationHelper =
        if (orientation == HORIZONTAL) HorizontalOrientationHelper() else VerticalOrientationHelper()

    /** Observer for adapter - this is really odd that's even needed but [onItemsMoved] is called
     * post-layout which makes it useless. */
    private val adapterMutationTracker = AdapterMutationTracker()
//    object : RecyclerView.AdapterDataObserver() {
//        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
//            Log.d(TAG, "obs - item moved: $fromPosition $toPosition $itemCount")
//            repeat(itemCount) {
//                pendingMoves.put(toPosition + it, fromPosition + it)
//                Log.d(TAG, "items moved: $pendingMoves")
//            }
//        }
//    }

    override fun isAutoMeasureEnabled() = false
    override fun canScrollVertically() = scrollsVertically
    override fun canScrollHorizontally() = scrollsHorizontally
    override fun supportsPredictiveItemAnimations() = true

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) = when {
        state.itemCount == 0 -> super.onMeasure(recycler, state, widthSpec, heightSpec)
        childCount == 0 -> {
            recycler.getViewForPosition(0).let { view ->
                orientationHelper.measure(view, state.itemCount, widthSpec, heightSpec)
                recycler.recycleView(view)
            }
        }
        else -> {
            getChildAt(0)!!.let { view ->
                orientationHelper.measure(view, state.itemCount, widthSpec, heightSpec)
            }
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
        layoutChildrenInLayout(recycler, state, 0)
    }

    private fun layoutChildrenInLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
        dScroll: Int
    ) {
        val itemCount = state?.itemCount ?: itemCount
        val viewCache = SparseArray<View>(childCount)
        if (state?.isPreLayout == true) {
            exitingViews.clear()
        }
        val visibleItemRange = when {
            childCount != 0 -> {    // redoing existing layout
                val range = getVisibleItemRange(dScroll, state)
                if (!prepareViews(recycler, state, viewCache, range)) return
                range
            }
            mRangeWasRestored -> {  // restoring the item range
                mRangeWasRestored = false
                var range = currentlyVisibleItemRange.first..currentlyVisibleItemRange.last
                if (itemCount == 0) {
                    // no items exist after restoration
                    range = IntRange.EMPTY
                } else if (range.last >= itemCount) {
                    // item count was reduced, clamp to max current possible size
                    range = coercedRange(
                        itemCount - 1 - viewCountToFit(orientationHelper.parentContentSize()) - outOfBoundsViews,
                        itemCount - 1
                    )
                }
                range
            }
            itemSizes.isNotEmpty() -> getVisibleItemRange(dScroll, state) // first layout
            else -> IntRange.EMPTY  // layout is empty and not measured
        }
        // layout visible items and store updated value in post-layout phase
        if (state?.isPreLayout != true) {
            visibleItemRange.forEach { addView(recycler, viewCache, it) }
            currentlyVisibleItemRange = visibleItemRange
            // optimization flag that will prevent view recycling during scroll
            isEntireContentInViewPort =
                layoutStrategy == LAYOUT_FIXED && visibleItemRange.first == 0 && visibleItemRange.last == (itemCount) - 1
        }
        layoutForPredictiveAnimations(recycler, state, viewCache) // called during POST layout only
        //scrap all unused views
        for (i in 0 until viewCache.size()) {
            val removingView = viewCache.valueAt(i)
            recycler.recycleView(removingView)
        }
        adapterMutationTracker.clear()
        if (state?.isPreLayout != true) {
            exitingViews.clear()
        }
    }

    /**
     * Call [prepareViewsInPreLayout] or [prepareViewsForLayout] depending on current state.
     * @return `false` if no preparation was performed and layout should be entirely skipped
     * */
    private fun prepareViews(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
        viewCache: SparseArray<View>,
        range: IntRange
    ): Boolean {
        return when {
            state?.willRunPredictiveAnimations() == true -> {
                if (state.isPreLayout) prepareViewsInPreLayout(recycler, state, range)
                else prepareViewsForLayout(recycler, state, viewCache)
                true
            }
            anyChildIsChanged() || range != currentlyVisibleItemRange -> {
                prepareViewsForLayout(recycler, state, viewCache)
                true
            }
            else -> {
                // no predictive animations to run and no changes are being made, do nothing
                false
            }
        }
    }

    /** Cache or scrap all views currently in layout. */
    private fun prepareViewsForLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
        viewCache: SparseArray<View>
    ) {
        check(state?.isPreLayout != true)
        // scrap modified items (they will get rebound by adapter) but just detach others
        while (childCount > 0) {
            val view = getChildAt(0)!!
            val p = view.params()
            when {
                p.isItemChanged || p.isItemRemoved || p.absoluteAdapterPosition in exitingViews -> {
                    detachAndScrapView(view, recycler)
                }
                else -> {
                    viewCache.put(p.absoluteAdapterPosition, view)
                    detachView(view)
                }
            }
        }
    }

    /**
     * See what children are about to leave the [newRange] and populate [exitingViews] then lay out
     * views that will enter the visible range.
     */
    private fun prepareViewsInPreLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        newRange: IntRange
    ) {
        check(state.isPreLayout)
        val laidOutViews = mutableSetOf<Int>()
        repeat(childCount) {
            val view = getChildAt(it)!!
            val p = view.params()
            val postPosition = p.absoluteAdapterPosition
            if (!p.isItemRemoved) {
                if (postPosition !in newRange) {
                    Log.d(TAG, "view l:${p.viewLayoutPosition}, a:$postPosition top ${view.top}")
                    Log.d(TAG, "     -> exited to $postPosition")
                    exitingViews.add(postPosition)
                } else laidOutViews.add(postPosition)
            }
        }
        Log.d(TAG, "range: $newRange")
        newRange.forEach {
            if (!laidOutViews.remove(it)) {
                val sourcePosition =
                    adapterMutationTracker.getPrepositionFor(it)
                if (sourcePosition < state.itemCount) {
                    // here's the confusing part: recycler only has small pre-calculated range
                    // of items that will come into layout which seems to be:
                    // # of viewholders in layout + # of viewholders that are leaving the layout
                    // for those items we have to get view from their "target" position
                    // and for all other we can query from source position
                    //todo: sometimes view is not properly animated in
                    val recPos = recycler.convertPreLayoutPositionToPostLayout(sourcePosition)
                    val view = if(recPos == sourcePosition)
                        recycler.getViewForPosition(it)
                    else recycler.getViewForPosition(sourcePosition)
                    Log.d(
                        TAG,
                        "view $it -> from $sourcePosition p- $recPos l- ${view.params().viewLayoutPosition} a:${view.params().absoluteAdapterPosition}"
                    )
                    addView(view)
                    orientationHelper.layoutViewForPosition(view, sourcePosition)
                }
            }
        }
    }

    /** add disappearing views that move out of bounds */
    private fun layoutForPredictiveAnimations(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?,
        viewCache: SparseArray<View>
    ) {
        if (!state!!.willRunPredictiveAnimations() || childCount == 0 || state.isPreLayout
            || !supportsPredictiveItemAnimations()
        ) {
            return
        }
        // prevent views that are being removed from being recycled so remove anim can run
        for (i in viewCache.size - 1 downTo 0) {
            val view = viewCache.valueAt(i)
            if (view.params().isItemRemoved) {
                addDisappearingView(view)
                viewCache.removeAt(i)
            }
        }
        // add views that are "moving out" of visible bounds
        exitingViews.forEach {
            //Log.d(TAG, "animating exiting at $it")
            val exitingView = recycler.getViewForPosition(it)
            orientationHelper.revalidateViewPosition(exitingView)
            addDisappearingView(exitingView)
        }
    }

    private fun anyChildIsChanged(): Boolean {
        repeat(childCount) {
            if (getChildAt(it)?.params()?.isItemChanged == true) return true
        }
        return false
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        mRangeWasRestored = false
    }

    // add new view from recycler or reattach view from viewCache
    private fun addView(
        recycler: RecyclerView.Recycler,
        viewCache: SparseArray<View>,
        position: Int
    ): View? {
        var view = viewCache[position]
        if (view == null) { // get new view or rebind scrap for position
            view = tryAdd(position, recycler)
            if (view != null) {
                orientationHelper.layoutViewForPosition(view, position)
//                Log.d(
//                    TAG,
//                    "adding view for $position, ${view.params().viewLayoutPosition} ${view.top}"
//                )
            }
        } else {    // quickly reattach existing view
            orientationHelper.revalidateViewPosition(view) // in case something was removed/moved
//            Log.d(
//                TAG,
//                "reattaching view for $position, ${view.params().viewLayoutPosition} ${view.top}"
//            )
            attachView(view)
            viewCache.remove(position)
        }
        return view
    }

    private fun tryAdd(position: Int, recycler: RecyclerView.Recycler): View? {
        var view: View? = null
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
                view = null
            }
        }
        return view
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.getIntArray("range")?.let {
            currentlyVisibleItemRange = it[0]..it[1]
            mRangeWasRestored = true
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        return Bundle().apply {
            putIntArray(
                "range",
                intArrayOf(currentlyVisibleItemRange.first, currentlyVisibleItemRange.last)
            )
        }
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
        if (layoutStrategy != LAYOUT_FIXED
            && !isRecyclerViewVisible(recyclerLocation, parentContentSize)
        ) {
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
        return r.coerce(0, itemCount - 1)
    }

    override fun collectAdjacentPrefetchPositions(
        dx: Int,
        dy: Int,
        state: RecyclerView.State,
        layoutPrefetchRegistry: LayoutPrefetchRegistry
    ) {
        // prefetch items only is there's any momentum
        val scroll = state.remainingScroll
        if (scroll == 0) return
        // items to scroll will be negative when scrolling up
        val itemsToScroll =
            absClamp(state.remainingScroll / orientationHelper.itemSize, prefetchItemCount)
        if (itemsToScroll == 0) return
        val prefetchList = when {
            itemsToScroll > 0 -> coercedRange(
                currentlyVisibleItemRange.last,
                currentlyVisibleItemRange.last + itemsToScroll
            )
            else -> coercedRange(
                currentlyVisibleItemRange.first + itemsToScroll,
                currentlyVisibleItemRange.first
            )
        }
        var prefetchDist = 0
        prefetchList.forEach {
            prefetchDist += orientationHelper.itemSize
            layoutPrefetchRegistry.addPosition(it, prefetchDist)
        }
    }

    override fun requestLayout() {
        currentlyVisibleItemRange = IntRange.EMPTY
        super.requestLayout()
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?
    ) = when {
        isEntireContentInViewPort -> 0    // no recycling will happen
        else -> {
            layoutChildrenInLayout(recycler, state, dx)
            0
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State?
    ) = when {
        isEntireContentInViewPort -> 0 // no recycling will happen
        else -> {
            layoutChildrenInLayout(recycler, state, dy)
            0
        }
    }

    override fun scrollToPosition(position: Int) {
        require(position >= 0)
        recyclerView.stopScroll()
        scrollParentHandler.stopSmoothScroll(scrollParent)
        orientationHelper.apply {
            if (childCount == 0) scrollScrollParentTo(0)
            else {
                val itemPosition =
                    itemSize * position.coerceAtMost(itemCount) + recyclerViewStartPadding
                scrollScrollParentTo(itemPosition)
            }
        }
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        require(position >= 0)
        recyclerView.stopScroll()
        orientationHelper.apply {
            val itemPosition =
                itemSize * position.coerceAtMost(itemCount) + recyclerViewStartPadding
            smoothScrollScrollParentTo(itemPosition)
        }
    }

    override fun computeScrollVectorForPosition(targetPosition: Int) =
        orientationHelper.computeScrollVectorForPosition(targetPosition)

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mRecyclerView = view
        view?.adapter?.registerAdapterDataObserver(adapterMutationTracker)
        if (forceListener) scrollParentHandler.attachScrollListener(this, scrollParent)
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        mRecyclerView = null
        view?.adapter?.unregisterAdapterDataObserver(adapterMutationTracker)
        adapterMutationTracker.clear()
        if (forceListener) scrollParentHandler.detachScrollListener(this, scrollParent)
    }

    override fun onScrollChange(
        v: NestedScrollView?,
        scrollX: Int,
        scrollY: Int,
        oldScrollX: Int,
        oldScrollY: Int
    ) {
        if (!isInNestedScroll) recyclerView.scrollBy(scrollX - oldScrollX, scrollY - oldScrollY)
    }

    /** Call this method during [scrollParent] onScrolled events if you opted out of [forceListener]. */
    fun onScrollTick() {
        if (itemSizes.isEmpty()) return  // no measurement happened, invalid call
        if (!isEntireContentInViewPort)
            layoutChildrenInLayout(reflectRecycler, null, 0)
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

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?
    ) {
        Log.d(TAG, "adapter changed")
        itemSizes.clear()
        currentlyVisibleItemRange = IntRange.EMPTY
        oldAdapter?.unregisterAdapterDataObserver(adapterMutationTracker)
        adapterMutationTracker.clear()
        newAdapter?.registerAdapterDataObserver(adapterMutationTracker)
        removeAllViews()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // relayout everything
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        requestLayout() // relayout everything
    }

    override fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {
        // useless - called post layout
    }

    override fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        // not needed
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

        // item count differs depending on how much of recycler is in visible viewport
        val size = if (recyclerLocation >= 0) {
            parentContentSize - recyclerLocation
        } else {
            orientationHelper.recyclerViewContentSize + recyclerLocation
        }
        return viewCountToFit(min(size, parentContentSize))
    }

    /** Amount of views that need to be laid out to completely fill 1 viewport of [size]. */
    private fun viewCountToFit(size: Int) =
        (size / orientationHelper.itemSize + 1).coerceAtMost(itemCount - 1)

    /** Check if recycler is visible at all. Pass current [recyclerScrollLocation], optionally altered by
     * scroll diff, and freshly calculated [parentContentSize]. */
    private fun isRecyclerViewVisible(
        recyclerScrollLocation: Int,
        parentContentSize: Int
    ) = recyclerScrollLocation < parentContentSize &&
            recyclerScrollLocation > -orientationHelper.recyclerViewContentSize

    // reflect recycler, used when listening to non-nested scroll
    private val reflectRecycler: RecyclerView.Recycler
        get() {
            val f = RecyclerView::class.java.getDeclaredField("mRecycler")
            f.isAccessible = true
            return f.get(recyclerView) as RecyclerView.Recycler
        }

    /** Method implementation that differ depending on selected orientation. */
    private abstract class OrientationHelper : RecyclerView.SmoothScroller.ScrollVectorProvider {
        /** Relevant item size - height vertically, width horizontally */
        abstract val itemSize: Int

        /**
         * Current location of recyclerView relative to rendering area of scroll parent
         * (ignores parents padding if it clips).
         *
         * This value changes as recyclerview or nested parent are scrolled.
         * */
        abstract fun recyclerScrollLocation(): Int

        /**
         * Absolute location of recyclerView in scroll parent.
         *
         * This value does not change as recyclerview or nested parent are scrolled.
         */
        abstract fun recyclerLocationInScrollParent(): Int

        /** How many pixels of content are visible in scrollable direction */
        abstract fun parentContentSize(): Int

        /** Measure the size of recyclerView. */
        abstract fun measure(v0: View, itemCount: Int, widthSpec: Int, heightSpec: Int)

        /** Quickly align views location with its current position. */
        abstract fun revalidateViewPosition(v: View)

        /** Layout a view at given position. */
        abstract fun layoutViewForPosition(v: View, position: Int)

        /** Unspecified layout params.*/
        abstract fun generateDefaultLayoutParams(): RecyclerView.LayoutParams

        /**
         * Scroll the scroll parent to specific x or y relative to [recyclerLocationInScrollParent] -
         * that means if [xy] is `0` recyclerview should become aligned to top/left of it.
         * */
        abstract fun scrollScrollParentTo(xy: Int)

        /**
         * Smooth scroll the scroll parent to specific x or y relative to [recyclerLocationInScrollParent] -
         * that means if [xy] is `0` recyclerview should become aligned to top/left of it.
         * */
        abstract fun smoothScrollScrollParentTo(xy: Int)

        /** Relevant content size (without paddings). */
        abstract val recyclerViewContentSize: Int

        /** Relevant padding before first item. */
        abstract val recyclerViewStartPadding: Int
    }

    private inner class VerticalOrientationHelper : OrientationHelper() {
        override val itemSize
            get() = itemHeight

        override fun recyclerScrollLocation() = recyclerView.nestedScrollTop() + paddingTop
        override fun recyclerLocationInScrollParent() = recyclerView.nestedTop()
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
            val viewTop = topForPosition(position)
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

        override fun revalidateViewPosition(v: View) {
            val targetTop = topForPosition(v.params().absoluteAdapterPosition)
            if (v.top != targetTop) {
                layoutDecoratedWithMargins(
                    v, v.left, targetTop,
                    v.left + v.width,
                    targetTop + v.height
                )
            }
        }

        override fun generateDefaultLayoutParams() = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

        override fun scrollScrollParentTo(xy: Int) {
            scrollParent.scrollTo(scrollParent.scrollX, recyclerLocationInScrollParent() + xy)
        }

        override fun smoothScrollScrollParentTo(xy: Int) {
            scrollParentHandler.smoothScrollTo(
                scrollParent,
                scrollParent.scrollX,
                recyclerLocationInScrollParent() + xy
            )
        }

        override val recyclerViewContentSize: Int
            get() = recyclerView.height - recyclerView.paddingBottom - recyclerView.paddingTop

        override val recyclerViewStartPadding: Int
            get() = recyclerView.paddingTop

        override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
            val itemLocation = itemSize * targetPosition + recyclerViewStartPadding
            val recyclerScrollPosition = recyclerScrollLocation() +
                    scrollParent.takeIfPaddingClips { paddingTop }
            return PointF(0f, (recyclerScrollPosition - itemLocation).sign.toFloat())
        }

        private fun topForPosition(position: Int) = paddingTop + (position * itemHeight)

        /** TOP position relative to nested scroll view parent */
        private tailrec fun View.nestedScrollTop(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> {
                top + current - scrollParent.scrollY - scrollParent.takeIfPaddingClips { paddingTop }
            }
            is ViewGroup -> p.nestedScrollTop(top + current)
            else -> throw IllegalStateException("Invalid scrollParent provided!!")
        }

        private tailrec fun View.nestedTop(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> top + current
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

        override fun recyclerScrollLocation() = recyclerView.nestedScrollLeft() + paddingLeft
        override fun recyclerLocationInScrollParent() = recyclerView.nestedLeft()
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
            val viewLeft = leftForPosition(position)
            val itemHeight = this@NestedWrapLayoutManager.itemHeight.takeIf { it > 0 }
                ?: heightNoPadding()
            // layout the view
            layoutDecoratedWithMargins(
                v, viewLeft, viewTop,
                viewLeft + itemWidth,
                viewTop + itemHeight
            )
        }

        override fun revalidateViewPosition(v: View) {
            val targetLeft = leftForPosition(v.params().absoluteAdapterPosition)
            if (v.left != targetLeft) {
                layoutDecoratedWithMargins(
                    v, targetLeft, v.top,
                    targetLeft + v.width,
                    v.top + v.height
                )
            }
        }

        override fun generateDefaultLayoutParams() = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.MATCH_PARENT
        )

        override fun scrollScrollParentTo(xy: Int) {
            scrollParent.scrollTo(recyclerLocationInScrollParent() + xy, scrollParent.scrollY)
        }

        override fun smoothScrollScrollParentTo(xy: Int) {
            scrollParentHandler.smoothScrollTo(
                scrollParent,
                recyclerLocationInScrollParent() + xy,
                scrollParent.scrollY
            )
        }

        override val recyclerViewContentSize: Int
            get() = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight

        override val recyclerViewStartPadding: Int
            get() = recyclerView.paddingLeft

        override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
            val itemLocation = itemSize * targetPosition + recyclerViewStartPadding
            val recyclerScrollPosition = recyclerScrollLocation() +
                    scrollParent.takeIfPaddingClips { paddingLeft }
            return PointF((recyclerScrollPosition - itemLocation).sign.toFloat(), 0f)
        }

        private fun leftForPosition(position: Int) = paddingLeft + position * itemWidth

        /** LEFT position relative to nested scroll view parent */
        private tailrec fun View.nestedScrollLeft(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> {
                left + current - scrollParent.scrollX - scrollParent.takeIfPaddingClips { paddingLeft }
            }
            is ViewGroup -> p.nestedScrollLeft(left + current)
            else -> throw IllegalStateException("Invalid scrollParent provided!!")
        }

        private tailrec fun View.nestedLeft(current: Int = 0): Int = when (val p = parent) {
            scrollParent -> left + current
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

    /** Proxy handling compatibility cases. */
    open class ScrollParentHandler {
        /** Attached scroll listener - used only for unspecified scroll views on low apis. */
        var legacyScrollListener: ViewTreeObserver.OnScrollChangedListener? = null

        open fun attachScrollListener(lm: NestedWrapLayoutManager, scrollParent: ViewGroup) {
            when {
                scrollParent is NestedScrollView -> scrollParent.setOnScrollChangeListener(lm)
                Build.VERSION.SDK_INT >= 23 -> scrollParent.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                    lm.onScrollTick()
                }
                else -> {
                    // low api fallback: flickering might happen because observer is less precise
                    val scrollObs = ViewTreeObserver.OnScrollChangedListener {
                        lm.onScrollTick()
                    }
                    scrollParent.viewTreeObserver.addOnScrollChangedListener(scrollObs)
                    legacyScrollListener = scrollObs
                }
            }
        }

        open fun detachScrollListener(lm: NestedWrapLayoutManager, scrollParent: ViewGroup) {
            when {
                scrollParent is NestedScrollView -> scrollParent.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
                Build.VERSION.SDK_INT >= 23 -> scrollParent.setOnScrollChangeListener(null)
                else -> {
                    scrollParent.viewTreeObserver?.removeOnScrollChangedListener(
                        legacyScrollListener
                    )
                    legacyScrollListener = null
                }
            }
        }

        open fun smoothScrollTo(scrollParent: ViewGroup, x: Int, y: Int) {
            when {
                scrollParent is NestedScrollView -> scrollParent.smoothScrollTo(x, y)
                scrollParent is ScrollView -> scrollParent.smoothScrollTo(x, y)
                scrollParent is HorizontalScrollView -> scrollParent.smoothScrollTo(x, y)
                else -> Log.e(
                    TAG,
                    "ScrollParentHandler.smoothScrollTo: no smooth scroll method in ${scrollParent::class.java.name}"
                )
            }
        }

        open fun stopSmoothScroll(scrollParent: ViewGroup) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) scrollParent.stopNestedScroll()
            // there isn't universal stop smooth scroll api so stop it by requesting smooth scroll to current location
            smoothScrollTo(scrollParent, scrollParent.scrollX, scrollParent.scrollY)
        }
    }

    private fun View.params() = layoutParams as RecyclerView.LayoutParams

    /** Return result of [padding] if clip to padding is true, otherwise 0. */
    private inline fun ViewGroup.takeIfPaddingClips(padding: View.() -> Int) = run {
        if (!clipToPadding) 0 else padding()
    }

    /** Remaining scroll valid for current orientation. */
    private val RecyclerView.State.remainingScroll
        get() = if (orientation == VERTICAL) remainingScrollVertical else remainingScrollHorizontal

    /** Clamp [value] between -minMax and minMax. */
    private fun absClamp(value: Int, minMax: Int) = when {
        value < -minMax -> -minMax
        value > minMax -> minMax
        else -> value
    }

    /** Make range coerced within [firstLimit] and [lastLimit]. By default use 0 .. [getItemCount] - 1. */
    private fun coercedRange(
        start: Int,
        end: Int,
        firstLimit: Int = 0,
        lastLimit: Int = itemCount - 1
    ) = max(start, firstLimit)..min(lastLimit, end)

    /** Ensure this range is within limits. */
    private fun IntRange.coerce(
        firstLimit: Int = 0,
        lastLimit: Int = itemCount - 1
    ) = coercedRange(first, last, firstLimit, lastLimit)

    /** measured width with margins - if width is 0 then margins are ignored and 0 is returned. */
    private fun View.measuredWidthWithMargins() = when (measuredWidth) {
        0 -> 0
        else -> measuredWidth + marginLeft + marginRight
    }

    /** measured height with margins - if height is 0 then margins are ignored and 0 is returned. */
    private fun View.measuredHeightWithMargins() = when (measuredHeight) {
        0 -> 0
        else -> measuredHeight + marginTop + marginBottom
    }
}