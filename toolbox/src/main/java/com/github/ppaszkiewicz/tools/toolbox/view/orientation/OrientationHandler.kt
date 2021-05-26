package com.github.ppaszkiewicz.tools.toolbox.view.orientation

import android.graphics.Point
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop

/**
 * Orientation handler that contains methods to query target objects for their properties
 * abstracting their orientation:
 *
 * **Horizontal:**
 * - left/right -> start/end
 * - top/bottom -> altStart/altEnd
 * - width -> size
 * - height -> altSize
 *
 * **Vertical:**
 * - top/bottom -> start/end
 * - left/right -> altStart/altEnd
 * - height -> size
 * - width -> altSize
 */
interface OrientationHandler {
    // orientation helper creation
    fun helperFor(point: Point): OrientationHelper.Point
    fun helperFor(rect: Rect): OrientationHelper.Rect
    fun helperFor(view: View): OrientationHelper.View
    fun helperFor(params: ViewGroup.MarginLayoutParams): OrientationHelper.LayoutParams
    fun helperFor(gravity: Int): OrientationHelper.Gravity
    fun helperForSize(point: Point): OrientationHelper.Size

    // point
    fun posOf(point: Point): Int
    fun altPosOf(point: Point): Int
    fun set(point: Point, pos: Int = posOf(point), altPos: Int = altPosOf(point))
    fun offset(point: Point, dir: Int = 0, altDir: Int = 0)

    // rect
    fun sizeOf(rect: Rect): Int
    fun altSizeOf(rect: Rect): Int
    fun startOf(rect: Rect): Int
    fun altStartOf(rect: Rect): Int
    fun endOf(rect: Rect): Int
    fun altEndOf(rect: Rect): Int
    fun setStartOf(rect: Rect, start: Int)
    fun setAltStartOf(rect: Rect, altStart: Int)
    fun setEndOf(rect: Rect, end: Int)
    fun setAltEndOf(rect: Rect, altEnd: Int)
    fun set(
        rect: Rect, start: Int = startOf(rect),
        altStart: Int = altStartOf(rect),
        end: Int = endOf(rect),
        altEnd: Int = altEndOf(rect)
    )

    fun offset(rect: Rect, dir: Int = 0, altDir: Int = 0)

    // view
    fun sizeOf(view: View): Int
    fun altSizeOf(view: View): Int
    fun startOf(view: View): Int
    fun altStartOf(view: View): Int
    fun endOf(view: View): Int
    fun altEndOf(view: View): Int
    fun measuredSizeOf(view: View): Int
    fun measuredAltSizeOf(view: View): Int
    fun minimumSizeOf(view: View): Int
    fun minimumAltSizeOf(view: View): Int
    fun scrollOf(view: View): Int
    fun altScrollOf(view: View): Int
    fun paddingStartOf(view: View): Int
    fun paddingEndOf(view: View): Int
    fun paddingAltStartOf(view: View): Int
    fun paddingAltEndOf(view: View): Int
    fun marginStartOf(view: View): Int
    fun marginEndOf(view: View): Int
    fun marginAltStartOf(view: View): Int
    fun marginAltEndOf(view: View): Int

    fun getSpec(widthMeasureSpec: Int, heightMeasureSpec: Int): Int
    fun getAltSpec(widthMeasureSpec: Int, heightMeasureSpec: Int): Int

    /** Put both specs into [out] point and return it wrapped with an inline [Spec] class. */
    fun getSpecs(widthMeasureSpec: Int, heightMeasureSpec: Int, out: Point): Spec

    /** Get relevant size to feed into [android.view.View.setMeasuredDimension].
     * @param target size relevant for this orientation
     * @param w width to use if it's not the target
     * @param h height to use if it's not the target
     * @param out point to set result into
     * @return [out] wrapped with inline [Size] class
     * */
    fun getMeasuredDimen(target: Int, w: Int, h: Int, out: Point): Size

    /**
     * Transform both sizes to feed into [android.view.View.setMeasuredDimension].
     * @param out point to set results into
     * @return [out] wrapped with inline [Size] class
     * */
    fun getMeasuredDimens(size: Int, altSize: Int, out: Point): Size

