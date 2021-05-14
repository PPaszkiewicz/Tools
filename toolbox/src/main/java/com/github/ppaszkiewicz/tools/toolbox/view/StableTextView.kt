package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.github.ppaszkiewicz.tools.toolbox.R
import com.github.ppaszkiewicz.tools.toolbox.view.StableTextViewResizePolicy.*

/** Base/handler of stabilizing the size during text changes. */
open class StableTextViewImpl(val host: View) {
    var resizePolicy = ON_LENGTH_CHANGED
        set(value) {
            field = value
            mConsumeLayoutRequest = false
            mTextLength = -1
        }
    private var mConsumeLayoutRequest = false
    private var mTextLength = -1

    /** Call this during [TextView.setText] (CharSequence, BufferType) before super call. */
    fun onTextLengthChanged(text: CharSequence?) = onTextLengthChanged(text?.length ?: 0)

    /** Call this during [TextView.setText] (CharSequence, BufferType) before super call. */
    fun onTextLengthChanged(textLength: Int) {
        when (resizePolicy) {
            ALWAYS -> {
                mConsumeLayoutRequest = false
            }
            ON_LENGTH_CHANGED -> {
                if (textLength == mTextLength) mConsumeLayoutRequest = true
            }
            NEVER -> {
                mConsumeLayoutRequest = true
            }
            IF_NOT_LAID_OUT -> {
                mConsumeLayoutRequest = host.width != 0 && host.height != 0
                        || host.isLaidOut && host.visibility == View.GONE
            }
        }
        mTextLength = textLength
    }

    /** Check this during [View.requestLayout] - if this returns false consume request and don't call super. */
    fun allowRequestLayout(): Boolean {
        if (mConsumeLayoutRequest) {
            mConsumeLayoutRequest = false
            return false
        }
        return true
    }
}

/** Possible resize policies of stable text views. */
enum class StableTextViewResizePolicy {
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

/**
 * TextView that prevents layout requests when text changes.
 * Use some padding and ellipsize = none for most predictable results.
 *
 * By default view will request layout only if text length changes ([StableTextViewResizePolicy.ON_LENGTH_CHANGED]).
 */
class StableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val impl = StableTextViewImpl(this)
    var resizePolicy by impl::resizePolicy

    init {
        attrs?.let {
            context.obtainStyledAttributes(it, R.styleable.StableTextView).run {
                val resizeAttrValue = getInt(
                    R.styleable.StableTextView_stableSizePolicy, ON_LENGTH_CHANGED.ordinal
                )
                resizePolicy = StableTextViewResizePolicy.values()[resizeAttrValue]
                recycle()
            }
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        impl.onTextLengthChanged(text)
        super.setText(text, type)
    }

    override fun requestLayout() {
        if (impl.allowRequestLayout()) super.requestLayout()
    }
}

/**
 * Chronometer that prevents layout requests when text changes.
 * Use some padding and ellipsize = none for most predictable results.
 *
 * By default view will request layout only if text length changes ([StableTextViewResizePolicy.ON_LENGTH_CHANGED]).
 */
class StableChronometer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Chronometer(context, attrs, defStyleAttr) {
    private val impl = StableTextViewImpl(this)
    var resizePolicy by impl::resizePolicy

    init {
        attrs?.let {
            context.obtainStyledAttributes(it, R.styleable.StableChronometer).run {
                val resizeAttrValue = getInt(
                    R.styleable.StableChronometer_stableSizePolicy, ON_LENGTH_CHANGED.ordinal
                )
                resizePolicy = StableTextViewResizePolicy.values()[resizeAttrValue]
                recycle()
            }
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        impl.onTextLengthChanged(text)
        super.setText(text, type)
    }

    override fun requestLayout() {
        if (impl.allowRequestLayout()) super.requestLayout()
    }
}