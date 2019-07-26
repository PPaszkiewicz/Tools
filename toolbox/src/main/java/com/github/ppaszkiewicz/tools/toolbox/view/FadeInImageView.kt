package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView supporting fade-in animation of drawables.
 *
 * Lighter to process than alpha animator of entire view.
 * */
class FadeInImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    companion object {
        const val DEFAULT_FADE_IN_DURATION_MS = 200
    }
    /** Fade duration used when setting drawable. */
    var fadeInDuration = DEFAULT_FADE_IN_DURATION_MS

    private var animationStart = 0L
    var isAnimating = false
        private set(value){
            field = value
            if(value)
                animationStart = System.currentTimeMillis()
        }

    /**
     * Prevent/interrupt fade in animation if [clear].
     *
     * Otherwise restart animation.
     * */
    fun clearFadeInAnimation(clear: Boolean = true) : FadeInImageView {
        isAnimating = !clear
        return this
    }

    // always fades in
    override fun setImageDrawable(drawable: Drawable?) {
        setImageDrawable(drawable, true)
    }

    /** Set image drawable with controlled fade. */
    fun setImageDrawable(drawable: Drawable?, fadeIn : Boolean) {
        super.setImageDrawable(drawable)
        isAnimating = fadeIn && drawable != null
    }

    /** Set image bitmap with controlled fade. */
    fun setImageBitmap(bitmap: Bitmap?, fadeIn : Boolean) {
        super.setImageBitmap(bitmap)
        isAnimating = fadeIn && bitmap != null
    }


    override fun draw(canvas: Canvas) {
        if(!isAnimating) {
            super.draw(canvas)
            return
        }
        val n = (System.currentTimeMillis() - animationStart).toFloat() / fadeInDuration
        if(n >= 1f){
            //stop animation
            isAnimating = false
            super.draw(canvas)
        }else{
            // only animate alpha of the drawable
            val d = drawable
            val animAlpha = (n * 0xFF).toInt()
            d?.alpha = animAlpha
           // Log.d("fimg","$n ${d?.alpha}")
            super.draw(canvas)
            d?.alpha = 0xFF
        }
    }
}