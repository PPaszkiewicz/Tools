package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * ImageView that calculates luminance of displayed bitmap image views.
 *
 * Can be used as a background of layout and have font on top react to luminance changes to modify font color.
 * */
class LuminanceAwareImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var luminanceListener: LuminanceListener? = null

    /** Determined luminance. */
    var luminance = -1.0
        private set

    /** Determined dominant color. If no bitmap drawable was set, this will default
     * to [Color.WHITE]. This value is only valid when [luminance] is not -1. */
    var dominantColor = Color.WHITE
        private set

    /** If raised [invalidateLuminance] will be triggered whenever image drawable is changed.*/
    var invalidateLuminanceOnDrawableChange = false

    /**
     * After calculating luminance, apply extra dim to increase contrast.
     *
     * This will allows black(or white) font to stay readable even if background contains those colors.
     * */
    var isDimEnabled = false

    /** Dim applied on top of dark images. */
    var darkDimColor = Color.parseColor("#55000000")

    /** Dim applied on top of light images. */
    var lightDimColor = Color.parseColor("#55FFFFFF")

    /** Listener for luminance change. -1 means it's undefined. [autoInvalidate] (default: true) to
     * raise [invalidateLuminanceOnDrawableChange]. */
    fun setLuminanceListener(
        luminanceListener: LuminanceListener?,
        autoInvalidate: Boolean = true
    ) {
        this.luminanceListener = luminanceListener
        invalidateLuminanceOnDrawableChange = autoInvalidate
    }

    /** Listener for luminance change. -1 means it's undefined. [autoInvalidate] (default: true) to
     * raise [invalidateLuminanceOnDrawableChange]. */
    inline fun setLuminanceListener(
        crossinline l: (Double) -> Unit,
        autoInvalidate: Boolean = true
    ) = setLuminanceListener(object : LuminanceListener {
        override fun onLuminanceChanged(luminance: Double) = l(luminance)
    }, autoInvalidate)

    /** Changing image drawable invalidates [luminance] and [dominantColor]
     * if [invalidateLuminanceOnDrawableChange] is raised.*/
    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable is BitmapDrawable && invalidateLuminanceOnDrawableChange)
            invalidateLuminance(drawable)
        else {
            luminanceListener?.onLuminanceChanged(-1.0)
            luminance = 1.0
            dominantColor = Color.WHITE
            // colorFilter = null
        }
    }

    private fun invalidateLuminance(bitmapDrawable: BitmapDrawable) {
        // palette calculation is blocking
        val palette = Palette.from(bitmapDrawable.bitmap).generate()
        val dom = palette.getDominantColor(Color.WHITE)
        val l = ColorUtils.calculateLuminance(dom)
        dominantColor = dom
        luminance = l
        luminanceListener?.onLuminanceChanged(l)
        setDim(l)
        invalidate()
    }

    /**
     * Force update of [luminance] and [dominantColor] even when there's no listener. This operation is
     * blocking so it's safe to read those values immediately after this call.
     *
     * Returns **true** if current drawable is a bitmap and update will be deployed.
     * */
    fun invalidateLuminance() = (drawable as? BitmapDrawable)?.let {
        invalidateLuminance(it)
        true
    } ?: false

    /**
     * Force dim using [luminance]. If [isDimEnabled] is false this clears the dim.
     *
     * Makes dark images even darker and light even lighter (increases contrast with text).
     * */
    fun setDim(luminance: Double) {
        if (isDimEnabled) {
            setColorFilter(if (luminance < 0.5) darkDimColor else lightDimColor)
        } else {
            colorFilter = null
        }
    }

    interface LuminanceListener {
        fun onLuminanceChanged(luminance: Double)
    }
}