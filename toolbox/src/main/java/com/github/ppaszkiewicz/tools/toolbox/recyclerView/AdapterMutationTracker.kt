package com.github.ppaszkiewicz.tools.toolbox.recyclerView

import androidx.recyclerview.widget.RecyclerView

/** Track insertion, removal and movement. Allows finding items position across structure change. */
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
        abstract fun getPPos(position: Int): Int // get pre position
        abstract fun getTPos(position: Int): Int // get target position

        data class Inserted(val positionStart: Int, val itemCount: Int) : Change() {
            override fun getPPos(position: Int) = removal(position, positionStart, itemCount)
            override fun getTPos(position: Int) = insert(position, positionStart, itemCount)
        }

        data class Removed(val positionStart: Int, val itemCount: Int) : Change() {
            override fun getPPos(position: Int) = insert(position, positionStart, itemCount)
            override fun getTPos(position: Int) = removal(position, positionStart, itemCount)
        }

        data class MForward(val from: Int, val to: Int, val itemCount: Int) : Change() {
            override fun getPPos(position: Int) = moveBackward(position, from, to, itemCount)
            // note: inverted from & to
            override fun getTPos(position: Int) = moveForward(position, to, from, itemCount)
        }

        data class MBackward(val from: Int, val to: Int, val itemCount: Int) : Change() {
            override fun getPPos(position: Int) = moveForward(position, from, to, itemCount)
            // note: inverted from & to
            override fun getTPos(position: Int) = moveBackward(position, to, from, itemCount)
        }

        protected fun removal(position: Int, positionStart: Int, itemCount: Int) = when {
            position < positionStart -> position // before affected range
            position >= positionStart + itemCount -> position - itemCount // after affected range
            else -> RecyclerView.NO_POSITION // items that are not inserted (prepos) or removed (pos)
        }

        protected fun insert(position: Int, positionStart: Int, itemCount: Int) = when {
            position >= positionStart -> position + itemCount // after affected range
            else -> position // before affected range
        }

        protected fun moveForward(position: Int, from: Int, to: Int, itemCount: Int) = when {
            position >= from + itemCount -> position // unaffected - items after move range
            position < to -> position    // unaffected - items before move range
            position <= to + itemCount -> from - (to - position) // moved items
            else -> position - itemCount    // items within move range
        }

        protected fun moveBackward(position: Int, from: Int, to: Int, itemCount: Int) = when {
            position >= to + itemCount -> position // unaffected - items after move range
            position < from -> position    // unaffected - items before move range
            position >= to -> from - (to - position) // moved items
            else -> position + itemCount    // items within move range
        }
    }


    /** Remove any tracked changes. */
    fun clear() {
        changes.clear()
    }

    // note those 2 functions should be reversible through each other
    // as long as they don't return NO_POSITION
    /**
     * Determine where is the item that will be in [targetPosition] after adapter
     * executes its structure changes.
     *
     * Returns [RecyclerView.NO_POSITION] for items that don't exist yet (newly inserted).
     * */
    fun getPrepositionFor(targetPosition: Int): Int {
        var prePosition = targetPosition
        changes.asReversed().forEach {
            if (prePosition == RecyclerView.NO_POSITION) return@forEach
            prePosition = it.getPPos(prePosition)
        }
        return prePosition
    }

    /**
     * Determine target position for item that's currently in [prePosition] after adapter
     * executes its structure changes.
     *
     * Returns [RecyclerView.NO_POSITION] for items that will be removed.
     * */
    //todo: has some issues, needs tweaking
    fun getTargetPositionFor(prePosition: Int): Int {
        var targetPosition = prePosition
        changes.forEach {
            if (targetPosition == RecyclerView.NO_POSITION) return@forEach
            targetPosition = it.getTPos(targetPosition)
        }
        return targetPosition
    }

    override fun toString(): String {
        return "${javaClass.simpleName}: ${changes.joinToString()}"
    }
}