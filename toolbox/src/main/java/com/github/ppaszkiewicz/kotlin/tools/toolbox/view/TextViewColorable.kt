package com.github.ppaszkiewicz.kotlin.tools.toolbox.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView that stores the color so ObjectAnimator can get() it.
 */
class TextViewColorable @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    @ColorInt
    private var textColor: Int = 0

    @Keep
    override fun setTextColor(@ColorInt color: Int) {
        super.setTextColor(color)
        this.textColor = color
    }

    override fun setTextColor(colors: ColorStateList) {
        super.setTextColor(colors)
        textColor = colors.defaultColor
    }

    @Keep
    @ColorInt
    fun getTextColor(): Int {
        return textColor
    }
}