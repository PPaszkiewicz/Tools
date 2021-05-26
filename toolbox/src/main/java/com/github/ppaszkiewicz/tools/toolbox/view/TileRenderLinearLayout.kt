package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import com.github.ppaszkiewicz.tools.toolbox.R
import com.github.ppaszkiewicz.tools.toolbox.view.orientation.OrientationHelper

/* Requires view.orientation package. */
/**
 * Linear layout that fill itself by tiling out its children (for pre load effect).
 *
 * Children must not be clickable or focusable because extra renders would not be interactive anyway.
 */
class TileRenderLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    // pre allocated variables used while drawing/measuring
    private val mPoint = Point() // throwaway point for size
    private var drawBitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private val srcRect = Rect() // source rect to take from bitmap
    private val drawRect = Rect() // target rect to draw on canvas
    private var currentRenders = 0
    private var renderStart = 0
    private var drawLimit = 0
    private var currentSpanOffset = 0
    private var drawLimitAlt = 0

    private var _orientHelper: OrientHelper? = null
        get() = field ?: when (orientation) {
            HORIZONTAL -> HorizontalOrientHelper()
            VERTICAL -> VerticalOrientHelper()
            else -> throw IllegalStateException()
        }.also { field = it }
    private val orientHelper: OrientHelper
        get() = _orientHelper!!


    companion object {
        /** Keep drawing copies until layout is filled. */
        const val DRAW_FILL = -1

        /** Keep drawing copies until layout is filled with fully rendered copies -
         * don't draw last one if it or its margins do not fit. */
        const val DRAW_FILL_FULL_ONLY = -2

        /** Draw only once (default native implementation) - this will enable shadows. */
        const val DRAW_ONCE = 1
    }

    /**
     * Minimum drawing count - this has to be set before layout phase as it will affect measurement
     * of this view.
     *
     * Note that margin of items IS considered considered its area so when it's non-zero
     * using fractions will have odd results.
     * */
    var minDrawCount: Float = 1f
        set(value) {
            require(value >= 1f)
            field = value
            requestLayout()
        }

    /**
     * Maximum draws in each span.
     * */
    var maxDrawCount = DRAW_FILL
        set(value) {
            require(value > -3 && value != 0)
            field = value
            invalidate()
        }

    /**
     * Amount of spans (rows/columns) to draw.
     *
     * If views height is `wrap_content` this determines minimum amount, for
     * `match_parent` this is maximum.
     * */
    var spanCount = DRAW_ONCE
        set(value) {
            require(value > -3 && value != 0)
            field = value
            requestLayout()
        }

    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        attrs?.let { attrz ->
            context.obtainStyledAttributes(attrz, R.styleable.TileRenderLinearLayout).use {
                maxDrawCount = it.getInt(R.styleable.TileRenderLinearLayout_maxDrawCount, DRAW_FILL)
                minDrawCount = it.getFloat(R.styleable.TileRenderLinearLayout_minDrawCount, 1f)
                spanCount = it.getInt(R.styleable.TileRenderLinearLayout_drawSpanCount, 1)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // fast exit case when there's 1 or less items to draw
        if (childCount == 0 || (maxDrawCount == 1 && spanCount == 1)) {
            super.dispatchDraw(canvas)
            return
        }
        // draw views on a bitmap (excluding padding) so we can "stamp" them all over
        // note: drawing views on bitmap canvas does not generate shadows
        // because default "canvas" is platform subclass that uses some internal trick to queue shadow
        // rendering for later
        val bitmap = getOrPrepareBitmap()
        bmpCanvas!!.withTranslation(-paddingLeft.toFloat(), -paddingTop.toFloat()) {
            super.dispatchDraw(bmpCanvas)
        }

        // tile out our bitmap (reinclude padding)
        canvas.withTranslation(paddingLeft.toFloat(), paddingTop.toFloat()) {
            drawSpans(bitmap, canvas)
        }
    }

    private fun drawSpans(bitmap: Bitmap, canvas: Canvas) {
        // determine drawing limits
        orientHelper.apply {
            renderStart = handler.startOf(children.first()) - paddingStart
            drawLimit = size - paddingStart - if (clipToPadding) paddingEnd else 0
            drawLimitAlt = altSize - paddingAltStart - if (clipToPadding) paddingAltEnd else 0
            if (spanCount == 1) {
                drawSpan(bitmap, canvas, 0)
            } else {
                // respect requested span count as long as it fits
                if (spanCount > 0) {
                    drawLimitAlt = (altSizeOf(bitmap) * spanCount).coerceAtMost(drawLimitAlt)
                }
                currentSpanOffset = 0
                while (currentSpanOffset < drawLimitAlt) {
                    drawSpan(bitmap, canvas, currentSpanOffset)
                    currentSpanOffset += altSizeOf(bitmap)
                }
            }
        }
    }

    private fun drawSpan(bitmap: Bitmap, canvas: Canvas, altOffset: Int) {
        orientHelper.apply {
            currentRenders = 0
            srcRect.set(0, 0, bitmap.width, bitmap.height)
            drawRect.set(0, 0, bitmap.width, bitmap.height)
            drawDst.offset(altDir = altOffset)
            // if this is last span clipping should be applied
            if (drawDst.altEnd > drawLimitAlt) {
                // early exit - if configured to not clip last span
                if (spanCount == DRAW_FILL_FULL_ONLY) return
                val overDraw = drawDst.altEnd - drawLimitAlt
                drawSrc.altEnd = drawSrc.altEnd - overDraw
                drawDst.altEnd = drawDst.altEnd - overDraw
            }

            while (drawDst.start + renderStart < drawLimit && checkRenderCount()) {
                // case for last copy: it doesn't fit so it might be clipped
                if (drawDst.end > drawLimit) {
                    // early exit - if configured to not clip last copy
                    if (maxDrawCount == DRAW_FILL_FULL_ONLY) return
                    val overDraw = drawDst.end - drawLimit
                    drawSrc.end = drawSrc.end - overDraw
                    drawDst.end = drawDst.end - overDraw
                }
                canvas.drawBitmap(bitmap, srcRect, drawRect, null)
                drawDst.offset(drawDst.size, 0)
                currentRenders++
            }
        }
    }

    private fun checkRenderCount() = when {
        maxDrawCount > 0 -> currentRenders < maxDrawCount
        else -> true
    }

    private fun getOrPrepareBitmap(): Bitmap {
        // measure the size of bitmap (enough to fit views and their margins)
        orientHelper.apply {
            point.set(
                children.last().let { handler.endOf(it) + handler.marginEndOf(it) } - paddingStart,
                children.maxOf { handler.altEndOf(it) + handler.marginAltEndOf(it) } - paddingAltStart
            )
        }
        val oldBmp = drawBitmap
        return when {
            // new bitmap needed
            oldBmp == null || oldBmp.width != mPoint.x || oldBmp.height != mPoint.y -> {
                createBitmap(mPoint.x, mPoint.y)
            }
            // just clear bitmap for drawing
            else -> oldBmp.apply { eraseColor(Color.TRANSPARENT) }
        }
    }

    private fun createBitmap(w: Int, h: Int): Bitmap {
        recycleBitmap()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            drawBitmap = it
            bmpCanvas = Canvas(it)
        }
    }

    private fun recycleBitmap() {
        bmpCanvas = null
        drawBitmap?.recycle()
        drawBitmap = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recycleBitmap()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (visibility != View.VISIBLE) {
            recycleBitmap()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // we might be altering the size
        if (minDrawCount > 1f && spanCount > 1) {
            remeasureToFit(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun setOrientation(orientation: Int) {
        super.setOrientation(orientation)
        _orientHelper = null
    }

    private fun remeasureToFit(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // there is no allocation: this returns mPoint
        val specs = orientHelper.handler.getSpecs(widthMeasureSpec, heightMeasureSpec, mPoint)
        orientHelper.apply {
            // see if we need to modify the main size (main layout)
            val mainSpecMode = MeasureSpec.getMode(specs.main)
            val newMainSize = if (mainSpecMode != MeasureSpec.EXACTLY && minDrawCount > 1f) {
                val extraSize = (measuredSize - paddingStart - paddingEnd) * (minDrawCount - 1f)
                if (extraSize > 0f) {
                    val clamped = (measuredSize + extraSize.toInt()).let {
                        if (mainSpecMode == MeasureSpec.AT_MOST) it.coerceAtMost(
                            MeasureSpec.getSize(specs.main)
                        )
                        else it
                    }
                    clamped
                } else measuredSize
            } else measuredSize

            // check alt size (extra spans)
            val altSpecMode = MeasureSpec.getMode(specs.alt)
            val newAltSize = if (altSpecMode != MeasureSpec.EXACTLY && spanCount > 1) {
                val extraSize =
                    (measuredAltSize - paddingAltStart - paddingAltEnd) * (spanCount - 1)
                if (extraSize > 0f) {
                    val clamped = (measuredAltSize + extraSize).let {
                        if (altSpecMode == MeasureSpec.AT_MOST) it.coerceAtMost(
                            MeasureSpec.getSize(specs.alt)
                        )
                        else it
                    }
                    clamped
                } else measuredAltSize
            } else measuredAltSize
            // there is no allocation: this returns mPoint
            // also makes specs invalid bc we override them
            val newDimens = handler.getMeasuredDimens(newMainSize, newAltSize, mPoint)
            setMeasuredDimension(newDimens.width, newDimens.height)
        }
    }

    @Suppress("LeakingThis")
    private abstract inner class OrientHelper(
        val self: OrientationHelper.View
    ) : OrientationHelper.View by self {
        val drawSrc: OrientationHelper.Rect
        val drawDst: OrientationHelper.Rect
        val point: OrientationHelper.Point
        init {
            handler.run {
                drawSrc = helperFor(srcRect)
                drawDst = helperFor(drawRect)
                point = helperFor(mPoint)
            }
        }
        abstract fun altSizeOf(bitmap: Bitmap): Int
    }

    private inner class HorizontalOrientHelper : OrientHelper(
        OrientationHelper.horizontal(this)
    ) {
        override fun altSizeOf(bitmap: Bitmap) = bitmap.height
    }

    private inner class VerticalOrientHelper : OrientHelper(
        OrientationHelper.vertical(this)
    ) {
        override fun altSizeOf(bitmap: Bitmap) = bitmap.width
    }
}