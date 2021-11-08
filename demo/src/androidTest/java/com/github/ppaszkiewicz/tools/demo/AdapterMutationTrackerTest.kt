package com.github.ppaszkiewicz.tools.demo

import androidx.recyclerview.widget.RecyclerView
import androidx.test.filters.SmallTest
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.AdapterMutationTracker
import org.junit.Test

@SmallTest
class AdapterMutationTrackerTest {

    @Test
    fun testMutationTracking() {
        AdapterMutationTracker().run {
            onItemRangeRemoved(1, 5)
            assert(getPrePositionFor(1) == 6)
            assert(getPrePositionFor(2) == 7)
            assert(getPrePositionFor(0) == 0)
            assert(getTargetPositionFor(6) == 1)
            assert(getTargetPositionFor(0) == 0)
            assert(getTargetPositionFor(2) == RecyclerView.NO_POSITION)
            clear()

            onItemRangeInserted(2, 4) // remove between 2-5
            assert(getPrePositionFor(0) == 0)
            assert(getPrePositionFor(1) == 1)
            assert(getPrePositionFor(2) == RecyclerView.NO_POSITION)
            assert(getPrePositionFor(5) == RecyclerView.NO_POSITION)
            assert(getPrePositionFor(10) == 6)
            assert(getTargetPositionFor(0) == 0)
            assert(getTargetPositionFor(6) == 10)
            clear()

            onItemRangeMoved(1, 5, 2)
            assert(getPrePositionFor(0) == 0)
            assert(getPrePositionFor(5) == 1)
            assert(getPrePositionFor(6) == 2)
            assert(getPrePositionFor(7) == 7)
            assert(getPrePositionFor(2) == 4)
            assert(getTargetPositionFor(1) == 5)
            assert(getTargetPositionFor(2) == 6)
            assert(getTargetPositionFor(3) == 1)
            assert(getTargetPositionFor(0) == 0)
            assert(getTargetPositionFor(7) == 7)
            clear()

            onItemRangeMoved(5, 1, 2)
            assert(getPrePositionFor(0) == 0)
            assert(getPrePositionFor(7) == 7)
            assert(getPrePositionFor(4) == 2)
            assert(getPrePositionFor(1) == 5)
            assert(getPrePositionFor(2) == 6)
            assert(getTargetPositionFor(0) == 0)
            assert(getTargetPositionFor(7) == 7)
            assert(getTargetPositionFor(5) == 1)
            assert(getTargetPositionFor(6) == 2)
            assert(getTargetPositionFor(2) == 4)
            clear()

            // this should cancel itself out
            onItemRangeMoved(2, 5, 3)
            onItemRangeMoved(5, 2, 3)
        }
    }

    @Test
    fun testMultipleMutations() {
        val tracker = AdapterMutationTracker()

        tracker.onItemRangeInserted(1, 1)
        println("dump: ${dump(tracker, 12)}")
        assert(tracker.getPrePositionFor(1) == RecyclerView.NO_POSITION)
        assert(tracker.getPrePositionFor(10) == 9)
        assert(tracker.getTargetPositionFor(3) == 4)

        tracker.onItemRangeMoved(1, 10, 1)
        println("dump: ${dump(tracker, 12)}")
        assert(tracker.getPrePositionFor(10) == RecyclerView.NO_POSITION)
        assert(tracker.getPrePositionFor(9) == 9)
        assert(tracker.getTargetPositionFor(10) == 11)

        tracker.onItemRangeRemoved(0, 2)
        println("dump: ${dump(tracker, 15)}")
        // NO_POS -> 1 -> 10 -> 8
        assert(tracker.getPrePositionFor(8) == RecyclerView.NO_POSITION)
        // unaffected by two mutations then moves due to removal
        assert(tracker.getPrePositionFor(6) == 8)
        assert(tracker.getTargetPositionFor(8) == 6)
        // this gets removed in third step
        assert(tracker.getTargetPositionFor(1) == RecyclerView.NO_POSITION)
    }

    private fun dump(tracker: AdapterMutationTracker, length: Int): String =
        """$tracker
s:${IntArray(length) { it }.joinToString()}
p:${IntArray(length) { tracker.getPrePositionFor(it) }.joinToString()}
t:${IntArray(length) { tracker.getTargetPositionFor(it) }.joinToString()}"""
}