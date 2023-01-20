package com.github.ppaszkiewicz.tools.toolbox.view.orientation

import android.view.ViewGroup

/**
 * Uses provided [OrientationCompass] to give [src] object properties fixed directions.
 */
interface OrientationGuide<T, H : OrientationCompass> {
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

    /** Object this guide is working on. */
    val src: T

    /** [OrientationCompass] used by this guide. */
    val compass: H

    /** Contains interface definitions and combinations. */
    interface Base {
        interface ImmutableSize<T, H : OrientationCompass> : OrientationGuide<T, H> {
            /** Main size: width for horizontal, height for vertical */
            val size: Int

            /** Alt size: height for horizontal, width for vertical */
            val altSize: Int
        }

        interface MutableSize<T, H : OrientationCompass> : ImmutableSize<T, H> {
            override var size: Int
            override var altSize: Int
            fun set(size: Int = this.size, altSize: Int = this.altSize)
        }

        interface CoordinatedSize<T, H : OrientationCompass> : ImmutableSize<T, H> {
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
        interface ImmutablePaddedSize<T, H : OrientationCompass> : ImmutableSize<T, H>, Padding
        interface CoordinatedPaddedSize<T, H : OrientationCompass> : CoordinatedSize<T, H>, Padding
        interface MutableMarginSize<T, H : OrientationCompass> : MutableSize<T, H>, MutableMargin
    }

    interface Point : OrientationGuide<android.graphics.Point, OrientationCompass> {
        val pos: Int
            get() = compass.posOf(src)
        val altPos: Int
            get() = compass.altPosOf(src)

        fun set(pos: Int = this.pos, altPos: Int = this.altPos) = compass.set(src, pos, altPos)
        fun offset(dir: Int = 0, altDir: Int = 0) = compass.set(src, pos, altPos)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Point) : Point {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Point) : Point {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }

    interface Size : Base.MutableSize<android.graphics.Point, OrientationCompass> {
        override var size: Int
            get() = compass.posOf(src)
            set(value) = compass.set(src, pos = value)
        override var altSize: Int
            get() = compass.altPosOf(src)
            set(value) = compass.set(src, altPos = value)

        override fun set(size: Int, altSize: Int) = compass.set(src, size, altSize)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Point) : Size {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Point) : Size {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }

    interface Rect : Base.CoordinatedSize<android.graphics.Rect, OrientationCompass> {
        override val size: Int
            get() = compass.sizeOf(src)
        override val altSize: Int
            get() = compass.altSizeOf(src)

        override var start: Int
            get() = compass.startOf(src)
            set(value) = compass.setStartOf(src, value)
        override var end: Int
            get() = compass.endOf(src)
            set(value) = compass.setEndOf(src, value)
        override var altStart: Int
            get() = compass.altStartOf(src)
            set(value) = compass.setAltStartOf(src, value)
        override var altEnd: Int
            get() = compass.altEndOf(src)
            set(value) = compass.setAltEndOf(src, value)

        fun set(
            start: Int = this.start,
            altStart: Int = this.altStart,
            end: Int = this.end,
            altEnd: Int = this.altEnd
        ) = compass.set(src, start, altStart, end, altEnd)

        fun offset(dir: Int = 0, altDir: Int = 0) = compass.offset(src, dir, altDir)

        @JvmInline
        value class Horizontal(override val src: android.graphics.Rect) : Rect {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.graphics.Rect) : Rect {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }

