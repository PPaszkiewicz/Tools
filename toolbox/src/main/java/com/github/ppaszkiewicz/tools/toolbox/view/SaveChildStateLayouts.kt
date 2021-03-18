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
 * Helper holding implementation for storing/restoring save state for views children, forcing parent to contain save of all children.
 *
 * This will
 * prevent save state collision due to duplicate view IDs within the same layout as long as parent containers have unique ID themselves.
 *
 * Implement following overrides in your ViewGroup:
 *
 *     override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) = ViewChildStateManager.dispatchSaveInstanceState(this, container, ::dispatchFreezeSelfOnly)
 *     override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) = ViewChildStateManager.dispatchRestoreInstanceState(this, container, ::dispatchThawSelfOnly)
 * */
object ViewChildStateManager {
    const val SAVE_SELF = "SAVE_STATE_SELF"
    const val SAVE_CHILDREN = "SAVE_STATE_CHILDREN"

    /** Merges [view] save state with its childrens save state.*/
    inline fun dispatchSaveInstanceState(
        view: ViewGroup,
        container: SparseArray<Parcelable>?,
        dispatchFreezeSelfOnly: (SparseArray<Parcelable>?) -> Unit
    ) {
        if (!view.isSaveEnabled || view.id == FrameLayout.NO_ID) return
        val selfState = SparseArray<Parcelable>(1)
        dispatchFreezeSelfOnly(selfState)
        val childrenState = SparseArray<Parcelable>()
        repeat(view.childCount) {
            view.getChildAt(it).let { v ->
                if (v.isSaveFromParentEnabled) v.saveHierarchyState(childrenState)
            }
        }
        Bundle().apply {
            putSparseParcelableArray(SAVE_SELF, selfState)
            putSparseParcelableArray(SAVE_CHILDREN, childrenState)
            container?.put(view.id, this)
        }
    }

    /** Restores [view] children save state and returns this views state. */
    inline fun dispatchRestoreInstanceState(
        view: ViewGroup,
        container: SparseArray<Parcelable>?,
        dispatchThawSelfOnly: (SparseArray<Parcelable>?) -> Unit
    ) {
        if (!view.isSaveEnabled || view.id == FrameLayout.NO_ID) return
        (container?.get(view.id) as? Bundle)?.let { bundle ->
            val selfState = bundle.getSparseParcelableArray<Parcelable>(SAVE_SELF)
            val childrenState = bundle.getSparseParcelableArray<Parcelable>(SAVE_CHILDREN)
            dispatchThawSelfOnly(selfState)

            repeat(view.childCount) {
                view.getChildAt(it).let { v ->
                    if (v.isSaveFromParentEnabled) v.restoreHierarchyState(childrenState)
                }
            }
        }
    }
}

open class SaveChildStateFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchSaveInstanceState(this, container, ::dispatchFreezeSelfOnly)

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchRestoreInstanceState(this, container, ::dispatchThawSelfOnly)
}

open class SaveChildStateConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchSaveInstanceState(this, container, ::dispatchFreezeSelfOnly)

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchRestoreInstanceState(this, container, ::dispatchThawSelfOnly)
}

open class SaveChildStateLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchSaveInstanceState(this, container, ::dispatchFreezeSelfOnly)

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) =
        ViewChildStateManager.dispatchRestoreInstanceState(this, container, ::dispatchThawSelfOnly)
}