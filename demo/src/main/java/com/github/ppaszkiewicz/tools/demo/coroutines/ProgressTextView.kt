package com.github.ppaszkiewicz.tools.demo.coroutines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat

/** TextView with progress background. */
class ProgressTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    companion object{
        const val COLOR_EMPTY = Color.LTGRAY    // doesn't matter because progress is 0
        const val COLOR_READY = Color.GRAY      // doesn't matter because progress is 0
        const val COLOR_PROGRESS = Color.YELLOW
        const val COLOR_COMPLETE = Color.GREEN
        const val COLOR_ERROR = Color.RED
        const val COLOR_CANCELLED = Color.BLUE
        const val COLOR_CANCELLED_ALL = Color.CYAN
    }

    // draw variables
    private val progressRect = Rect()
    private val bgPaint = Paint().apply {
        color = COLOR_EMPTY
    }

    // progress (0.0f - 1.0f)
    private var progressCurrent = 0f

    init {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
        gravity = Gravity.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        progressRect.let {
            it.right = width
            it.bottom = height
            it.top = height - (progressCurrent * height).toInt()
        }
        canvas.drawRect(progressRect, bgPaint)
        super.onDraw(canvas)
    }

    fun setReady(){
        setPaintColor(COLOR_READY)
    }

    fun setProgress(current: Float){
        progressCurrent = current
        setPaintColor(COLOR_PROGRESS)
    }

    fun setComplete(){
        progressCurrent = 1f
        setPaintColor(COLOR_COMPLETE)
    }

    fun setError(){
        setPaintColor(COLOR_ERROR)
    }

    fun setCancelled(){
        setPaintColor(COLOR_CANCELLED)
    }

    fun setCancelledAll(){
        setPaintColor(COLOR_CANCELLED_ALL)
    }

    // modify paint color and redraw
    private fun setPaintColor(color: Int){
        bgPaint.color = color
        invalidate()
    }
}