    interface View : Base.CoordinatedPaddedSize<android.view.View, OrientationCompass>,
        Base.Margin {
        val measuredSize: Int
            get() = compass.measuredSizeOf(src)
        val measuredAltSize: Int
            get() = compass.measuredAltSizeOf(src)

        val minimumSize: Int
            get() = compass.minimumSizeOf(src)
        val minimumAltSize: Int
            get() = compass.minimumAltSizeOf(src)

        val scroll: Int
            get() = compass.scrollOf(src)
        val altScroll: Int
            get() = compass.altScrollOf(src)


        override val paddingStart: Int
            get() = compass.paddingStartOf(src)
        override val paddingEnd: Int
            get() = compass.paddingEndOf(src)
        override val paddingAltStart: Int
            get() = compass.paddingAltStartOf(src)
        override val paddingAltEnd: Int
            get() = compass.paddingAltEndOf(src)
        override val marginStart: Int
            get() = compass.marginStartOf(src)
        override val marginEnd: Int
            get() = compass.marginEndOf(src)
        override val marginAltStart: Int
            get() = compass.marginAltStartOf(src)
        override val marginAltEnd: Int
            get() = compass.marginAltEndOf(src)
        override val size: Int
            get() = compass.sizeOf(src)
        override val altSize: Int
            get() = compass.altSizeOf(src)
        override val start: Int
            get() = compass.startOf(src)
        override val end: Int
            get() = compass.endOf(src)
        override val altStart: Int
            get() = compass.altStartOf(src)
        override val altEnd: Int
            get() = compass.altEndOf(src)


        @JvmInline
        value class Horizontal(override val src: android.view.View) : View {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: android.view.View) : View {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }

    // almost all layout params support margins so there's no support for non-margin ones
    interface LayoutParams :
        Base.MutableMarginSize<ViewGroup.MarginLayoutParams, OrientationCompass> {
        override var size: Int
            get() = compass.sizeOf(src)
            set(value) = compass.setSizeOf(src, value)
        override var altSize: Int
            get() = compass.altSizeOf(src)
            set(value) = compass.setSizeOf(src, value)

        override fun set(size: Int, altSize: Int) =
            compass.set(src, size, altSize)

        override var marginStart: Int
            get() = compass.marginStartOf(src)
            set(value) = compass.setMarginStartOf(src, value)
        override var marginEnd: Int
            get() = compass.marginEndOf(src)
            set(value) = compass.setMarginEndOf(src, value)
        override var marginAltStart: Int
            get() = compass.marginAltStartOf(src)
            set(value) = compass.setMarginAltStartOf(src, value)
        override var marginAltEnd: Int
            get() = compass.marginAltEndOf(src)
            set(value) = compass.setMarginAltEndOf(src, value)

        override fun updateMargin(start: Int, altStart: Int, end: Int, altEnd: Int) =
            compass.updateMarginOf(src, start, altStart, end, altEnd)

        @JvmInline
        value class Horizontal(override val src: ViewGroup.MarginLayoutParams) : LayoutParams {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: ViewGroup.MarginLayoutParams) : LayoutParams {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }

    interface Gravity : OrientationGuide<Int, OrientationCompass> {
        val isStart: Boolean
            get() = compass.isStart(src)
        val isEnd: Boolean
            get() = compass.isEnd(src)
        val isCenter: Boolean
            get() = compass.isCenter(src)
        val hasCenter : Boolean
            get() = compass.hasCenter(src)
        val isFill: Boolean
            get() = compass.isFill(src)
        val hasFill: Boolean
            get() = compass.hasFill(src)

        val isClip: Boolean
            get() = compass.isClip(src)

        val isAltStart: Boolean
            get() = compass.isAltStart(src)
        val isAltEnd: Boolean
            get() = compass.isAltEnd(src)
        val isAltCenter: Boolean
            get() = compass.isAltCenter(src)
        val hasAltCenter: Boolean
            get() = compass.hasAltCenter(src)
        val isAltFill: Boolean
            get() = compass.isAltFill(src)
        val hasAltFill: Boolean
            get() = compass.hasAltFill(src)
        val isAltClip: Boolean
            get() = compass.isAltClip(src)

        /** Centered in both directions. */
        val isCenterBoth
            get() = compass.isCenterBoth(src)


        @JvmInline
        value class Horizontal(override val src: Int) : Gravity {
            override val compass: OrientationCompass
                get() = OrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: Int) : Gravity {
            override val compass: OrientationCompass
                get() = OrientationCompass.Vertical
        }
    }
}







