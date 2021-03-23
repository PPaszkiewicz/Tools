package com.github.ppaszkiewicz.tools.demo

import androidx.recyclerview.widget.RecyclerView
import androidx.test.runner.AndroidJUnit4
import com.github.ppaszkiewicz.tools.toolbox.recyclerView.AdapterMutationTracker
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdapterMutationTrackerTest {

    @Test
    fun testMutationTracking(){
        val tracker = AdapterMutationTracker()

        tracker.onItemRangeRemoved(1, 5)
        assert(tracker.getPrepositionFor(1) == 6)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getTargetPositionFor(6) == 1)
        assert(tracker.getTargetPositionFor(0) == 0)
        assert(tracker.getTargetPositionFor(2) == RecyclerView.NO_POSITION)
        tracker.clear()

        tracker.onItemRangeInserted(2, 8)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(1) == 1)
        assert(tracker.getPrepositionFor(2) == RecyclerView.NO_POSITION)
        assert(tracker.getPrepositionFor(9) == RecyclerView.NO_POSITION)
        assert(tracker.getPrepositionFor(10) == 2)
        assert(tracker.getTargetPositionFor(0) == 0)
        assert(tracker.getTargetPositionFor(2) == 10)
        tracker.clear()

        tracker.onItemRangeMoved(1, 5, 2)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(5) == 1)
        assert(tracker.getPrepositionFor(6) == 2)
        assert(tracker.getPrepositionFor(7) == 7)
        assert(tracker.getPrepositionFor(2) == 4)
        assert(tracker.getTargetPositionFor(1) == 5)
        assert(tracker.getTargetPositionFor(0) == 0)
        assert(tracker.getTargetPositionFor(7) == 7)
        assert(tracker.getTargetPositionFor(1) == 5)
        assert(tracker.getTargetPositionFor(2) == 6)
        tracker.clear()

        tracker.onItemRangeMoved(5, 1, 2)
        assert(tracker.getPrepositionFor(0) == 0)
        assert(tracker.getPrepositionFor(7) == 7)
        assert(tracker.getPrepositionFor(4) == 2)
        assert(tracker.getPrepositionFor(1) == 5)
        assert(tracker.getPrepositionFor(2) == 6)
        assert(tracker.getTargetPositionFor(0) == 0)
        assert(tracker.getTargetPositionFor(7) == 7)
        assert(tracker.getTargetPositionFor(5) == 1)
        assert(tracker.getTargetPositionFor(6) == 2)
        assert(tracker.getTargetPositionFor(2) == 4)
        tracker.clear()
    }

    @Test
    fun testMultipleMutations(){
        val tracker = AdapterMutationTracker()

        tracker.onItemRangeInserted(1, 1)
        println("dumpP: ${dump(tracker, 12)}")
        println("dumpT: ${dump2(tracker, 12)}")
        assert(tracker.getPrepositionFor(1) == RecyclerView.NO_POSITION)
        assert(tracker.getPrepositionFor(10) == 9)
        assert(tracker.getTargetPositionFor(3) == 4)

        tracker.onItemRangeMoved(1, 10, 1)
        println("dumpP: ${dump(tracker, 12)}")
        println("dumpT: ${dump2(tracker, 12)}")
        assert(tracker.getPrepositionFor(10) == RecyclerView.NO_POSITION)
        assert(tracker.getPrepositionFor(9) == 9)
  //      assert(tracker.getTargetPositionFor(3) == 3)

        tracker.onItemRangeRemoved(0, 5)
        // NO_POS -> 1 -> 10 -> 5
        assert(tracker.getPrepositionFor(5) == RecyclerView.NO_POSITION)
        // unaffected by two mutations then moves due to removal
        assert(tracker.getPrepositionFor(6) == 10)
        // this gets removed in third step
 //       assert(tracker.getTargetPositionFor(3) == RecyclerView.NO_POSITION)
    }

    private fun dump(tracker: AdapterMutationTracker, length: Int) : String =
        IntArray(length){tracker.getPrepositionFor(it)}.joinToString()

    private fun dump2(tracker: AdapterMutationTracker, length: Int) : String =
        IntArray(length){tracker.getTargetPositionFor(it)}.joinToString()
}