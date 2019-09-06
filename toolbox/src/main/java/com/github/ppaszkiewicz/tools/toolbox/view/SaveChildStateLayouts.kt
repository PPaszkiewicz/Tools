package com.github.ppaszkiewicz.tools.toolbox.view

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Helper holding implementation for storing/restoring save state for views children.
 *
 * To use it ensure all views children [isSaveFromParentEnabled] is disabled and they have set IDs.
 *
 * Then implement following overrides:
 *
 *     override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) = super.addView(child.apply { isSaveFromParentEnabled = false }, index, params)
 *     override fun onSaveInstanceState() = ViewChildStateManager.onSaveInstanceState(this, super.onSaveInstanceState())
 *     override fun onRestoreInstanceState(state: Parcelable?) = super.onRestoreInstanceState(ViewChildStateManager.onRestoreInstanceState(this, state))
 *
 * */
object ViewChildStateManager {
    const val SAVE_SELF = "SAVE_STATE_SELF"
    const val SAVE_CHILDREN = "SAVE_STATE_CHILDREN"

    /** Merges [view] save state with its childrens save state.*/
    fun onSaveInstanceState(view: ViewGroup, superState: Parcelable?): Parcelable? {
        val b = Bundle()
        b.putParcelable(SAVE_SELF, superState)
        val sa = SparseArray<Parcelable>()
        repeat(view.childCount) {
            view.getChildAt(it).saveHierarchyState(sa)
        }
        b.putSparseParcelableArray(SAVE_CHILDREN, sa)
        return b
    }

    /** Restores [view] children save state and returns this views state. */
    fun onRestoreInstanceState(view: ViewGroup, state: Parcelable?): Parcelable? {
        if (state == null) return null
        val b = state as Bundle
        val sa = b.getSparseParcelableArray<Parcelable>(SAVE_CHILDREN)
        repeat(view.childCount) {
            view.getChildAt(it).restoreHierarchyState(sa)
        }
        return b.getParcelable(SAVE_SELF)
    }
}

open class SaveChildStateFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) =
        super.addView(child.apply { isSaveFromParentEnabled = false }, index, params)

    override fun onSaveInstanceState() =
        ViewChildStateManager.onSaveInstanceState(this, super.onSaveInstanceState())

    override fun onRestoreInstanceState(state: Parcelable?) =
        super.onRestoreInstanceState(ViewChildStateManager.onRestoreInstanceState(this, state))
}

open class SaveChildStateConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) =
        super.addView(child.apply { isSaveFromParentEnabled = false }, index, params)

    override fun onSaveInstanceState() =
        ViewChildStateManager.onSaveInstanceState(this, super.onSaveInstanceState())

    override fun onRestoreInstanceState(state: Parcelable?) =
        super.onRestoreInstanceState(ViewChildStateManager.onRestoreInstanceState(this, state))
}

open class SaveChildStateLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) =
        super.addView(child.also { it?.isSaveFromParentEnabled = false }, index, params)

    override fun onSaveInstanceState() =
        ViewChildStateManager.onSaveInstanceState(this, super.onSaveInstanceState())

    override fun onRestoreInstanceState(state: Parcelable?) =
        super.onRestoreInstanceState(ViewChildStateManager.onRestoreInstanceState(this, state))
}