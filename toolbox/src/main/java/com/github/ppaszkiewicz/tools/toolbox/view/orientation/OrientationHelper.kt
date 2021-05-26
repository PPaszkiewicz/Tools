package com.github.ppaszkiewicz.tools.toolbox.view.orientation

import android.view.ViewGroup

/**
 * Orientation helpers that wrap calls to [OrientationHandler] of a single [src] object as properties.
 */
interface OrientationHelper<T, H : OrientationHandler> {
    companion object {
        fun horizontal(point: android.graphics.Point) = Point.Horizontal(point)
        fun vertical(point: android.graphics.Point) = Point.Vertical(point)

        fun horizontal(rect: android.graphics.Rect) = Rect.Horizontal(rect)
        fun vertical(rect: android.graphics.Rect) = Rect.Vertical(rect)

        fun horizontal(view: android.view.View) = View.Horizontal(view)
        fun vertical(view: android.view.View) = View.Vertical(view)

        fun horizontal(params: ViewGroup.MarginLayoutParams) = LayoutParams.Horizontal(params)
        fun vertical(params: ViewGroup.MarginLayoutParams) = LayoutParams.Vertical(params)

        fun horizontal(gravity: Int) = Gravity.Horizontal(gravity)
        fun vertical(gravity: Int) = Gravity.Vertical(gravity)

        /** Size is backed by a point rather than [android.util.Size] because it has no min api and is mutable. */
        fun horizontalSize(point: android.graphics.Point) = Size.Horizontal(point)

        /** Size is backed by a point rather than [android.util.Size] because it has no min api and is mutable. */
        fun verticalSize(point: android.graphics.Point) = Size.Vertical(point)
    }

    /** Object this helper is wrapping. */
    val src: T

    /** Orientation handler used by this wrapper. */
    val handler: H

    /** Contains interface definitions and combinations. */
    interface Base {
        interface ImmutableSize<T, H : OrientationHandler> : OrientationHelper<T, H> {
            /** Main size: width for horizontal, height for vertical */
            val size: Int

            /** Alt size: height for horizontal, width for vertical */
            val altSize: Int
        }

        interface MutableSize<T, H : OrientationHandler> : ImmutableSize<T, H> {
            override var size: Int
            override var altSize: Int
            fun set(size: Int = this.size, altSize: Int = this.altSize)
        }

        interface CoordinatedSize<T, H : OrientationHandler> : ImmutableSize<T, H> {
            val start: Int
            val altStart: Int
            val end: Int
            val altEnd: Int
        }

        interface Padding {
            val paddingStart: Int
            val paddingEnd: Int
            val paddingAltStart: Int
            val paddingAltEnd: Int
        }

        interface Margin {
            val marginStart: Int
            val marginEnd: Int
            val marginAltStart: Int
            val marginAltEnd: Int
        }

        interface MutableMargin : Margin {
            override var marginStart: Int
            override var marginEnd: Int
            override var marginAltStart: Int
            override var marginAltEnd: Int

            fun updateMargin(
                start: Int = marginStart,
                altStart: Int = marginAltStart,
                end: Int = marginEnd,
                altEnd: Int = marginAltEnd
            )
        }

        // couple of grouped up bases
        interface ImmutablePaddedSize<T, H : OrientationHandler> : ImmutableSize<T, H>, Padding
        interface CoordinatedPaddedSize<T, H : OrientationHandler> : CoordinatedSize<T, H>, Padding
        interface MutableMarginSize<T, H : OrientationHandler> : MutableSize<T, H>, MutableMargin
    }

    interface Point : OrientationHelper<android.graphics.Point, OrientationHandler> {
        val pos: Int
            get() = handler.posOf(src)
        val altPos: Int
            get() = handler.altPosOf(src)

        fun set(pos: Int = this.pos, altPos: Int = this.altPos) = handler.set(src, pos, altPos)
        fun offset(dir: Int = 0, altDir: Int = 0) = handler.set(src, pos, altPos)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Point) : Point {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Point) : Point {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }

    interface Size : Base.MutableSize<android.graphics.Point, OrientationHandler> {
        override var size: Int
            get() = handler.posOf(src)
            set(value) = handler.set(src, pos = value)
        override var altSize: Int
            get() = handler.altPosOf(src)
            set(value) = handler.set(src, altPos = value)

        override fun set(size: Int, altSize: Int) = handler.set(src, size, altSize)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Point) : Size {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Point) : Size {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }

    interface Rect : Base.CoordinatedSize<android.graphics.Rect, OrientationHandler> {
        override val size: Int
            get() = handler.sizeOf(src)
        override val altSize: Int
            get() = handler.altSizeOf(src)

        override var start: Int
            get() = handler.startOf(src)
            set(value) = handler.setStartOf(src, value)
        override var end: Int
            get() = handler.endOf(src)
            set(value) = handler.setEndOf(src, value)
        override var altStart: Int
            get() = handler.altStartOf(src)
            set(value) = handler.setAltStartOf(src, value)
        override var altEnd: Int
            get() = handler.altEndOf(src)
            set(value) = handler.setAltEndOf(src, value)

