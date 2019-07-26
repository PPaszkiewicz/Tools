package com.github.ppaszkiewicz.kotlin.tools.toolbox.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView that prevents layout requests when text changes.
 * Use some padding and ellipsize = none for most predictable results.
 *
 * By default view will request layout only if text length changes ([ResizePolicy.ON_LENGTH_CHANGED]).
 */
class FixedSizeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var resizePolicy = ResizePolicy.ON_LENGTH_CHANGED
    private var mConsumeLayoutRequest = false
    private var mTextLength = -1


    /**
     * Change view request layout behavior. Default value is [ResizePolicy.ON_LENGTH_CHANGED].
     *
     * @param resizePolicy new resize policy
     */
    fun setResizePolicy(resizePolicy: ResizePolicy) {
        this.resizePolicy = resizePolicy
        mConsumeLayoutRequest = false
        mTextLength = -1

    }

    override fun setText(text: CharSequence, type: BufferType) {
        when (resizePolicy) {
            ResizePolicy.ALWAYS -> mConsumeLayoutRequest = false
            ResizePolicy.ON_LENGTH_CHANGED -> if (text.length == mTextLength) mConsumeLayoutRequest = true
            ResizePolicy.NEVER -> mConsumeLayoutRequest = true
            ResizePolicy.IF_NOT_LAID_OUT -> mConsumeLayoutRequest = width != 0 && height != 0
        }
        mTextLength = text.length
        super.setText(text, type)
    }

    override fun requestLayout() {
        if (mConsumeLayoutRequest) {
            mConsumeLayoutRequest = false
            return
        }
        super.requestLayout()
    }
}

/** Possible view resize policies. */
enum class ResizePolicy {
    /**
     * Always resize the text view on text change.
     */
    ALWAYS,
    /**
     * Resize text view only when text length changes. This is the default.
     */
    ON_LENGTH_CHANGED,
    /**
     * Never resize the text view.
     */
    NEVER,
    /** Allow layout only if one of sizes is 0 (for example during first wrap_content). */
    IF_NOT_LAID_OUT
}