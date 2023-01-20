package com.github.ppaszkiewicz.tools.toolbox.view.orientation

import androidx.recyclerview.widget.RecyclerView

/**
 * [OrientationCompass] extended to handle [RecyclerView.LayoutManager] properties as well.
 * */
interface RecyclerViewOrientationCompass : OrientationCompass {
    fun guide(layoutManager: RecyclerView.LayoutManager): RecyclerViewOrientationGuide.LayoutManager
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

    open class Horizontal protected constructor() : OrientationCompass.Horizontal(),
        RecyclerViewOrientationCompass {
        /** Default instance for horizontal orientation. */
        companion object Default : Horizontal()

        override fun guide(layoutManager: RecyclerView.LayoutManager) =
            RecyclerViewOrientationGuide.horizontal(layoutManager)

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

    open class Vertical protected constructor() : OrientationCompass.Vertical(),
        RecyclerViewOrientationCompass {
        /** Default instance for vertical orientation. */
        companion object Default : Vertical()

        override fun guide(layoutManager: RecyclerView.LayoutManager) =
            RecyclerViewOrientationGuide.vertical(layoutManager)

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
 * Contains [LayoutManager] guide.
 */
interface RecyclerViewOrientationGuide {
    companion object {
        fun horizontal(layoutManager: RecyclerView.LayoutManager) =
            LayoutManager.Horizontal(layoutManager)

        fun vertical(layoutManager: RecyclerView.LayoutManager) =
            LayoutManager.Vertical(layoutManager)
    }

    /**
     * Uses provided [RecyclerViewOrientationCompass] to give [src] object properties fixed directions.
     */
    interface LayoutManager :
        OrientationGuide.Base.ImmutablePaddedSize<RecyclerView.LayoutManager, RecyclerViewOrientationCompass> {
        val minimumSize: Int
            get() = compass.minimumSizeOf(src)
        val minimumAltSize: Int
            get() = compass.minimumAltSizeOf(src)

        fun canScroll() = compass.canScroll(src)
        fun canScrollAlt() = compass.canScrollAlt(src)

        override val size: Int
            get() = compass.sizeOf(src)
        override val altSize: Int
            get() = compass.altSizeOf(src)
        override val paddingStart: Int
            get() = compass.paddingStartOf(src)
        override val paddingEnd: Int
            get() = compass.paddingEndOf(src)
        override val paddingAltStart: Int
            get() = compass.paddingAltStartOf(src)
        override val paddingAltEnd: Int
            get() = compass.paddingAltEndOf(src)

        @JvmInline
        value class Horizontal(override val src: RecyclerView.LayoutManager) : LayoutManager {
            override val compass: RecyclerViewOrientationCompass
                get() = RecyclerViewOrientationCompass.Horizontal
        }

        @JvmInline
        value class Vertical(override val src: RecyclerView.LayoutManager) : LayoutManager {
            override val compass: RecyclerViewOrientationCompass
                get() = RecyclerViewOrientationCompass.Vertical
        }
    }
}