        fun set(
            start: Int = this.start,
            altStart: Int = this.altStart,
            end: Int = this.end,
            altEnd: Int = this.altEnd
        ) = handler.set(src, start, altStart, end, altEnd)

        fun offset(dir: Int = 0, altDir: Int = 0) = handler.offset(src, dir, altDir)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Rect) : Rect {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Rect) : Rect {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }

    interface View : Base.CoordinatedPaddedSize<android.view.View, OrientationHandler>,
        Base.Margin {
        val measuredSize: Int
            get() = handler.measuredSizeOf(src)
        val measuredAltSize: Int
            get() = handler.measuredAltSizeOf(src)

        val minimumSize: Int
            get() = handler.minimumSizeOf(src)
        val minimumAltSize: Int
            get() = handler.minimumAltSizeOf(src)

        val scroll: Int
            get() = handler.scrollOf(src)
        val altScroll: Int
            get() = handler.altScrollOf(src)


        override val paddingStart: Int
            get() = handler.paddingStartOf(src)
        override val paddingEnd: Int
            get() = handler.paddingEndOf(src)
        override val paddingAltStart: Int
            get() = handler.paddingAltStartOf(src)
        override val paddingAltEnd: Int
            get() = handler.paddingAltEndOf(src)
        override val marginStart: Int
            get() = handler.marginStartOf(src)
        override val marginEnd: Int
            get() = handler.marginEndOf(src)
        override val marginAltStart: Int
            get() = handler.marginAltStartOf(src)
        override val marginAltEnd: Int
            get() = handler.marginAltEndOf(src)
        override val size: Int
            get() = handler.sizeOf(src)
        override val altSize: Int
            get() = handler.altSizeOf(src)
        override val start: Int
            get() = handler.startOf(src)
        override val end: Int
            get() = handler.endOf(src)
        override val altStart: Int
            get() = handler.altStartOf(src)
        override val altEnd: Int
            get() = handler.altEndOf(src)


        @JvmInline
        value class Horizontal(override val src: android.view.View) : View {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.view.View) : View {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }

    // almost all layout params support margins so there's no support for non-margin ones
    interface LayoutParams :
        Base.MutableMarginSize<ViewGroup.MarginLayoutParams, OrientationHandler> {
        override var size: Int
            get() = handler.sizeOf(src)
            set(value) = handler.setSizeOf(src, value)
        override var altSize: Int
            get() = handler.altSizeOf(src)
            set(value) = handler.setSizeOf(src, value)

        override fun set(size: Int, altSize: Int) =
            handler.set(src, size, altSize)

        override var marginStart: Int
            get() = handler.marginStartOf(src)
            set(value) = handler.setMarginStartOf(src, value)
        override var marginEnd: Int
            get() = handler.marginEndOf(src)
            set(value) = handler.setMarginEndOf(src, value)
        override var marginAltStart: Int
            get() = handler.marginAltStartOf(src)
            set(value) = handler.setMarginAltStartOf(src, value)
        override var marginAltEnd: Int
            get() = handler.marginAltEndOf(src)
            set(value) = handler.setMarginAltEndOf(src, value)

        override fun updateMargin(start: Int, altStart: Int, end: Int, altEnd: Int) =
            handler.updateMarginOf(src, start, altStart, end, altEnd)

        @JvmInline
        value class Horizontal(override val src: ViewGroup.MarginLayoutParams) : LayoutParams {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: ViewGroup.MarginLayoutParams) : LayoutParams {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }

    interface Gravity : OrientationHelper<Int, OrientationHandler> {
        val isStart: Boolean
            get() = handler.isStart(src)
        val isEnd: Boolean
            get() = handler.isEnd(src)
        val isCenter: Boolean
            get() = handler.isCenter(src)
        val hasCenter : Boolean
            get() = handler.hasCenter(src)
        val isFill: Boolean
            get() = handler.isFill(src)
        val hasFill: Boolean
            get() = handler.hasFill(src)

        val isClip: Boolean
            get() = handler.isClip(src)

        val isAltStart: Boolean
            get() = handler.isAltStart(src)
        val isAltEnd: Boolean
            get() = handler.isAltEnd(src)
        val isAltCenter: Boolean
            get() = handler.isAltCenter(src)
        val hasAltCenter: Boolean
            get() = handler.hasAltCenter(src)
        val isAltFill: Boolean
            get() = handler.isAltFill(src)
        val hasAltFill: Boolean
            get() = handler.hasAltFill(src)
        val isAltClip: Boolean
            get() = handler.isAltClip(src)

        /** Centered in both directions. */
        val isCenterBoth
            get() = handler.isCenterBoth(src)


        @JvmInline
        value class Horizontal(override val src: Int) : Gravity {
            override val handler: OrientationHandler
                get() = OrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: Int) : Gravity {
            override val handler: OrientationHandler
                get() = OrientationHandler.Vertical
        }
    }
}