    // layout params
    fun sizeOf(params: ViewGroup.LayoutParams): Int
    fun altSizeOf(params: ViewGroup.LayoutParams): Int
    fun marginStartOf(params: ViewGroup.MarginLayoutParams): Int
    fun marginEndOf(params: ViewGroup.MarginLayoutParams): Int
    fun marginAltStartOf(params: ViewGroup.MarginLayoutParams): Int
    fun marginAltEndOf(params: ViewGroup.MarginLayoutParams): Int
    fun setSizeOf(params: ViewGroup.LayoutParams, size: Int)
    fun setAltSizeOf(params: ViewGroup.LayoutParams, altSize: Int)
    fun setMarginStartOf(params: ViewGroup.MarginLayoutParams, start: Int)
    fun setMarginEndOf(params: ViewGroup.MarginLayoutParams, end: Int)
    fun setMarginAltStartOf(params: ViewGroup.MarginLayoutParams, altStart: Int)
    fun setMarginAltEndOf(params: ViewGroup.MarginLayoutParams, altEnd: Int)
    fun set(
        params: ViewGroup.MarginLayoutParams,
        size: Int = sizeOf(params),
        altSize: Int = altSizeOf(params)
    )

    fun updateMarginOf(
        params: ViewGroup.MarginLayoutParams,
        start: Int = marginStartOf(params),
        altStart: Int = marginAltStartOf(params),
        end: Int = marginEndOf(params),
        altEnd: Int = marginAltEndOf(params)
    )

    // gravity
    fun isStart(gravity: Int): Boolean
    fun isEnd(gravity: Int): Boolean
    fun isCenter(gravity: Int): Boolean
    fun hasCenter(gravity: Int) : Boolean
    fun isFill(gravity: Int): Boolean
    fun hasFill(gravity: Int) : Boolean
    fun isClip(gravity: Int): Boolean

    fun isAltStart(gravity: Int): Boolean
    fun isAltEnd(gravity: Int): Boolean
    fun isAltCenter(gravity: Int): Boolean
    fun hasAltCenter(gravity: Int) : Boolean
    fun isAltFill(gravity: Int): Boolean
    fun hasAltFill(gravity: Int) : Boolean
    fun isAltClip(gravity: Int): Boolean
    fun isCenterBoth(gravity: Int) = hasCenter(gravity) && hasAltCenter(gravity)

    // implementations

    open class Horizontal protected constructor() : OrientationHandler {
        /** Default instance for horizontal orientation. */
        companion object Default : Horizontal()

        override fun helperFor(point: Point) = OrientationHelper.horizontal(point)
        override fun helperFor(rect: Rect) = OrientationHelper.horizontal(rect)
        override fun helperFor(view: View) = OrientationHelper.horizontal(view)
        override fun helperFor(params: ViewGroup.MarginLayoutParams) =
            OrientationHelper.horizontal(params)

        override fun helperFor(gravity: Int) = OrientationHelper.horizontal(gravity)
        override fun helperForSize(point: Point) = OrientationHelper.horizontalSize(point)

        override fun posOf(point: Point) = point.x
        override fun altPosOf(point: Point) = point.y
        override fun set(point: Point, pos: Int, altPos: Int) = point.set(pos, altPos)
        override fun set(rect: Rect, start: Int, altStart: Int, end: Int, altEnd: Int) {
            rect.set(start, altStart, end, altEnd)
        }

        override fun set(params: ViewGroup.MarginLayoutParams, size: Int, altSize: Int) {
            params.width = size
            params.height = altSize
        }

