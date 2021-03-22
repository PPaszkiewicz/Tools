package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import androidx.recyclerview.widget.RecyclerView

/** Track insertion and movement to determine pre-layout positions. */
class AdapterMutationTracker : RecyclerView.AdapterDataObserver() {
    private val changes = mutableListOf<Change>()

    override fun onChanged() {
        clear()
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        changes.add(Change.Inserted(positionStart, itemCount))
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        changes.add(Change.Removed(positionStart, itemCount))
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        when {
            toPosition == fromPosition -> return
            toPosition > fromPosition -> {
                changes.add(Change.MForward(fromPosition, toPosition, itemCount))
            }
            else -> changes.add(Change.MBackward(fromPosition, toPosition, itemCount))
        }
    }

    private sealed class Change {
        abstract fun get(position: Int): Int

        class Inserted(val positionStart: Int, val itemCount: Int) : Change() {
            override fun get(position: Int) = when {
                position >= positionStart -> position - itemCount
                else -> position
            }
        }

        class Removed(val positionStart: Int, val itemCount: Int) : Change() {
            override fun get(position: Int) = when {
                position >= positionStart -> position + itemCount
                else -> position
            }
        }


        class MForward(val from: Int, val to: Int, val itemCount: Int) : Change() {
            override fun get(position: Int) = when {
                position >= to + itemCount -> position // unaffected - items after move range
                position < from -> position    // unaffected - items before move range
                position >= to -> from - (to - position) // moved items
                else -> position + itemCount    // items within move range
            }
        }

        class MBackward(val from: Int, val to: Int, val itemCount: Int) : Change() {
            override fun get(position: Int) = when {
                position >= from + itemCount -> position // unaffected - items after move range
                position < to -> position    // unaffected - items before move range
                position <= to + itemCount -> from - (to - position) // moved items
                else -> position - itemCount    // items within move range
            }
        }
    }

    /** Remove any tracked changes. */
    fun clear() {
        changes.clear()
    }

    /**
     * Determine where is the item will be in [targetPosition] after adapter
     * executes its structure changes.
     * */
    fun getPrepositionFor(targetPosition: Int): Int {
        var prePosition = targetPosition
        changes.forEach {
            prePosition = it.get(prePosition)
        }
        return prePosition
    }
}