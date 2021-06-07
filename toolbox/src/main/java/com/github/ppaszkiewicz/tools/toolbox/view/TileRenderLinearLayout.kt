package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import com.github.ppaszkiewicz.tools.toolbox.R
import com.github.ppaszkiewicz.tools.toolbox.view.orientation.OrientationHandler
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
    private val renderer = Renderer()

    private val mContentRect = Rect() // bounds of all children (with margins)
    private val mDrawRect = Rect() // target rect that is being drawn

    private var currentRenders = 0
    private var drawLimit = 0
    private var drawLimitAlt = 0

    private var maxDraws = 0

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

    /**
     * Alter internal draw to render views on bitmap and "stamp" over the canvas. This prevents
     * multiple calls to childrens `onDraw` but it does not support shadows and will have
     * compatibility issues with outline clipping.
     * */
    var useBitmapRendering = false
        set(value) {
            if (field && !value) renderer.recycleBitmap()
            field = value
        }

    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        attrs?.let { attrz ->
            context.obtainStyledAttributes(attrz, R.styleable.TileRenderLinearLayout).use {
                maxDrawCount = it.getInt(R.styleable.TileRenderLinearLayout_maxDrawCount, DRAW_FILL)
                minDrawCount = it.getFloat(R.styleable.TileRenderLinearLayout_minDrawCount, 1f)
                spanCount = it.getInt(R.styleable.TileRenderLinearLayout_drawSpanCount, 1)
                useBitmapRendering =
                    it.getBoolean(R.styleable.TileRenderLinearLayout_useBitmapRendering, false)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // fast exit case when there's 1 or less items to draw
        if (childCount == 0 || (maxDrawCount == 1 && spanCount == 1)) {
            super.dispatchDraw(canvas)
            return
        }

        // prepare renderer: measure children area into mContentRect and spawn a backing bitmap if needed
        renderer.prepareDraw()
        if (mContentRect.isEmpty) {
            super.dispatchDraw(canvas) // there's nothing to draw so invoke super impl and exit
            return
        }

        // tile out the drawing
        canvas.withSave {
            if (clipToPadding) {
                canvas.clipRect(
                    paddingLeft,
                    paddingTop,
                    this@TileRenderLinearLayout.width - paddingRight,
                    this@TileRenderLinearLayout.height - paddingBottom
                )
            }
            drawSpans(canvas)
        }
        if (maxDraws >= 200) {
            Log.e("TRLL", "too many draws executed, drawing interrupted!")
        }
    }

    // do super draw on canvas
    private fun superDispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
    }

    private fun drawSpans(canvas: Canvas) {
        maxDraws =
            0 // this is just a fallback to prevent infinite draws in case of a measurement error
        orientHelper.apply {
            // determine drawing limits (essentially content width/height to fill in)
            drawLimit = size - paddingStart - if (clipToPadding) paddingEnd else 0
            drawLimitAlt = altSize - paddingAltStart - if (clipToPadding) paddingAltEnd else 0
            // set rectangle to track where drawing has to occur and if it's already out of viewport
            mDrawRect.set(0, 0, mContentRect.width(), mContentRect.height())

            if (spanCount == 1) {
                drawSpan(canvas)
            } else {
                // respect requested span count as long as it fits
                if (spanCount > 0) {
                    drawLimitAlt = (contentRect.altSize * spanCount).coerceAtMost(drawLimitAlt)
                }
                while (drawRect.altStart < drawLimitAlt) {
                    if (spanCount == DRAW_FILL_FULL_ONLY && drawRect.altEnd > drawLimitAlt) {
                        return // early exit - if configured to not clip last span
                    }
                    drawSpan(canvas)
                    drawRect.offset(-drawRect.start, contentRect.altSize)
                    if (maxDraws > 200) return
                }
            }
        }
    }

    private fun drawSpan(canvas: Canvas) {
        orientHelper.apply {
            currentRenders = 0
            while (drawRect.start < drawLimit && checkRenderCount()) {
                if (maxDrawCount == DRAW_FILL_FULL_ONLY && drawRect.end > drawLimit) {
                    return // early exit - if configured to not clip last copy
                }
                canvas.withTranslation(mDrawRect.left.toFloat(), mDrawRect.top.toFloat()) {
                    renderer.render(canvas)
                }
                drawRect.offset(dir = contentRect.size)
                currentRenders++
                maxDraws++
                if (maxDraws > 200) return
            }
        }
    }

    private fun checkRenderCount() = when {
        maxDrawCount > 0 -> currentRenders < maxDrawCount
        else -> true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer.recycleBitmap()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (visibility != View.VISIBLE) {
            renderer.recycleBitmap()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // we might be altering the size
        if (minDrawCount > 1f || spanCount > 1) {
            remeasureToFit(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun setOrientation(orientation: Int) {
        _orientHelper = null
        super.setOrientation(orientation)
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

    private inner class Renderer {
        private var bitmap: Bitmap? = null
        private var bmpCanvas: Canvas? = null


        fun render(canvas: Canvas) {
            bitmap?.let {
                // bitmap rendering needs extra offset
                canvas.withTranslation(paddingLeft.toFloat(), paddingTop.toFloat()) {
                    canvas.drawBitmap(it, 0f, 0f, null)
                }
            } ?: run {
                // otherwise just dispatch draw of children
                superDispatchDraw(canvas)
            }
        }

        fun prepareDraw() {
            orientHelper.apply {
                contentRect.set(
                    start = paddingStart,
                    altStart = paddingAltStart,
                    end = children.last()
                        .let { handler.endOf(it) + handler.marginEndOf(it) },
                    altEnd = children.maxOf { handler.altEndOf(it) + handler.marginAltEndOf(it) }
                )
            }
            if (useBitmapRendering) {
                prepareBitmap()
                bmpCanvas!!.withTranslation(-paddingLeft.toFloat(), -paddingTop.toFloat()) {
                    superDispatchDraw(bmpCanvas!!)
                }
            } else recycleBitmap()
        }

        private fun prepareBitmap() {
            val oldBmp = bitmap
            when {
                // new bitmap needed
                oldBmp == null || !bitmapFitsContentRect(oldBmp) -> {
                    createBitmap(mContentRect.width(), mContentRect.height())
                }
                // just clear bitmap for drawing
                else -> oldBmp.apply { eraseColor(Color.TRANSPARENT) }
            }
        }

        private fun bitmapFitsContentRect(bmp: Bitmap) =
            bmp.width == mContentRect.width() && bmp.height == mContentRect.height()

        private fun createBitmap(w: Int, h: Int) {
            recycleBitmap()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                bmpCanvas = Canvas(it)
            }
        }

        fun recycleBitmap() {
            bmpCanvas = null
            bitmap?.recycle()
            bitmap = null
        }
    }

    private abstract inner class OrientHelper(
        override val handler: OrientationHandler
    ) : OrientationHelper.View {
        override val src = this@TileRenderLinearLayout
        val contentRect: OrientationHelper.Rect
        val drawRect: OrientationHelper.Rect
        val point: OrientationHelper.Point

        init {
            @Suppress("LeakingThis")
            handler.run {
                contentRect = helperFor(mContentRect)
                drawRect = helperFor(mDrawRect)
                point = helperFor(mPoint)
            }
        }

//        abstract fun clip(canvas: Canvas, end: Int = size, altEnd: Int = altSize)
//        abstract fun translate(canvas: Canvas, dir: Float = 0f, altDir: Float = 0f)
    }
    private inner class HorizontalOrientHelper : OrientHelper(OrientationHandler.Horizontal) {
//        override fun translate(canvas: Canvas, dir: Float, altDir: Float) {
//            canvas.translate(dir, altDir)
//        }
//
//        override fun clip(canvas: Canvas, end: Int, altEnd: Int) {
//            canvas.clipRect(0, 0, end, altEnd)
//        }
    }

    private inner class VerticalOrientHelper : OrientHelper(OrientationHandler.Vertical) {
//        override fun translate(canvas: Canvas, dir: Float, altDir: Float) {
//            canvas.translate(altDir, dir)
//        }
//
//        override fun clip(canvas: Canvas, end: Int, altEnd: Int) {
//            canvas.clipRect(0, 0, altEnd, end)
//        }
    }
}