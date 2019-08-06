package com.github.ppaszkiewicz.tools.toolbox.view

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.ppaszkiewicz.tools.toolbox.R

/**
 * Very simple stub displaying how to propagate custom view/drawable states.
 *
 * See [R.animator.elev_linear_layout_animator] as well.
 * */
class ElevLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    var isElevated = false
        set(v) {
            field = v
            refreshDrawableState()
        }

    init {
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.elev_linear_layout_animator)
    }

    override fun onCreateDrawableState(extraSpace: Int)= super.onCreateDrawableState(extraSpace + 1).also {
        if (isElevated) View.mergeDrawableStates(it, intArrayOf(R.attr.state_is_elevated))
    }
}