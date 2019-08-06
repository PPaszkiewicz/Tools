package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.widget.FrameLayout

/**
 * Stub for viewgroup that saves its children states even if they inflate duplicate IDs
 * within a layout.
 * */
open class SaveStateFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object{
        const val SAVE_SELF = "SAVE_STATE_SELF"
        const val SAVE_CHILDREN = "SAVE_STATE_CHILDREN"
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // disable saving on each child so they don't collide in save sparseArray
        repeat(childCount) {
            getChildAt(it).isSaveFromParentEnabled = false
        }
    }

    // saves this views state as bundle with super state + all children states merged
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val b = Bundle()
        b.putParcelable(SAVE_SELF, superState)
        val sa = SparseArray<Parcelable>()
        repeat(childCount) {
            getChildAt(it).saveHierarchyState(sa)
        }
        b.putSparseParcelableArray(SAVE_CHILDREN, sa)
        return b
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state == null) return
        val b = state as Bundle
        val sa = b.getSparseParcelableArray<Parcelable>(SAVE_CHILDREN)
        repeat(childCount) {
            getChildAt(it).restoreHierarchyState(sa)
        }
        super.onRestoreInstanceState(b.getParcelable(SAVE_SELF))
    }
}