        override fun offset(point: Point, dir: Int, altDir: Int) = point.offset(dir, altDir)
        override fun offset(rect: Rect, dir: Int, altDir: Int) = rect.offset(dir, altDir)
        override fun sizeOf(rect: Rect) = rect.width()
        override fun sizeOf(view: View) = view.width
        override fun sizeOf(params: ViewGroup.LayoutParams) = params.width
        override fun altSizeOf(rect: Rect) = rect.height()
        override fun altSizeOf(view: View) = view.height
        override fun altSizeOf(params: ViewGroup.LayoutParams) = params.height
        override fun startOf(rect: Rect) = rect.left
        override fun startOf(view: View) = view.left
        override fun altStartOf(rect: Rect) = rect.top
        override fun altStartOf(view: View) = view.top
        override fun endOf(rect: Rect) = rect.right
        override fun endOf(view: View) = view.right
        override fun altEndOf(rect: Rect) = rect.bottom
        override fun altEndOf(view: View) = view.bottom
        override fun setStartOf(rect: Rect, start: Int) {
            rect.left = start
        }

        override fun setAltStartOf(rect: Rect, altStart: Int) {
            rect.top = altStart
        }

        override fun setEndOf(rect: Rect, end: Int) {
            rect.right = end
        }

        override fun setAltEndOf(rect: Rect, altEnd: Int) {
            rect.bottom = altEnd
        }

