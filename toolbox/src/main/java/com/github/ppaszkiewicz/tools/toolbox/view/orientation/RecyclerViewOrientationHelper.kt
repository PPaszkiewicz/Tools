package com.github.ppaszkiewicz.tools.toolbox.view.orientation

import androidx.recyclerview.widget.RecyclerView

/**
 * [OrientationHandler] extended to handle [RecyclerView.LayoutManager] properties as well.
 * */
interface RecyclerViewOrientationHandler : OrientationHandler {
    fun helperFor(layoutManager: RecyclerView.LayoutManager): RecyclerViewOrientationHelper.LayoutManager
    fun sizeOf(layoutManager: RecyclerView.LayoutManager): Int
    fun altSizeOf(layoutManager: RecyclerView.LayoutManager): Int
    fun paddingStartOf(layoutManager: RecyclerView.LayoutManager): Int
    fun paddingEndOf(layoutManager: RecyclerView.LayoutManager): Int
    fun paddingAltStartOf(layoutManager: RecyclerView.LayoutManager): Int
    fun paddingAltEndOf(layoutManager: RecyclerView.LayoutManager): Int
    fun minimumSizeOf(layoutManager: RecyclerView.LayoutManager): Int
    fun minimumAltSizeOf(layoutManager: RecyclerView.LayoutManager): Int
    fun canScroll(layoutManager: RecyclerView.LayoutManager): Boolean
    fun canScrollAlt(layoutManager: RecyclerView.LayoutManager): Boolean

    open class Horizontal protected constructor() : OrientationHandler.Horizontal(),
        RecyclerViewOrientationHandler {
        /** Default instance for horizontal orientation. */
        companion object Default : Horizontal()

        override fun helperFor(layoutManager: RecyclerView.LayoutManager) =
            RecyclerViewOrientationHelper.horizontal(layoutManager)

        override fun sizeOf(layoutManager: RecyclerView.LayoutManager) = layoutManager.width
        override fun altSizeOf(layoutManager: RecyclerView.LayoutManager) = layoutManager.height
        override fun paddingStartOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingStart

        override fun paddingEndOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingEnd

        override fun paddingAltStartOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingTop

        override fun paddingAltEndOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingBottom

        override fun minimumSizeOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.minimumWidth

        override fun minimumAltSizeOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.minimumHeight

        override fun canScroll(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.canScrollHorizontally()

        override fun canScrollAlt(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.canScrollVertically()
    }

    open class Vertical protected constructor() : OrientationHandler.Vertical(),
        RecyclerViewOrientationHandler {
        /** Default instance for vertical orientation. */
        companion object Default : Vertical()

        override fun helperFor(layoutManager: RecyclerView.LayoutManager) =
            RecyclerViewOrientationHelper.vertical(layoutManager)

        override fun sizeOf(layoutManager: RecyclerView.LayoutManager) = layoutManager.height
        override fun altSizeOf(layoutManager: RecyclerView.LayoutManager) = layoutManager.width
        override fun paddingStartOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingTop

        override fun paddingEndOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingBottom

        override fun paddingAltStartOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingStart

        override fun paddingAltEndOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.paddingEnd

        override fun minimumSizeOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.minimumHeight

        override fun minimumAltSizeOf(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.minimumWidth

        override fun canScroll(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.canScrollVertically()

        override fun canScrollAlt(layoutManager: RecyclerView.LayoutManager) =
            layoutManager.canScrollHorizontally()
    }
}

/**
 * Orientation helpers that wrap calls to [RecyclerViewOrientationHandler] of a single object as properties.
 */
interface RecyclerViewOrientationHelper {
    companion object {
        fun horizontal(layoutManager: RecyclerView.LayoutManager) =
            LayoutManager.Horizontal(layoutManager)

        fun vertical(layoutManager: RecyclerView.LayoutManager) =
            LayoutManager.Vertical(layoutManager)
    }

    interface LayoutManager :
        OrientationHelper.Base.ImmutablePaddedSize<RecyclerView.LayoutManager, RecyclerViewOrientationHandler> {
        val minimumSize: Int
            get() = handler.minimumSizeOf(src)
        val minimumAltSize: Int
            get() = handler.minimumAltSizeOf(src)

        fun canScroll() = handler.canScroll(src)
        fun canScrollAlt() = handler.canScrollAlt(src)

        override val size: Int
            get() = handler.sizeOf(src)
        override val altSize: Int
            get() = handler.altSizeOf(src)
        override val paddingStart: Int
            get() = handler.paddingStartOf(src)
        override val paddingEnd: Int
            get() = handler.paddingEndOf(src)
        override val paddingAltStart: Int
            get() = handler.paddingAltStartOf(src)
        override val paddingAltEnd: Int
            get() = handler.paddingAltEndOf(src)

        @JvmInline
        value class Horizontal(override val src: RecyclerView.LayoutManager) : LayoutManager {
            override val handler: RecyclerViewOrientationHandler
                get() = RecyclerViewOrientationHandler.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: RecyclerView.LayoutManager) : LayoutManager {
            override val handler: RecyclerViewOrientationHandler
                get() = RecyclerViewOrientationHandler.Vertical
        }
    }
}