        override fun measuredSizeOf(view: View) = view.measuredWidth
        override fun measuredAltSizeOf(view: View) = view.measuredHeight
        override fun minimumSizeOf(view: View) = view.minimumWidth
        override fun minimumAltSizeOf(view: View) = view.minimumHeight
        override fun scrollOf(view: View) = view.scrollX
        override fun altScrollOf(view: View) = view.scrollY
        override fun paddingStartOf(view: View) = view.paddingStart
        override fun paddingEndOf(view: View) = view.paddingEnd
        override fun paddingAltStartOf(view: View) = view.paddingTop
        override fun paddingAltEndOf(view: View) = view.paddingBottom
        override fun marginStartOf(view: View) = view.marginStart
        override fun marginStartOf(params: ViewGroup.MarginLayoutParams) = params.marginStart
        override fun marginEndOf(view: View) = view.marginEnd
        override fun marginEndOf(params: ViewGroup.MarginLayoutParams) = params.marginEnd
        override fun marginAltStartOf(view: View) = view.marginTop
        override fun marginAltStartOf(params: ViewGroup.MarginLayoutParams) = params.topMargin
        override fun marginAltEndOf(view: View) = view.marginBottom
        override fun marginAltEndOf(params: ViewGroup.MarginLayoutParams) = params.bottomMargin
        override fun getSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) = widthMeasureSpec
        override fun getAltSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) = heightMeasureSpec
        override fun getSpecs(widthMeasureSpec: Int, heightMeasureSpec: Int, out: Point) =
            Spec(out, widthMeasureSpec, heightMeasureSpec)

        override fun getMeasuredDimen(target: Int, w: Int, h: Int, out: Point) =
            Size(out, target, h)

        override fun getMeasuredDimens(size: Int, altSize: Int, out: Point) =
            Size(out, size, altSize)

        override fun setSizeOf(params: ViewGroup.LayoutParams, size: Int) {
            params.width = size
        }

        override fun setAltSizeOf(params: ViewGroup.LayoutParams, altSize: Int) {
            params.height = altSize
        }

        override fun setMarginStartOf(params: ViewGroup.MarginLayoutParams, start: Int) {
            params.marginStart = start
        }

        override fun setMarginEndOf(params: ViewGroup.MarginLayoutParams, end: Int) {
            params.marginEnd = end
        }

        override fun setMarginAltStartOf(params: ViewGroup.MarginLayoutParams, altStart: Int) {
            params.topMargin = altStart
        }

        override fun setMarginAltEndOf(params: ViewGroup.MarginLayoutParams, altEnd: Int) {
            params.bottomMargin = altEnd
        }

        override fun updateMarginOf(
            params: ViewGroup.MarginLayoutParams,
            start: Int,
            altStart: Int,
            end: Int,
            altEnd: Int
        ) = params.setMargins(start, altStart, end, altEnd)

        override fun isStart(gravity: Int) = gravity == Gravity.START
        override fun isEnd(gravity: Int) = gravity == Gravity.BOTTOM
        override fun isCenter(gravity: Int) = gravity == Gravity.CENTER_HORIZONTAL
        override fun hasCenter(gravity: Int) = gravity has Gravity.CENTER_HORIZONTAL
        override fun isFill(gravity: Int) = gravity == Gravity.FILL_HORIZONTAL
        override fun hasFill(gravity: Int) = gravity has Gravity.FILL_HORIZONTAL
        override fun isClip(gravity: Int) = gravity == Gravity.CLIP_HORIZONTAL
        override fun isAltStart(gravity: Int) = gravity == Gravity.TOP
        override fun isAltEnd(gravity: Int) = gravity == Gravity.BOTTOM
        override fun isAltCenter(gravity: Int) = gravity == Gravity.CENTER_VERTICAL
        override fun hasAltCenter(gravity: Int) = gravity has Gravity.CENTER_VERTICAL
        override fun isAltFill(gravity: Int) = gravity == Gravity.FILL_VERTICAL
        override fun hasAltFill(gravity: Int) = gravity has Gravity.FILL_VERTICAL
        override fun isAltClip(gravity: Int) = gravity == Gravity.CLIP_VERTICAL
    }

    open class Vertical protected constructor() : OrientationHandler {
        /** Default instance for vertical orientation. */
        companion object Default : Vertical()

        override fun helperFor(point: Point) = OrientationHelper.vertical(point)
        override fun helperFor(rect: Rect) = OrientationHelper.vertical(rect)
        override fun helperFor(view: View) = OrientationHelper.vertical(view)
        override fun helperFor(params: ViewGroup.MarginLayoutParams) =
            OrientationHelper.vertical(params)

        override fun helperFor(gravity: Int) = OrientationHelper.vertical(gravity)
        override fun helperForSize(point: Point) = OrientationHelper.verticalSize(point)

        override fun posOf(point: Point) = point.y
        override fun altPosOf(point: Point) = point.x
        override fun set(point: Point, pos: Int, altPos: Int) = point.set(altPos, pos)
        override fun set(rect: Rect, start: Int, altStart: Int, end: Int, altEnd: Int) {
            rect.set(altStart, start, altEnd, end)
        }

        override fun set(params: ViewGroup.MarginLayoutParams, size: Int, altSize: Int) {
            params.height = size
            params.width = altSize
        }

        override fun offset(point: Point, dir: Int, altDir: Int) = point.offset(altDir, dir)
        override fun offset(rect: Rect, dir: Int, altDir: Int) = rect.offset(altDir, dir)
        override fun sizeOf(rect: Rect) = rect.height()
        override fun sizeOf(view: View) = view.height
        override fun sizeOf(params: ViewGroup.LayoutParams) = params.height
        override fun altSizeOf(rect: Rect) = rect.width()
        override fun altSizeOf(view: View) = view.width
        override fun altSizeOf(params: ViewGroup.LayoutParams) = params.width
        override fun startOf(rect: Rect) = rect.top
        override fun startOf(view: View) = view.top
        override fun altStartOf(rect: Rect) = rect.left
        override fun altStartOf(view: View) = view.left
        override fun endOf(rect: Rect) = rect.bottom
        override fun endOf(view: View) = view.bottom
        override fun altEndOf(rect: Rect) = rect.right
        override fun altEndOf(view: View) = view.right
        override fun setStartOf(rect: Rect, start: Int) {
            rect.top = start
        }

        override fun setAltStartOf(rect: Rect, altStart: Int) {
            rect.left = altStart
        }

        override fun setEndOf(rect: Rect, end: Int) {
            rect.bottom = end
        }

        override fun setAltEndOf(rect: Rect, altEnd: Int) {
            rect.right = altEnd
        }

        override fun measuredSizeOf(view: View) = view.measuredHeight
        override fun measuredAltSizeOf(view: View) = view.measuredWidth
        override fun minimumSizeOf(view: View) = view.minimumHeight
        override fun minimumAltSizeOf(view: View) = view.minimumWidth
        override fun scrollOf(view: View) = view.scrollY
        override fun altScrollOf(view: View) = view.scrollX
        override fun paddingStartOf(view: View) = view.paddingTop
        override fun paddingEndOf(view: View) = view.paddingBottom
        override fun paddingAltStartOf(view: View) = view.paddingStart
        override fun paddingAltEndOf(view: View) = view.paddingEnd
        override fun marginStartOf(view: View) = view.marginTop
        override fun marginStartOf(params: ViewGroup.MarginLayoutParams) = params.topMargin
        override fun marginEndOf(view: View) = view.marginBottom
        override fun marginEndOf(params: ViewGroup.MarginLayoutParams) = params.bottomMargin
        override fun marginAltStartOf(view: View) = view.marginStart
        override fun marginAltStartOf(params: ViewGroup.MarginLayoutParams) = params.marginStart
        override fun marginAltEndOf(view: View) = view.marginEnd
        override fun marginAltEndOf(params: ViewGroup.MarginLayoutParams) = params.marginEnd
        override fun getSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) = heightMeasureSpec
        override fun getAltSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) = widthMeasureSpec
        override fun getSpecs(widthMeasureSpec: Int, heightMeasureSpec: Int, out: Point) =
            Spec(out, heightMeasureSpec, widthMeasureSpec)

        override fun getMeasuredDimen(target: Int, w: Int, h: Int, out: Point) =
            Size(out, w, target)

        override fun getMeasuredDimens(size: Int, altSize: Int, out: Point) =
            Size(out, altSize, size)

        override fun setSizeOf(params: ViewGroup.LayoutParams, size: Int) {
            params.height = size
        }

        override fun setAltSizeOf(params: ViewGroup.LayoutParams, altSize: Int) {
            params.width = altSize
        }

        override fun setMarginStartOf(params: ViewGroup.MarginLayoutParams, start: Int) {
            params.topMargin = start
        }

        override fun setMarginEndOf(params: ViewGroup.MarginLayoutParams, end: Int) {
            params.bottomMargin = end
        }

        override fun setMarginAltStartOf(params: ViewGroup.MarginLayoutParams, altStart: Int) {
            params.marginStart = altStart
        }

        override fun setMarginAltEndOf(params: ViewGroup.MarginLayoutParams, altEnd: Int) {
            params.marginEnd = altEnd
        }

        override fun updateMarginOf(
            params: ViewGroup.MarginLayoutParams,
            start: Int,
            altStart: Int,
            end: Int,
            altEnd: Int
        ) = params.setMargins(altStart, start, altEnd, end)

        override fun isStart(gravity: Int) = gravity == Gravity.TOP
        override fun isEnd(gravity: Int) = gravity == Gravity.BOTTOM
        override fun isCenter(gravity: Int) = gravity == Gravity.CENTER_VERTICAL
        override fun hasCenter(gravity: Int) = gravity has Gravity.CENTER_VERTICAL
        override fun isFill(gravity: Int) = gravity == Gravity.FILL_VERTICAL
        override fun hasFill(gravity: Int) = gravity has Gravity.FILL_VERTICAL
        override fun isClip(gravity: Int) = gravity == Gravity.CLIP_VERTICAL
        override fun isAltStart(gravity: Int) = gravity == Gravity.START
        override fun isAltEnd(gravity: Int) = gravity == Gravity.END
        override fun isAltCenter(gravity: Int) = gravity == Gravity.CENTER_HORIZONTAL
        override fun hasAltCenter(gravity: Int) = gravity has Gravity.CENTER_HORIZONTAL
        override fun isAltFill(gravity: Int) = gravity == Gravity.FILL_HORIZONTAL
        override fun hasAltFill(gravity: Int) = gravity has Gravity.FILL_HORIZONTAL
        override fun isAltClip(gravity: Int) = gravity == Gravity.CLIP_HORIZONTAL
    }

    @JvmInline
    value class Spec(val point: Point) {
        constructor(p: Point, x: Int, y: Int) : this(p.apply { set(x, y) })

        inline val main: Int
            get() = point.x
        inline val alt: Int
            get() = point.y
    }


    @JvmInline
    value class Size(val point: Point) {
        constructor(p: Point, x: Int, y: Int) : this(p.apply { set(x, y) })

        inline val width: Int
            get() = point.x
        inline val height: Int
            get() = point.y
    }
}

// helper function for gravity
internal infix fun Int.has(flag: Int) = (this and flag) == flag